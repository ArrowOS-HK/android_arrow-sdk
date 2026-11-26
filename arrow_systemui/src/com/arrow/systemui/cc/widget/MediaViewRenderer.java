package com.arrow.systemui.cc.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

import com.arrow.systemui.cc.Res;
import com.arrow.systemui.cc.controller.MediaSessionController;


public final class MediaViewRenderer {
    private static final Map<View, Runnable> PROGRESS_TICKERS = new WeakHashMap<>();

    private MediaViewRenderer() { }

    public static void render(View root, MediaSessionController mediaSession) {
        render(root, mediaSession, 2, 2);
    }

    public static void render(View root, MediaSessionController mediaSession, int spanW, int spanH) {
        if (root == null) return;
        Context context = root.getContext();
        FrameLayout body = root.findViewById(Res.id("arrow_media_body"));
        FrameLayout content = root.findViewById(Res.id("arrow_media_content"));
        TextView title = root.findViewById(Res.id("arrow_media_title"));
        TextView subtitle = root.findViewById(Res.id("arrow_media_artist"));
        LinearLayout info = root.findViewById(Res.id("arrow_media_info"));
        View artHolder = root.findViewById(Res.id("arrow_media_art_holder"));
        ImageView artwork = root.findViewById(Res.id("arrow_media_art"));
        ImageView blurredBackground = root.findViewById(Res.id("arrow_media_blur_bg"));
        View scrim = root.findViewById(Res.id("arrow_media_scrim"));
        ImageView placeholder = root.findViewById(Res.id("arrow_media_placeholder_icon"));
        View outputButton = root.findViewById(Res.id("arrow_media_output_button"));
        ImageView output = root.findViewById(Res.id("arrow_media_output"));
        TextView outputLabel = root.findViewById(Res.id("arrow_media_output_label"));
        FrameLayout controls = root.findViewById(Res.id("arrow_media_controls"));
        ImageView previous = root.findViewById(Res.id("arrow_media_prev"));
        ImageView play = root.findViewById(Res.id("arrow_media_play"));
        ImageView next = root.findViewById(Res.id("arrow_media_next"));
        ImageView shuffle = root.findViewById(Res.id("arrow_media_shuffle"));
        FrameLayout timeRow = root.findViewById(Res.id("arrow_media_time_row"));
        TextView timeStart = root.findViewById(Res.id("arrow_media_time_start"));
        TextView timeEnd = root.findViewById(Res.id("arrow_media_time_end"));
        FrameLayout seek = root.findViewById(Res.id("arrow_media_seek_container"));
        View seekTrack = root.findViewById(Res.id("arrow_media_seek_track"));
        View seekFill = root.findViewById(Res.id("arrow_media_seek_fill"));

        boolean access = mediaSession != null && mediaSession.hasAccess();
        boolean session = mediaSession != null && mediaSession.hasSession();
        CharSequence mediaTitle = session ? mediaSession.getTitle() : null;
        CharSequence mediaSubtitle = session ? mediaSession.getSubtitle() : null;

        if (title != null) {
            CharSequence text;
            if (!access) {
                text = context.getString(Res.string("arrow_media_access_required"));
            } else if (!session) {
                text = context.getString(Res.string("arrow_media_now_playing"));
            } else {
                text = TextUtils.isEmpty(mediaTitle)
                        ? context.getString(Res.string("arrow_media_now_playing")) : mediaTitle;
            }
            setTextIfChanged(title, text);
            enableMarquee(title);
        }
        if (subtitle != null) {
            CharSequence text;
            if (!access) {
                text = context.getString(Res.string("arrow_media_tap_to_enable"));
            } else if (!session) {
                text = context.getString(Res.string("arrow_media_artist_placeholder"));
            } else {
                text = TextUtils.isEmpty(mediaSubtitle)
                        ? context.getString(Res.string("arrow_media_artist_placeholder")) : mediaSubtitle;
            }
            setTextIfChanged(subtitle, text);
            enableMarquee(subtitle);
        }

        int safeW = Math.max(2, Math.min(4, spanW));
        int safeH = Math.max(1, Math.min(2, spanH));
        boolean compact = safeH <= 1;
        boolean full = safeH >= 2 && safeW >= 4;
        Bitmap bitmap = session ? mediaSession.getArtwork() : null;
        Drawable appIcon = session && mediaSession != null ? mediaSession.getAppIcon() : null;
        boolean artworkMode = bitmap != null && (compact || full);

        applySurface(context, body, artHolder, scrim, title, subtitle, placeholder,
                outputButton, output, outputLabel, previous, play, next, shuffle, seekTrack, seekFill,
                timeStart, timeEnd, artworkMode, compact, full);
        bindArtwork(artwork, blurredBackground, placeholder, bitmap, appIcon, full, compact, artworkMode,
                title == null ? context.getString(Res.string("arrow_media_player")) : title.getText());
        bindOutputRoute(context, output, outputLabel, mediaSession);

        boolean playing = session && mediaSession.isPlaying();
        if (play != null) {
            play.setImageResource(playing ? Res.drawable("arrow_ic_pause") : Res.drawable("arrow_ic_play"));
            play.setContentDescription(context.getString(
                    playing ? Res.string("arrow_media_pause") : Res.string("arrow_media_play")));
        }

        setEnabled(previous, session && mediaSession.canPrevious());
        setEnabled(play, session && mediaSession.canPlayPause());
        setEnabled(next, session && mediaSession.canNext());
        setEnabled(shuffle, session && mediaSession.canShuffle());
        if (outputButton != null) {
            outputButton.setEnabled(true);
            outputButton.setAlpha(access ? 1f : 0.45f);
        }

        applyResponsiveLayout(context, content, info, artHolder, outputButton, outputLabel,
                controls, previous, play, next, shuffle, timeRow, seek, title, subtitle, safeW, safeH);
        bindProgress(seek, seekTrack, seekFill, timeRow, timeStart, timeEnd, mediaSession, safeW, safeH);
    }

    private static void applySurface(Context context, FrameLayout body, View artHolder, View scrim,
            TextView title, TextView subtitle, ImageView placeholder, View outputButton,
            ImageView output, TextView outputLabel, ImageView previous, ImageView play,
            ImageView next, ImageView shuffle, View seekTrack, View seekFill, TextView timeStart,
            TextView timeEnd, boolean artworkMode, boolean compact, boolean full) {
        int iconColor = context.getColor(Res.color(artworkMode ? "arrow_cc_media_text" : "arrow_cc_icon"));
        int titleColor = context.getColor(Res.color(artworkMode ? "arrow_cc_media_text" : "arrow_cc_text_primary"));
        int subtitleColor = context.getColor(
                Res.color(artworkMode ? "arrow_cc_media_text_secondary" : "arrow_cc_text_secondary"));
        ColorStateList tint = ColorStateList.valueOf(iconColor);


        int chipIconColor = artworkMode ? 0xFF202124 : iconColor;
        ColorStateList outputTint = ColorStateList.valueOf(chipIconColor);
        int outputTextColor = artworkMode ? 0xFF202124 : titleColor;

        if (body != null) {
            if (compact) {
                body.setBackgroundResource(Res.drawable(
                        artworkMode ? "arrow_bg_cc_media_pill" : "arrow_bg_cc_media_empty_pill"));
                body.setForeground(context.getDrawable(Res.drawable("arrow_bg_cc_media_border_pill")));
            } else {
                body.setBackgroundResource(Res.drawable(
                        artworkMode ? "arrow_bg_cc_media" : "arrow_bg_cc_media_empty"));
                body.setForeground(context.getDrawable(Res.drawable("arrow_bg_cc_media_border")));
            }
        }
        if (artHolder != null) {
            if (full && artworkMode) {

                artHolder.setBackground(null);
            } else {
                artHolder.setBackgroundResource(
                        Res.drawable(artworkMode ? "arrow_bg_cc_media_art" : "arrow_bg_cc_media_art_empty"));
            }
        }
        if (scrim != null) scrim.setVisibility(artworkMode ? View.VISIBLE : View.GONE);
        if (title != null) title.setTextColor(titleColor);
        if (subtitle != null) subtitle.setTextColor(subtitleColor);
        if (placeholder != null) placeholder.setImageTintList(tint);
        if (outputButton != null) outputButton.setBackgroundResource(Res.drawable(
                artworkMode ? "arrow_bg_cc_media_output_button_light" : "arrow_bg_cc_media_output_button_empty"));
        if (output != null) output.setImageTintList(outputTint);
        if (outputLabel != null) outputLabel.setTextColor(outputTextColor);
        if (previous != null) previous.setImageTintList(tint);
        if (play != null) {
            play.setImageTintList(ColorStateList.valueOf(chipIconColor));
            play.setBackgroundResource(Res.drawable(
                    artworkMode ? "arrow_bg_cc_media_output_button_light" : "arrow_bg_cc_media_output_button_empty"));
        }
        if (next != null) next.setImageTintList(tint);
        if (shuffle != null) shuffle.setImageTintList(tint);
        if (seekTrack != null) setPillBackground(seekTrack,
                artworkMode ? 0x55FFFFFF : context.getColor(Res.color("arrow_cc_outline")));
        if (seekFill != null) setPillBackground(seekFill,
                artworkMode ? 0xFFFFFFFF : context.getColor(Res.color("arrow_cc_accent")));
        if (timeStart != null) timeStart.setTextColor(subtitleColor);
        if (timeEnd != null) timeEnd.setTextColor(subtitleColor);
    }

    private static void applyResponsiveLayout(Context context, FrameLayout content, LinearLayout info,
            View artHolder, View outputButton, TextView outputLabel, FrameLayout controls,
            ImageView previous, ImageView play, ImageView next, ImageView shuffle, FrameLayout timeRow, FrameLayout seek,
            TextView title, TextView subtitle, int spanW, int spanH) {
        if (content == null) return;
        boolean compact = spanH <= 1;
        boolean wide = spanH >= 2 && spanW >= 3;
        boolean full = spanH >= 2 && spanW >= 4;

        resetControlView(previous, dp(context, 6));
        resetControlView(play, dp(context, 6));
        resetControlView(next, dp(context, 6));
        resetControlView(shuffle, dp(context, 7));
        if (shuffle != null) shuffle.setVisibility(View.GONE);
        if (timeRow != null) timeRow.setVisibility(View.GONE);
        if (outputLabel != null) outputLabel.setVisibility(View.GONE);
        if (outputButton != null) outputButton.setVisibility((compact && spanW < 3) ? View.GONE : View.VISIBLE);

        if (compact) {
            final boolean showOutput = spanW >= 3;
            final boolean showSideControls = spanW >= 4;
            content.setPadding(dp(context, 13), dp(context, 7), dp(context, 10), dp(context, 7));
            if (title != null) title.setTextSize(14f);
            if (subtitle != null) {
                subtitle.setTextSize(12f);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) subtitle.getLayoutParams();
                lp.topMargin = dp(context, 2);
                subtitle.setLayoutParams(lp);
            }
            if (artHolder != null) artHolder.setVisibility(View.GONE);

            if (outputButton != null) {
                outputButton.setVisibility(showOutput ? View.VISIBLE : View.GONE);
                frame(outputButton, dp(context, 34), dp(context, 34),
                        Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 0, 0);
            }

            if (previous != null) previous.setVisibility(showSideControls ? View.VISIBLE : View.GONE);
            if (next != null) next.setVisibility(showSideControls ? View.VISIBLE : View.GONE);

            int controlsW = showSideControls ? dp(context, 118) : dp(context, 38);
            int controlsRight = showOutput ? dp(context, 41) : 0;
            if (controls != null) frame(controls, controlsW, dp(context, 38),
                    Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, controlsRight, 0);
            if (showSideControls) {
                controlFrame(previous, dp(context, 34), dp(context, 38),
                        Gravity.START | Gravity.CENTER_VERTICAL, 0, 0, 0, 0);
                controlFrame(play, dp(context, 38), dp(context, 38),
                        Gravity.CENTER, 0, 0, 0, 0);
                controlFrame(next, dp(context, 34), dp(context, 38),
                        Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 0, 0);
            } else {
                controlFrame(play, dp(context, 38), dp(context, 38), Gravity.CENTER, 0, 0, 0, 0);
            }
            if (play != null) play.setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7));

            if (info != null) {
                info.setGravity(Gravity.CENTER_VERTICAL);
                int infoRight = showSideControls ? dp(context, 166) : showOutput ? dp(context, 89) : dp(context, 48);
                frame(info, -1, -2, Gravity.START | Gravity.CENTER_VERTICAL,
                        0, 0, infoRight, 0);
            }
            if (seek != null) seek.setVisibility(View.GONE);
            return;
        }

        content.setPadding(dp(context, full ? 14 : 12), dp(context, full ? 12 : 12),
                dp(context, full ? 14 : 12), dp(context, full ? 10 : 9));
        if (subtitle != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) subtitle.getLayoutParams();
            lp.topMargin = dp(context, full ? 4 : 3);
            subtitle.setLayoutParams(lp);
        }

        if (!wide) {
            if (title != null) title.setTextSize(14f);
            if (subtitle != null) subtitle.setTextSize(13f);
            if (artHolder != null) {
                artHolder.setVisibility(View.VISIBLE);
                frame(artHolder, dp(context, 44), dp(context, 44), Gravity.START | Gravity.TOP, 0, 0, 0, 0);
            }
            if (outputButton != null) frame(outputButton, dp(context, 34), dp(context, 34),
                    Gravity.END | Gravity.TOP, 0, 0, 0, 0);
            if (info != null) {
                info.setGravity(Gravity.START);
                frame(info, -1, -2, Gravity.START | Gravity.TOP, 0, dp(context, 56), 0, 0);
            }
            if (controls != null) frame(controls, -1, dp(context, 36), Gravity.BOTTOM, 0, 0, 0, 0);
            controlFrame(previous, dp(context, 36), dp(context, 36), Gravity.CENTER, -dp(context, 44), 0, 0, 0);
            controlFrame(play, dp(context, 42), dp(context, 36), Gravity.CENTER, 0, 0, 0, 0);
            controlFrame(next, dp(context, 36), dp(context, 36), Gravity.CENTER, dp(context, 44), 0, 0, 0);
            if (seek != null) seek.setVisibility(View.GONE);
            return;
        }

        if (!full) {
            if (title != null) title.setTextSize(14f);
            if (subtitle != null) subtitle.setTextSize(13f);
            if (artHolder != null) {
                artHolder.setVisibility(View.VISIBLE);
                frame(artHolder, dp(context, 52), dp(context, 52), Gravity.START | Gravity.TOP, 0, 0, 0, 0);
            }
            if (outputButton != null) frame(outputButton, dp(context, 34), dp(context, 34),
                    Gravity.END | Gravity.TOP, 0, 0, 0, 0);
            if (info != null) {
                info.setGravity(Gravity.START);
                frame(info, -1, -2, Gravity.START | Gravity.TOP,
                        dp(context, 64), dp(context, 4), dp(context, 42), 0);
            }
            if (controls != null) frame(controls, -1, dp(context, 38), Gravity.BOTTOM, 0, 0, 0, 0);
            controlFrame(previous, dp(context, 38), dp(context, 38), Gravity.CENTER, -dp(context, 56), 0, 0, 0);
            controlFrame(play, dp(context, 46), dp(context, 38), Gravity.CENTER, 0, 0, 0, 0);
            controlFrame(next, dp(context, 38), dp(context, 38), Gravity.CENTER, dp(context, 56), 0, 0, 0);
            if (timeRow != null) {
                timeRow.setVisibility(View.VISIBLE);
                frame(timeRow, -1, dp(context, 14), Gravity.BOTTOM, 0, 0, 0, dp(context, 58));
            }
            if (seek != null) frame(seek, -1, dp(context, 16), Gravity.BOTTOM, 0, 0, 0, dp(context, 41));
            return;
        }

        if (title != null) title.setTextSize(15f);
        if (subtitle != null) subtitle.setTextSize(14f);
        if (artHolder != null) {
            artHolder.setVisibility(View.VISIBLE);
            frame(artHolder, dp(context, 34), dp(context, 34), Gravity.START | Gravity.TOP, 0, 0, 0, 0);
        }
        if (outputLabel != null) {
            outputLabel.setVisibility(View.VISIBLE);
            outputLabel.setTextSize(12.5f);
        }
        if (outputButton != null) frame(outputButton, dp(context, 124), dp(context, 32),
                Gravity.END | Gravity.TOP, 0, 0, 0, 0);
        if (info != null) {
            info.setGravity(Gravity.START);
            frame(info, -1, -2, Gravity.START | Gravity.TOP,
                    0, dp(context, 48), dp(context, 64), 0);
        }
        if (controls != null) frame(controls, -1, -1, Gravity.FILL, 0, 0, 0, 0);
        controlFrame(play, dp(context, 52), dp(context, 52), Gravity.END | Gravity.TOP,
                0, dp(context, 52), 0, 0);
        if (play != null) {
            play.setPadding(dp(context, 13), dp(context, 13), dp(context, 13), dp(context, 13));
            play.setBackgroundResource(Res.drawable("arrow_bg_cc_media_play_large"));
            play.setImageTintList(ColorStateList.valueOf(0xFF202124));
        }
        controlFrame(previous, dp(context, 34), dp(context, 34), Gravity.START | Gravity.BOTTOM,
                0, 0, 0, 0);
        if (shuffle != null) shuffle.setVisibility(View.VISIBLE);
        controlFrame(next, dp(context, 34), dp(context, 34), Gravity.END | Gravity.BOTTOM,
                0, 0, dp(context, 42), 0);
        controlFrame(shuffle, dp(context, 34), dp(context, 34), Gravity.END | Gravity.BOTTOM,
                0, 0, 0, 0);
        if (seek != null) frame(seek, -1, dp(context, 16), Gravity.BOTTOM,
                dp(context, 44), 0, dp(context, 88), dp(context, 9));
    }

    private static void bindProgress(final FrameLayout seek, final View track, final View fill,
            final View timeRow, final TextView timeStart, final TextView timeEnd,
            final MediaSessionController mediaSession, int spanW, int spanH) {
        if (seek == null || track == null || fill == null) return;
        cancelProgressTicker(seek);
        boolean visible = spanH >= 2 && spanW >= 3;
        final boolean showTime = visible && spanW == 3;
        seek.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (timeRow != null) timeRow.setVisibility(showTime ? View.VISIBLE : View.GONE);
        if (!visible) {
            seek.setOnTouchListener(null);
            return;
        }

        final boolean[] hasProgress = new boolean[1];
        final long[] duration = new long[1];
        updateProgressNow(seek, track, fill, timeStart, timeEnd, showTime, mediaSession, hasProgress, duration);
        seek.post(new Runnable() {
            @Override public void run() {
                updateProgressNow(seek, track, fill, timeStart, timeEnd, showTime, mediaSession, hasProgress, duration);
            }
        });

        if (hasProgress[0] && mediaSession != null && mediaSession.canSeek()) {
            seek.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent event) {
                    int action = event.getActionMasked();
                    if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE
                            && action != MotionEvent.ACTION_UP) return true;
                    int width = Math.max(1, track.getWidth() > 0 ? track.getWidth() : seek.getWidth());
                    float x = Math.max(0f, Math.min(width, event.getX()));
                    float p = x / width;
                    long target = (long) (duration[0] * p);
                    updateFill(track, fill, p);
                    if (showTime) updateTimeLabels(timeStart, timeEnd, target, duration[0]);
                    mediaSession.seekTo(target);
                    return true;
                }
            });
        } else {
            seek.setOnTouchListener(null);
        }

        if (mediaSession != null && mediaSession.hasSession() && mediaSession.isPlaying()) {
            Runnable ticker = new Runnable() {
                @Override public void run() {
                    if (seek.getVisibility() != View.VISIBLE || seek.getParent() == null) {
                        cancelProgressTicker(seek);
                        return;
                    }
                    updateProgressNow(seek, track, fill, timeStart, timeEnd, showTime, mediaSession, hasProgress, duration);
                    if (mediaSession.hasSession() && mediaSession.isPlaying()) seek.postDelayed(this, 1000L);
                    else cancelProgressTicker(seek);
                }
            };
            PROGRESS_TICKERS.put(seek, ticker);
            seek.postDelayed(ticker, 1000L);
        }
    }

    private static void cancelProgressTicker(View seek) {
        Runnable old = PROGRESS_TICKERS.remove(seek);
        if (old != null) seek.removeCallbacks(old);
    }

    private static void updateProgressNow(FrameLayout seek, View track, View fill,
            TextView timeStart, TextView timeEnd, boolean showTime, MediaSessionController mediaSession,
            boolean[] hasProgressOut, long[] durationOut) {
        boolean hasProgress = mediaSession != null && mediaSession.hasSession()
                && mediaSession.getDurationMillis() > 0L;
        long duration = hasProgress ? mediaSession.getDurationMillis() : 0L;
        long position = hasProgress ? mediaSession.getPositionMillis() : 0L;
        float percent = duration <= 0L ? 0f : Math.max(0f, Math.min(1f, position / (float) duration));
        hasProgressOut[0] = hasProgress;
        durationOut[0] = duration;
        updateFill(track, fill, percent);
        if (showTime) updateTimeLabels(timeStart, timeEnd, position, duration);
    }

    private static void updateTimeLabels(TextView start, TextView end, long positionMillis, long durationMillis) {
        if (start != null) setTextIfChanged(start, formatMediaTime(positionMillis));
        if (end != null) setTextIfChanged(end, formatMediaTime(durationMillis));
    }

    private static String formatMediaTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long seconds = totalSeconds % 60L;
        long totalMinutes = totalSeconds / 60L;
        long minutes = totalMinutes % 60L;
        long hours = totalMinutes / 60L;
        if (hours > 0L) {
            return hours + ":" + twoDigits(minutes) + ":" + twoDigits(seconds);
        }
        return minutes + ":" + twoDigits(seconds);
    }

    private static String twoDigits(long value) {
        return value < 10L ? "0" + value : String.valueOf(value);
    }

    private static void updateFill(View track, View fill, float percent) {
        int width = track.getWidth();
        if (width <= 0 && track.getParent() instanceof View) width = ((View) track.getParent()).getWidth();
        if (width <= 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
        lp.width = Math.max(0, Math.min(width, Math.round(width * percent)));
        fill.setLayoutParams(lp);
    }

    private static void bindArtwork(ImageView artwork, ImageView blurredBackground,
            ImageView placeholder, Bitmap bitmap, Drawable appIcon, boolean full, boolean compact,
            boolean showBackdrop, CharSequence description) {
        if (showBackdrop && bitmap != null && blurredBackground != null) {
            blurredBackground.setImageBitmap(bitmap);
            blurredBackground.setAlpha(1f);
            blurredBackground.setVisibility(View.VISIBLE);

            blurredBackground.setRenderEffect(null);
        } else if (blurredBackground != null) {
            blurredBackground.setImageDrawable(null);
            blurredBackground.setRenderEffect(null);
            blurredBackground.setVisibility(View.GONE);
        }

        if (compact) {
            if (artwork != null) {
                artwork.setImageDrawable(null);
                artwork.setImageTintList(null);
                artwork.setPadding(0, 0, 0, 0);
                artwork.setVisibility(View.GONE);
                artwork.setContentDescription(description);
            }
            if (placeholder != null) {
                placeholder.setVisibility(View.GONE);
                placeholder.setContentDescription(description);
            }
            return;
        }

        if (full) {
            if (artwork != null) {
                artwork.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int pad = dp(artwork.getContext(), 0);
                artwork.setPadding(pad, pad, pad, pad);
                Drawable monoIcon = monochromeAppIcon(appIcon);
                if (monoIcon != null) artwork.setImageDrawable(monoIcon);
                else artwork.setImageResource(Res.drawable("arrow_ic_music"));
                artwork.setImageTintList(ColorStateList.valueOf(showBackdrop ? 0xFFFFFFFF : 0xFF202124));
                artwork.setVisibility(View.VISIBLE);
                artwork.setContentDescription(description);
            }
            if (placeholder != null) {
                placeholder.setVisibility(View.GONE);
                placeholder.setContentDescription(description);
            }
            return;
        }

        if (bitmap != null) {
            if (artwork != null) {
                artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
                artwork.setPadding(0, 0, 0, 0);
                artwork.setImageTintList(null);
                artwork.setImageBitmap(bitmap);
                artwork.setVisibility(View.VISIBLE);
                artwork.setContentDescription(description);
            }
            if (placeholder != null) {
                placeholder.setVisibility(View.GONE);
                placeholder.setContentDescription(description);
            }
        } else {
            if (artwork != null) {
                artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
                artwork.setPadding(0, 0, 0, 0);
                artwork.setImageTintList(null);
                artwork.setImageDrawable(null);
                artwork.setVisibility(View.GONE);
                artwork.setContentDescription(description);
            }
            if (placeholder != null) {
                placeholder.setVisibility(View.VISIBLE);
                placeholder.setContentDescription(description);
            }
        }
    }

    private static Drawable monochromeAppIcon(Drawable appIcon) {
        if (appIcon == null) return null;
        if (appIcon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) appIcon;
            Drawable mono = null;
            try { mono = adaptive.getMonochrome(); }
            catch (Throwable ignored) { mono = null; }
            if (mono == null) {
                try { mono = adaptive.getForeground(); }
                catch (Throwable ignored) { mono = null; }
            }
            if (mono != null) return cloneDrawable(mono);
        }
        return cloneDrawable(appIcon);
    }

    private static Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        Drawable copy = state != null ? state.newDrawable() : drawable;
        return copy.mutate();
    }

    private static void bindOutputRoute(Context context, ImageView output, TextView outputLabel,
            MediaSessionController mediaSession) {
        if (output == null && outputLabel == null) return;
        int route = mediaSession == null
                ? MediaSessionController.ROUTE_SPEAKER : mediaSession.getOutputRoute();
        int icon;
        int label;
        switch (route) {
            case MediaSessionController.ROUTE_BLUETOOTH:
                icon = Res.drawable("arrow_ic_bluetooth");
                label = Res.string("arrow_media_output_bluetooth");
                break;
            case MediaSessionController.ROUTE_HEADSET:
                icon = Res.drawable("arrow_ic_headphones");
                label = Res.string("arrow_media_output_headphones");
                break;
            case MediaSessionController.ROUTE_REMOTE:
                icon = Res.drawable("arrow_ic_cast");
                label = Res.string("arrow_media_output_remote");
                break;
            case MediaSessionController.ROUTE_SPEAKER:
            default:
                icon = Res.drawable("arrow_ic_speaker");
                label = Res.string("arrow_media_output_speaker");
                break;
        }
        if (output != null) output.setImageResource(icon);
        if (outputLabel != null) outputLabel.setText(label);
        if (output != null) output.setContentDescription(context.getString(label));
    }

    private static void setTextIfChanged(TextView view, CharSequence value) {
        if (view == null) return;
        CharSequence current = view.getText();
        if (!TextUtils.equals(current, value)) view.setText(value);
    }

    private static void enableMarquee(final TextView view) {
        if (view == null) return;
        view.setSingleLine(true);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        view.setMarqueeRepeatLimit(-1);
        view.setHorizontallyScrolling(true);
        if (!view.isSelected()) view.setSelected(true);
    }

    private static void setEnabled(ImageView view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.32f);
    }

    private static void resetControlView(ImageView view, int pad) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        view.setPadding(pad, pad, pad, pad);
    }

    private static void frame(View view, int width, int height, int gravity,
            int left, int top, int right, int bottom) {
        if (view == null) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.rightMargin = right;
        lp.bottomMargin = bottom;
        view.setLayoutParams(lp);
    }

    private static void controlFrame(View view, int width, int height, int gravity,
            int left, int top, int right, int bottom) {
        if (view == null) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.rightMargin = right;
        lp.bottomMargin = bottom;
        view.setLayoutParams(lp);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
    }

    private static void setPillBackground(View view, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(999f);
        view.setBackground(drawable);
    }
}
