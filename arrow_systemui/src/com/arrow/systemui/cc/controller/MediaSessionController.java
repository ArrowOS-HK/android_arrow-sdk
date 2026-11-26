package com.arrow.systemui.cc.controller;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.arrow.systemui.cc.MediaNotificationListener;


public final class MediaSessionController {
    public interface Callback { void onMediaChanged(); }

    public static final int ROUTE_SPEAKER = 0;
    public static final int ROUTE_HEADSET = 1;
    public static final int ROUTE_BLUETOOTH = 2;
    public static final int ROUTE_REMOTE = 3;


    private static final long ACTION_SET_SHUFFLE_MODE_ENABLED = 524288L;
    private static final long ACTION_SET_SHUFFLE_MODE = 2097152L;
    private static final int SHUFFLE_MODE_NONE = 0;
    private static final int SHUFFLE_MODE_ALL = 1;

    private final Context context;
    private final MediaSessionManager manager;
    private final AudioManager audioManager;
    private final ComponentName listenerComponent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private final Runnable progressTicker = new Runnable() {
        @Override public void run() { }
    };

    private Callback callback;
    private MediaController current;
    private boolean listenerRegistered;
    private String resolvedArtworkUri;
    private String loadingArtworkUri;
    private Bitmap resolvedArtwork;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) {
            clearArtworkCache();
            notifyChanged();
        }

        @Override public void onPlaybackStateChanged(PlaybackState state) {
            notifyChanged();
        }

        @Override public void onSessionDestroyed() {
            refresh();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> controllers) {
                    selectBest(controllers);
                }
            };

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            notifyChanged();
        }

        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            notifyChanged();
        }
    };

    public MediaSessionController(Context value) {
        context = value.getApplicationContext();
        manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        listenerComponent = new ComponentName(context, MediaNotificationListener.class);
    }

    public void start(Callback value) {
        callback = value;
        ensureAudioDeviceCallback();
        ensureSessionsListener();
        refresh();
    }

    public void stop() {
        if (manager != null && listenerRegistered) {
            try { manager.removeOnActiveSessionsChangedListener(sessionsListener); }
            catch (Throwable ignored) { }
        }
        mainHandler.removeCallbacks(progressTicker);
        if (audioManager != null) {
            try { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback); }
            catch (Throwable ignored) { }
        }
        listenerRegistered = false;
        setCurrent(null);
        callback = null;
        artworkExecutor.shutdownNow();
    }

    public boolean hasAccess() {
        if (context.checkSelfPermission("android.permission.MEDIA_CONTENT_CONTROL")
                == PackageManager.PERMISSION_GRANTED) return true;
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager != null
                && notificationManager.isNotificationListenerAccessGranted(listenerComponent);
    }

    public void requestAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); } catch (Throwable ignored) { }
    }

    private void ensureAudioDeviceCallback() {
        if (audioManager == null) return;
        try { audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler); }
        catch (Throwable ignored) { }
    }

    public void refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override public void run() { refresh(); }
            });
            return;
        }
        ensureSessionsListener();
        selectBest(querySessions());
    }

    private void ensureSessionsListener() {
        if (manager == null || listenerRegistered) return;
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, null, mainHandler);
            listenerRegistered = true;
            return;
        } catch (Throwable ignored) { }
        try {
            manager.addOnActiveSessionsChangedListener(
                    sessionsListener, listenerComponent, mainHandler);
            listenerRegistered = true;
        } catch (Throwable ignored) {
            listenerRegistered = false;
        }
    }

    private List<MediaController> querySessions() {
        if (manager == null) return Collections.emptyList();
        try { return manager.getActiveSessions(null); }
        catch (Throwable ignored) { }
        try { return manager.getActiveSessions(listenerComponent); }
        catch (Throwable ignored) { return Collections.emptyList(); }
    }

    private void selectBest(List<MediaController> controllers) {
        MediaController best = null;
        int bestScore = Integer.MIN_VALUE;
        if (controllers != null) {
            for (MediaController candidate : controllers) {
                int score = score(candidate);
                if (best == null || score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        setCurrent(best);
        notifyChanged();
    }

    private static int score(MediaController controller) {
        PlaybackState state = null;
        MediaMetadata metadata = null;
        try { state = controller.getPlaybackState(); } catch (Throwable ignored) { }
        try { metadata = controller.getMetadata(); } catch (Throwable ignored) { }
        int score;
        int stateValue = state == null ? PlaybackState.STATE_NONE : state.getState();
        switch (stateValue) {
            case PlaybackState.STATE_PLAYING: score = 1000; break;
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING: score = 900; break;
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING: score = 850; break;
            case PlaybackState.STATE_PAUSED: score = 700; break;
            case PlaybackState.STATE_STOPPED: score = 300; break;
            default: score = 100; break;
        }
        if (metadata != null) score += 50;
        return score;
    }

    private void setCurrent(MediaController next) {
        boolean changed = !sameSession(current, next);
        if (!changed) return;

        if (current != null) {
            try { current.unregisterCallback(controllerCallback); }
            catch (Throwable ignored) { }
        }
        current = next;
        clearArtworkCache();
        if (current != null) {
            try { current.registerCallback(controllerCallback, mainHandler); }
            catch (Throwable ignored) { }
        }
    }

    private static boolean sameSession(MediaController left, MediaController right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        try { return left.getSessionToken().equals(right.getSessionToken()); }
        catch (Throwable ignored) { return false; }
    }

    private void notifyChanged() {
        Callback value = callback;
        if (value != null) value.onMediaChanged();
        mainHandler.removeCallbacks(progressTicker);
    }

    public Drawable getAppIcon() {
        if (current == null) return null;
        try { return context.getPackageManager().getApplicationIcon(current.getPackageName()); }
        catch (Throwable ignored) { return null; }
    }

    public boolean hasSession() { return current != null; }

    public boolean isPlaying() {
        PlaybackState state = playbackState();
        if (state == null) return false;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                return true;
            default:
                return false;
        }
    }

    public CharSequence getTitle() {
        MediaMetadata metadata = metadata();
        return firstText(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                MediaMetadata.METADATA_KEY_TITLE);
    }

    public CharSequence getArtist() {
        MediaMetadata metadata = metadata();
        return firstText(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_AUTHOR);
    }

    public CharSequence getAlbum() {
        MediaMetadata metadata = metadata();
        return firstText(metadata,
                MediaMetadata.METADATA_KEY_ALBUM,
                MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION);
    }

    public CharSequence getSubtitle() {
        CharSequence artist = getArtist();
        CharSequence album = getAlbum();
        if (TextUtils.isEmpty(artist)) return album;
        if (TextUtils.isEmpty(album) || TextUtils.equals(artist, album)) return artist;
        return artist + " • " + album;
    }

    public Bitmap getArtwork() {
        MediaMetadata metadata = metadata();
        if (metadata == null) return null;

        Bitmap bitmap = firstBitmap(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON,
                MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap != null) return bitmap;

        MediaDescription description = null;
        try { description = metadata.getDescription(); }
        catch (Throwable ignored) { }
        if (description != null) {
            try {
                bitmap = description.getIconBitmap();
                if (bitmap != null) return bitmap;
            } catch (Throwable ignored) { }
        }

        String uri = firstString(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (TextUtils.isEmpty(uri) && description != null) {
            try {
                Uri iconUri = description.getIconUri();
                if (iconUri != null) uri = iconUri.toString();
            } catch (Throwable ignored) { }
        }
        if (TextUtils.isEmpty(uri)) return null;
        if (uri.equals(resolvedArtworkUri)) return resolvedArtwork;
        if (!uri.equals(loadingArtworkUri)) loadArtwork(uri);
        return null;
    }

    public int getOutputRoute() {
        if (audioManager == null) return ROUTE_SPEAKER;


        try {
            int routed = routeFromDevices(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
            if (routed != ROUTE_SPEAKER) return routed;
        } catch (Throwable ignored) { }

        try { if (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn()) return ROUTE_BLUETOOTH; }
        catch (Throwable ignored) { }
        try { if (audioManager.isWiredHeadsetOn()) return ROUTE_HEADSET; }
        catch (Throwable ignored) { }
        return ROUTE_SPEAKER;
    }

    private static int routeFromDevices(AudioDeviceInfo[] devices) {
        if (devices == null || devices.length == 0) return ROUTE_SPEAKER;
        int best = ROUTE_SPEAKER;
        for (AudioDeviceInfo device : devices) {
            if (device == null || !device.isSink()) continue;
            int type = device.getType();
            if (isBluetooth(type)) return ROUTE_BLUETOOTH;
            if (isHeadset(type)) best = ROUTE_HEADSET;
            else if (best == ROUTE_SPEAKER && isRemote(type)) best = ROUTE_REMOTE;
        }
        return best;
    }

    private static boolean isBluetooth(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO


                || type == 26
                || type == 27
                || type == 30;
    }

    private static boolean isHeadset(int type) {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == 23;
    }

    private static boolean isRemote(int type) {
        return type == AudioDeviceInfo.TYPE_HDMI
                || type == AudioDeviceInfo.TYPE_HDMI_ARC
                || type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_DOCK
                || type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX
                || type == 29;
    }


    public long getDurationMillis() {
        MediaMetadata metadata = metadata();
        if (metadata == null) return 0L;
        try { return Math.max(0L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)); }
        catch (Throwable ignored) { return 0L; }
    }

    public long getPositionMillis() {
        PlaybackState state = playbackState();
        if (state == null) return 0L;
        long position;
        try { position = state.getPosition(); }
        catch (Throwable ignored) { return 0L; }
        if (position < 0L) position = 0L;
        if (isPlaying()) {
            try {
                long update = state.getLastPositionUpdateTime();
                if (update > 0L) {
                    float speed = state.getPlaybackSpeed();
                    if (speed <= 0f) speed = 1f;
                    position += (long) ((SystemClock.elapsedRealtime() - update) * speed);
                }
            } catch (Throwable ignored) { }
        }
        long duration = getDurationMillis();
        if (duration > 0L) position = Math.min(position, duration);
        return Math.max(0L, position);
    }

    public boolean canSeek() {
        PlaybackState state = playbackState();
        if (state == null) return false;
        long duration = getDurationMillis();
        if (duration <= 0L) return false;
        long actions;
        try { actions = state.getActions(); }
        catch (Throwable ignored) { return false; }
        return (actions & PlaybackState.ACTION_SEEK_TO) != 0L;
    }

    public void seekTo(long positionMillis) {
        MediaController controller = current;
        if (controller == null) return;
        long duration = getDurationMillis();
        if (duration > 0L) positionMillis = Math.min(Math.max(0L, positionMillis), duration);
        try { controller.getTransportControls().seekTo(Math.max(0L, positionMillis)); }
        catch (Throwable ignored) { }
    }

    public boolean canPrevious() {
        return supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS);
    }

    public boolean canNext() {
        return supports(PlaybackState.ACTION_SKIP_TO_NEXT);
    }

    public boolean canShuffle() {
        return supports(ACTION_SET_SHUFFLE_MODE) || supports(ACTION_SET_SHUFFLE_MODE_ENABLED);
    }

    public boolean canPlayPause() {
        PlaybackState state = playbackState();
        if (state == null) return hasSession();
        long actions = state.getActions();
        if (actions == 0L || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L) return true;
        long required = isPlaying() ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        return (actions & required) != 0L;
    }

    private boolean supports(long action) {
        PlaybackState state = playbackState();
        if (state == null) return hasSession();
        long actions = state.getActions();
        return actions == 0L || (actions & action) != 0L;
    }

    public void previous() {
        MediaController controller = current;
        if (controller == null) return;
        try { controller.getTransportControls().skipToPrevious(); }
        catch (Throwable ignored) { }
    }

    public void next() {
        MediaController controller = current;
        if (controller == null) return;
        try { controller.getTransportControls().skipToNext(); }
        catch (Throwable ignored) { }
    }

    public void toggleShuffle() {
        MediaController controller = current;
        if (controller == null) return;
        int currentMode = getShuffleModeCompat(controller);
        int nextMode = (currentMode == SHUFFLE_MODE_ALL || currentMode > SHUFFLE_MODE_ALL)
                ? SHUFFLE_MODE_NONE : SHUFFLE_MODE_ALL;
        try {
            Object controls = controller.getTransportControls();
            controls.getClass().getMethod("setShuffleMode", int.class).invoke(controls, nextMode);
            return;
        } catch (Throwable ignored) { }


        try {
            Object controls = controller.getTransportControls();
            controls.getClass().getMethod("setShuffleModeEnabled", boolean.class)
                    .invoke(controls, nextMode != SHUFFLE_MODE_NONE);
        } catch (Throwable ignored) { }
    }

    private static int getShuffleModeCompat(MediaController controller) {
        if (controller == null) return SHUFFLE_MODE_NONE;
        try {
            Object value = controller.getClass().getMethod("getShuffleMode").invoke(controller);
            if (value instanceof Integer) return ((Integer) value).intValue();
        } catch (Throwable ignored) { }
        return SHUFFLE_MODE_NONE;
    }

    public void togglePlayPause() {
        MediaController controller = current;
        if (controller == null) return;
        try {
            if (isPlaying()) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } catch (Throwable ignored) { }
    }

    public void openApp() {
        MediaController controller = current;
        if (controller == null) return;
        try {
            Intent launch = context.getPackageManager()
                    .getLaunchIntentForPackage(controller.getPackageName());
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } catch (Throwable ignored) { }
    }

    private MediaMetadata metadata() {
        MediaController controller = current;
        if (controller == null) return null;
        try { return controller.getMetadata(); }
        catch (Throwable ignored) { return null; }
    }

    private PlaybackState playbackState() {
        MediaController controller = current;
        if (controller == null) return null;
        try { return controller.getPlaybackState(); }
        catch (Throwable ignored) { return null; }
    }

    private static CharSequence firstText(MediaMetadata metadata, String... keys) {
        if (metadata == null) return null;
        for (String key : keys) {
            try {
                CharSequence value = metadata.getText(key);
                if (!TextUtils.isEmpty(value)) return value;
            } catch (Throwable ignored) { }
            try {
                String value = metadata.getString(key);
                if (!TextUtils.isEmpty(value)) return value;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static String firstString(MediaMetadata metadata, String... keys) {
        if (metadata == null) return null;
        for (String key : keys) {
            try {
                String value = metadata.getString(key);
                if (!TextUtils.isEmpty(value)) return value;
            } catch (Throwable ignored) { }
            try {
                CharSequence value = metadata.getText(key);
                if (!TextUtils.isEmpty(value)) return value.toString();
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Bitmap firstBitmap(MediaMetadata metadata, String... keys) {
        if (metadata == null) return null;
        for (String key : keys) {
            try {
                Bitmap value = metadata.getBitmap(key);
                if (value != null) return value;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private void loadArtwork(final String uriText) {
        loadingArtworkUri = uriText;
        final MediaSession.Token token;
        try { token = current == null ? null : current.getSessionToken(); }
        catch (Throwable ignored) { loadingArtworkUri = null; return; }
        try {
            artworkExecutor.execute(new Runnable() {
                @Override public void run() {
                final Bitmap bitmap = decodeBitmap(uriText);
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        MediaController controller = current;
                        if (controller == null || token == null
                                || !token.equals(controller.getSessionToken())) return;
                        loadingArtworkUri = null;
                        resolvedArtworkUri = uriText;
                        resolvedArtwork = bitmap;
                        notifyChanged();
                    }
                });
                }
            });
        } catch (Throwable ignored) {
            loadingArtworkUri = null;
        }
    }

    private Bitmap decodeBitmap(String uriText) {
        InputStream stream = null;
        try {
            Uri uri = Uri.parse(uriText);
            String scheme = uri.getScheme();
            if (scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                URLConnection connection = new URL(uriText).openConnection();
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                stream = connection.getInputStream();
            } else {
                stream = context.getContentResolver().openInputStream(uri);
            }
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            return scaleArtwork(bitmap);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private static Bitmap scaleArtwork(Bitmap bitmap) {
        if (bitmap == null) return null;
        int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (maxSide <= 768) return bitmap;
        float scale = 768f / maxSide;
        return Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
    }

    private void clearArtworkCache() {
        resolvedArtworkUri = null;
        loadingArtworkUri = null;
        resolvedArtwork = null;
    }
}
