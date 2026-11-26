package com.arrow.systemui.cc.widget;

import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.arrow.systemui.cc.Res;
import com.arrow.systemui.cc.controller.BrightnessController;
import com.arrow.systemui.cc.controller.ConnectivityController;
import com.arrow.systemui.cc.controller.FlashlightController;
import com.arrow.systemui.cc.controller.MediaSessionController;
import com.arrow.systemui.cc.controller.RotationController;
import com.arrow.systemui.cc.controller.VolumeController;
import com.arrow.systemui.cc.model.ControlId;
import com.arrow.systemui.cc.model.PanelRepository;
import com.arrow.systemui.cc.model.TileId;


class QsPanelController implements FlashlightController.Callback {
    private final QsPanelView panel;
    private PanelRepository repo;
    private FlashlightController flashlight;
    private BrightnessController brightness;
    private VolumeController volume;
    private ConnectivityController connectivity;
    private RotationController rotation;
    private MediaSessionController media;
    private boolean edit, autoBright, mute;
    private final Map<TileId, Boolean> active = new EnumMap<>(TileId.class);
    private final List<VerticalLevelSlider> brightnessV = new ArrayList<>();
    private final List<HorizontalLevelSlider> brightnessH = new ArrayList<>();
    private final List<VerticalLevelSlider> volumeV = new ArrayList<>();
    private final List<HorizontalLevelSlider> volumeH = new ArrayList<>();
    private final List<View> mediaViews = new ArrayList<>();
    private final Map<View, int[]> mediaSpans = new WeakHashMap<>();
    private final Map<TileId, List<View>> tileViews = new EnumMap<>(TileId.class);
    private final Runnable attachDragRunnable = new Runnable() {
        @Override public void run() {
            if (edit && repo != null) DragSupport.attach(panel, repo);
        }
    };

    QsPanelController(QsPanelView value) { panel = value; }
    boolean isEditMode() { return edit; }

    void bind() {
        repo = new PanelRepository(panel.getContext());
        flashlight = new FlashlightController(panel.getContext());
        brightness = new BrightnessController(panel.getContext());
        volume = new VolumeController(panel.getContext());
        connectivity = new ConnectivityController(panel.getContext());
        rotation = new RotationController(panel.getContext());
        media = new MediaSessionController(panel.getContext());
        flashlight.addCallback(this);
        media.start(() -> panel.post(this::renderMediaViews));
        rebuildUi();
    }

    void onResume() {
        if (media != null) media.refresh();
        rebuildUi();
        scheduleDragAttach();
    }

    void onDestroy() {
        panel.removeCallbacks(attachDragRunnable);
        if (flashlight != null) {
            flashlight.removeCallback(this);
            flashlight.destroy();
        }
        if (media != null) media.stop();
    }

    void setEditMode(boolean enabled) {
        edit = enabled;
        panel.findViewById(Res.id("arrow_header_normal")).setVisibility(enabled ? View.GONE : View.VISIBLE);
        panel.findViewById(Res.id("arrow_header_edit")).setVisibility(enabled ? View.VISIBLE : View.GONE);
        panel.findViewById(Res.id("arrow_txt_edit_hint")).setVisibility(enabled ? View.VISIBLE : View.GONE);
        panel.findViewById(Res.id("arrow_label_controls")).setVisibility(enabled ? View.VISIBLE : View.GONE);
        panel.findViewById(Res.id("arrow_label_tiles")).setVisibility(enabled ? View.VISIBLE : View.GONE);
        FrameLayout cb = panel.findViewById(Res.id("arrow_controls_box"));
        FrameLayout tb = panel.findViewById(Res.id("arrow_tiles_box"));
        int p = dp(12);
        if (enabled) {
            cb.setBackgroundResource(Res.drawable("arrow_bg_cc_card"));
            tb.setBackgroundResource(Res.drawable("arrow_bg_cc_card"));
            cb.setPadding(p, p, p, p);
            tb.setPadding(p, p, p, p);
            cb.setClipChildren(false);
            tb.setClipChildren(false);
        } else {
            cb.setBackground(null);
            tb.setBackground(null);
            cb.setPadding(0, 0, 0, 0);
            tb.setPadding(0, 0, 0, 0);
        }
        rebuildUi();
        scheduleDragAttach();
    }

    void rebuildUi() { rebuildControls(); rebuildTilesOnly(); }

    private void scheduleDragAttach() {
        panel.removeCallbacks(attachDragRunnable);
        if (edit) panel.postDelayed(attachDragRunnable, 48L);
    }
    private int dp(int v) { return (int) (v * panel.getResources().getDisplayMetrics().density + .5f); }
    private int color(int id) { return panel.getContext().getColor(id); }

    private void rebuildControls() {
        final LinearLayout host = panel.findViewById(Res.id("arrow_controls_host"));
        host.removeAllViews();
        mediaViews.clear();
        mediaSpans.clear();
        host.post(() -> {
            int maxW = host.getWidth();
            if (maxW <= 0) {
                host.post(this::rebuildControls);
                return;
            }
            host.removeAllViews();
            mediaViews.clear();
            mediaSpans.clear();
            brightnessV.clear();
            brightnessH.clear();
            volumeV.clear();
            volumeH.clear();
            int cols = Math.max(2, Math.min(4, repo.getControlColumns()));
            int rowLimit = Math.max(1, Math.min(3, repo.getControlRows()));
            int cell = Math.min(panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")), Math.max(1, maxW / cols));
            int gapV = panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
            int gapH = cols > 1 ? Math.max(0, maxW - cols * cell) / (cols - 1) : 0;
            boolean[][] occ = new boolean[rowLimit][cols];
            List<Place> places = new ArrayList<>();
            for (ControlId id : repo.visibleControls()) {
                int sw = Math.max(1, Math.min(cols, repo.getControlSpanW(id)));
                int sh = Math.max(1, repo.getControlSpanH(id));
                boolean placed = false;
                outer:
                for (int r = 0; r <= rowLimit - sh; r++) for (int c = 0; c <= cols - sw; c++) {
                    if (free(occ, r, c, sw, sh)) {
                        mark(occ, r, c, sw, sh);
                        places.add(new Place(id, r, c, sw, sh));
                        placed = true;
                        break outer;
                    }
                }
            }
            int rows = edit ? rowLimit : 1;
            for (Place p : places) rows = Math.max(rows, p.r + p.h);
            FrameLayout wrap = new FrameLayout(panel.getContext());
            GridLayout grid = new GridLayout(panel.getContext());
            grid.setColumnCount(cols);
            grid.setRowCount(rows);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            wrap.addView(grid, new FrameLayout.LayoutParams(-1, -2));
            LayoutInflater inflater = LayoutInflater.from(panel.getContext());
            for (Place p : places) {
                View child = createControl(inflater, p.id, p.w, p.h);
                child.setTag(p.id);
                int w = cell * p.w + gapH * (p.w - 1);
                int h = cell * p.h + gapV * (p.h - 1);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(p.r, p.h, GridLayout.FILL),
                        GridLayout.spec(p.c, p.w, GridLayout.FILL));
                lp.width = w;
                lp.height = h;
                lp.rightMargin = p.c + p.w < cols ? gapH : 0;
                lp.bottomMargin = p.r + p.h < rows ? gapV : 0;
                lp.setGravity(Gravity.FILL);
                child.setLayoutParams(lp);
                grid.addView(child);
            }
            host.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
            scheduleDragAttach();
        });
    }

    private static boolean free(boolean[][] a, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) for (int x = c; x < c + w; x++) if (a[y][x]) return false;
        return true;
    }

    private static void mark(boolean[][] a, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) for (int x = c; x < c + w; x++) a[y][x] = true;
    }

    private static class Place {
        final ControlId id;
        final int r, c, w, h;
        Place(ControlId i, int y, int x, int sw, int sh) { id = i; r = y; c = x; w = sw; h = sh; }
    }

    private View createControl(LayoutInflater i, ControlId id, int spanW, int spanH) {
        switch (id) {
            case INTERNET:
            case BLUETOOTH:
            case RINGER:
                return pill(i, id);
            case MEDIA:
                return media(i, id, spanW, spanH);
            case BRIGHTNESS_V:
                return vertical(i, id, true);
            case VOLUME_V:
                return vertical(i, id, false);
            case BRIGHTNESS_H:
                return horizontal(i, id, true);
            case VOLUME_H:
                return horizontal(i, id, false);
            default:
                return circle(i, id);
        }
    }

    private View pill(LayoutInflater i, ControlId id) {
        View v = i.inflate(Res.layout("arrow_item_pill"), panel, false);
        ImageView icon = v.findViewById(Res.id("arrow_pill_icon"));
        ImageView badge = v.findViewById(Res.id("arrow_badge"));
        icon.setImageResource(id.iconRes);
        renderPillState(v, id);
        badge.setVisibility(edit ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(x -> { repo.removeControl(id); rebuildUi(); });
        v.findViewById(Res.id("arrow_pill_body")).setOnClickListener(x -> {
            if (edit) return;
            if (id == ControlId.INTERNET) {
                connectivity.toggleWifi();
                renderPillState(v, id);
                panel.postDelayed(() -> renderPillState(v, id), 350);
            } else if (id == ControlId.BLUETOOTH) {
                connectivity.toggleBluetooth();
                renderPillState(v, id);
                panel.postDelayed(() -> renderPillState(v, id), 350);
            } else {
                connectivity.openDndSettings();
            }
        });
        return v;
    }

    private void renderPillState(View v, ControlId id) {
        LinearLayout body = v.findViewById(Res.id("arrow_pill_body"));
        TextView label = v.findViewById(Res.id("arrow_pill_label"));
        if (id == ControlId.INTERNET) {
            boolean on = connectivity != null && connectivity.isWifiEnabled();
            label.setText(on ? Res.string("arrow_networks_available") : Res.string("arrow_ctrl_internet"));
            body.setBackgroundResource(on ? Res.drawable("arrow_bg_cc_pill_on") : Res.drawable("arrow_bg_cc_pill"));
        } else if (id == ControlId.BLUETOOTH) {
            boolean on = connectivity != null && connectivity.isBluetoothEnabled();
            label.setText(Res.string("arrow_bluetooth"));
            body.setBackgroundResource(on ? Res.drawable("arrow_bg_cc_pill_on") : Res.drawable("arrow_bg_cc_pill"));
        } else {
            label.setText(id.labelRes);
            body.setBackgroundResource(Res.drawable("arrow_bg_cc_pill"));
        }
    }

    private View media(LayoutInflater i, ControlId id, int spanW, int spanH) {
        View v = i.inflate(Res.layout("arrow_item_media"), panel, false);
        mediaViews.add(v);
        mediaSpans.put(v, new int[] { spanW, spanH });
        ImageView badge = v.findViewById(Res.id("arrow_badge"));
        ImageView prev = v.findViewById(Res.id("arrow_media_prev"));
        ImageView play = v.findViewById(Res.id("arrow_media_play"));
        ImageView next = v.findViewById(Res.id("arrow_media_next"));
        ImageView shuffle = v.findViewById(Res.id("arrow_media_shuffle"));
        View body = v.findViewById(Res.id("arrow_media_body"));
        badge.setVisibility(edit ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(x -> { repo.removeControl(id); rebuildUi(); });
        View resize = v.findViewById(Res.id("arrow_media_resize_handle"));
        if (resize != null) {
            resize.setVisibility(edit ? View.VISIBLE : View.GONE);
            if (edit) attachMediaResize(v, resize);
            else resize.setOnTouchListener(null);
        }
        prev.setOnClickListener(x -> { if (!edit && media != null) media.previous(); });
        play.setOnClickListener(x -> { if (!edit && media != null) media.togglePlayPause(); });
        next.setOnClickListener(x -> { if (!edit && media != null) media.next(); });
        if (shuffle != null) {
            shuffle.setOnClickListener(x -> { if (!edit && media != null) media.toggleShuffle(); });
        }
        body.setOnClickListener(x -> {
            if (edit || media == null) return;
            if (media.hasSession()) media.openApp();
            else if (!media.hasAccess()) media.requestAccess();
        });
        renderMediaView(v);
        return v;
    }

    private void renderMediaViews() {
        Iterator<View> it = mediaViews.iterator();
        while (it.hasNext()) {
            View v = it.next();
            if (v.getParent() == null) {
                it.remove();
                mediaSpans.remove(v);
            } else renderMediaView(v);
        }
    }

    private void renderMediaView(View v) {
        int[] span = mediaSpans.get(v);
        if (span == null) span = new int[] { repo.getMediaSpanW(), repo.getMediaSpanH() };
        MediaViewRenderer.render(v, media, span[0], span[1]);
    }

    private void attachMediaResize(final View card, final View handle) {
        handle.setClickable(true);
        handle.setLongClickable(false);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float startX;
            float startY;
            int startIndex;
            int lastIndex;
            int startCellW;
            int startCellH;
            long lastResizeAt;

            @Override public boolean onTouch(View touch, MotionEvent event) {
                if (!edit) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startIndex = repo.getMediaSizeIndex();
                        lastIndex = startIndex;
                        int[] startCells = mediaCellsForCard(card, startIndex);
                        startCellW = startCells[0];
                        startCellH = startCells[1];
                        lastResizeAt = event.getEventTime() - 220L;
                        if (touch.getParent() != null) touch.getParent().requestDisallowInterceptTouchEvent(true);
                        card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        setResizeVisual(card, true);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int wanted = mediaSizeIndexForDrag(
                                event.getRawX() - startX, event.getRawY() - startY,
                                startIndex, startCellW, startCellH);
                        if (wanted != lastIndex && event.getEventTime() - lastResizeAt >= 180L) {
                            repo.setMediaSizeIndex(wanted);
                            lastIndex = repo.getMediaSizeIndex();
                            lastResizeAt = event.getEventTime();
                            if (!relayoutControlsLive()) {
                                mediaSpans.put(card, new int[] { repo.getMediaSpanW(), repo.getMediaSpanH() });
                                renderMediaView(card);
                                SmoothLayout.softRefresh(card.findViewById(Res.id("arrow_media_content")));
                            }
                            SmoothLayout.pulse(touch);
                            touch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (touch.getParent() != null) touch.getParent().requestDisallowInterceptTouchEvent(false);
                        setResizeVisual(card, false);
                        if (!relayoutControlsLive()) rebuildControls();
                        panel.post(() -> DragSupport.attach(panel, repo));
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void setResizeVisual(View card, boolean on) {
        card.animate().cancel();
        card.setScaleX(1f);
        card.setScaleY(1f);
        if (on) {
            card.animate()
                    .translationZ(dp(18))
                    .alpha(.97f)
                    .setDuration(100L)
                    .start();
        } else {
            card.animate()
                    .translationZ(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .start();
        }
    }

    private int[] mediaCellsForCard(View card, int index) {
        int startW = mediaSpanWForIndex(index);
        int startH = mediaSpanHForIndex(index);
        int gap = panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
        int cellW = Math.max(1, (card.getWidth() - gap * (startW - 1)) / Math.max(1, startW));
        int cellH = Math.max(1, (card.getHeight() - gap * (startH - 1)) / Math.max(1, startH));
        return new int[] { cellW, cellH };
    }

    private int mediaSizeIndexForDrag(float dx, float dy, int startIndex, int cellW, int cellH) {
        int startW = mediaSpanWForIndex(startIndex);
        int startH = mediaSpanHForIndex(startIndex);
        int gap = panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
        float stepX = Math.max(1f, cellW + gap);
        float stepY = Math.max(1f, cellH + gap);
        float slopX = Math.max(dp(48), stepX * 0.68f);
        float slopY = Math.max(dp(48), stepY * 0.68f);

        int targetW = startW;
        int targetH = startH;
        if (dx > slopX) {
            targetW = startW + 1 + (int) ((dx - slopX) / stepX);
        } else if (dx < -slopX) {
            targetW = startW - 1 - (int) ((-dx - slopX) / stepX);
        }
        if (dy > slopY) {
            targetH = startH + 1;
        } else if (dy < -slopY) {
            targetH = startH - 1;
        }

        targetW = Math.max(2, Math.min(4, targetW));
        targetH = Math.max(1, Math.min(2, targetH));
        if (targetH <= 1) {
            if (targetW >= 4) return 5;
            if (targetW >= 3) return 4;
            return 0;
        }
        if (targetW <= 2) return 1;
        if (targetW == 3) return 2;
        return 3;
    }

    private int mediaSpanWForIndex(int index) {
        switch (Math.max(0, Math.min(5, index))) {
            case 2:
            case 4:
                return 3;
            case 3:
            case 5:
                return 4;
            case 0:
            case 1:
            default:
                return 2;
        }
    }

    private int mediaSpanHForIndex(int index) {
        int safe = Math.max(0, Math.min(5, index));
        return (safe == 0 || safe == 4 || safe == 5) ? 1 : 2;
    }

    private boolean relayoutControlsLive() {
        if (!edit) return false;
        LinearLayout host = panel.findViewById(Res.id("arrow_controls_host"));
        if (host == null || host.getChildCount() == 0 || !(host.getChildAt(0) instanceof FrameLayout)) return false;
        FrameLayout wrap = (FrameLayout) host.getChildAt(0);
        GridLayout grid = findControlGrid(wrap);
        if (grid == null) return false;

        Map<ControlId, View> existing = new EnumMap<>(ControlId.class);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof ControlId) existing.put((ControlId) tag, child);
        }

        List<ControlId> ids = repo.getControls();
        for (ControlId id : ids) if (!existing.containsKey(id)) return false;

        int maxW = host.getWidth();
        if (maxW <= 0) return false;
        int cols = Math.max(2, Math.min(4, repo.getControlColumns()));
        int rowLimit = Math.max(1, Math.min(3, repo.getControlRows()));
        int cell = Math.min(panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")), Math.max(1, maxW / cols));
        int gapV = panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
        int gapH = cols > 1 ? Math.max(0, maxW - cols * cell) / (cols - 1) : 0;
        boolean[][] used = new boolean[rowLimit][cols];
        List<Place> places = new ArrayList<>();

        for (ControlId id : ids) {
            int sw = Math.max(1, Math.min(cols, repo.getControlSpanW(id)));
            int sh = Math.max(1, repo.getControlSpanH(id));
            boolean placed = false;
            outer:
            for (int r = 0; r <= rowLimit - sh; r++) {
                for (int c = 0; c <= cols - sw; c++) {
                    if (free(used, r, c, sw, sh)) {
                        mark(used, r, c, sw, sh);
                        places.add(new Place(id, r, c, sw, sh));
                        placed = true;
                        break outer;
                    }
                }
            }
            if (!placed) return false;
        }

        int rows = rowLimit;
        for (Place place : places) rows = Math.max(rows, place.r + place.h);
        Map<View, Rect> beforeBounds = SmoothLayout.capture(grid);
        boolean boundsAnim = SmoothLayout.beginSmoothBounds(grid);
        while (wrap.getChildCount() > 1) wrap.removeViewAt(1);
        grid.setColumnCount(cols);
        grid.setRowCount(rows);

        for (int i = grid.getChildCount() - 1; i >= 0; i--) {
            View child = grid.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof ControlId) || !ids.contains(tag)) grid.removeViewAt(i);
        }

        mediaViews.clear();
        mediaSpans.clear();
        for (Place place : places) {
            View child = existing.get(place.id);
            if (child == null) return false;
            int w = cell * place.w + gapH * (place.w - 1);
            int h = cell * place.h + gapV * (place.h - 1);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(place.r, place.h, GridLayout.FILL),
                    GridLayout.spec(place.c, place.w, GridLayout.FILL));
            lp.width = w;
            lp.height = h;
            lp.rightMargin = place.c + place.w < cols ? gapH : 0;
            lp.bottomMargin = place.r + place.h < rows ? gapV : 0;
            lp.setGravity(Gravity.FILL);
            child.setTag(place.id);
            child.setLayoutParams(lp);
            if (child.getParent() == null) grid.addView(child);
            child.setAlpha(1f);
            if (place.id == ControlId.MEDIA) {
                mediaViews.add(child);
                mediaSpans.put(child, new int[] { place.w, place.h });
                renderMediaView(child);
                SmoothLayout.softRefresh(child.findViewById(Res.id("arrow_media_content")));
                View resize = child.findViewById(Res.id("arrow_media_resize_handle"));
                if (resize != null) resize.setVisibility(View.VISIBLE);
            }
        }

        drawLiveEmptyControls(wrap, used, rows, cols, cell, gapH, gapV);
        grid.requestLayout();
        wrap.requestLayout();
        host.requestLayout();


        if (!boundsAnim) SmoothLayout.play(grid, beforeBounds);
        return true;
    }

    private GridLayout findControlGrid(FrameLayout wrap) {
        for (int i = 0; i < wrap.getChildCount(); i++) {
            if (wrap.getChildAt(i) instanceof GridLayout) return (GridLayout) wrap.getChildAt(i);
        }
        return null;
    }

    private void drawLiveEmptyControls(FrameLayout wrap, boolean[][] used, int rows, int cols,
            int cell, int gapH, int gapV) {
        FrameLayout layer = new FrameLayout(panel.getContext());
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        wrap.addView(layer, new FrameLayout.LayoutParams(-1, rows * cell + (rows - 1) * gapV));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r >= used.length || c >= used[r].length || used[r][c]) continue;
                FrameLayout slot = new FrameLayout(panel.getContext());
                slot.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty"));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cell, cell);
                lp.leftMargin = c * (cell + gapH);
                lp.topMargin = r * (cell + gapV);
                layer.addView(slot, lp);
                SmoothLayout.appear(slot);
            }
        }
    }

    private void setMediaButtonState(ImageView v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : .35f);
    }

    private static boolean isBlank(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private View vertical(LayoutInflater i, ControlId id, boolean bright) {
        View v = i.inflate(Res.layout("arrow_item_slider_vertical"), panel, false);
        FrameLayout body = v.findViewById(Res.id("arrow_slider_body"));
        View fill = v.findViewById(Res.id("arrow_slider_fill"));
        ImageView icon = v.findViewById(Res.id("arrow_slider_icon"));
        ImageView badge = v.findViewById(Res.id("arrow_badge"));
        icon.setImageResource(bright ? Res.drawable("arrow_ic_brightness") : Res.drawable("arrow_ic_volume"));
        badge.setVisibility(edit ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(x -> { repo.removeControl(id); rebuildUi(); });
        int max = bright ? 255 : Math.max(1, volume.getMax());
        int value = bright ? brightness.getBrightness() : volume.getVolume();
        if (!edit) {
            VerticalLevelSlider s = new VerticalLevelSlider(body, fill, max,
                    n -> { if (bright) applyBrightness(n); else applyVolume(n); },
                    icon, color(Res.color("arrow_cc_icon")), color(Res.color("arrow_cc_on_accent")));
            s.setProgress(value);
            if (bright) brightnessV.add(s); else volumeV.add(s);
        } else {
            VerticalLevelSlider.applyStaticProgress(body, fill, max, value, icon,
                    color(Res.color("arrow_cc_icon")), color(Res.color("arrow_cc_on_accent")));
        }
        return v;
    }

    private View horizontal(LayoutInflater i, ControlId id, boolean bright) {
        View v = i.inflate(Res.layout("arrow_item_slider_horizontal"), panel, false);
        FrameLayout body = v.findViewById(Res.id("arrow_slider_body"));
        View fill = v.findViewById(Res.id("arrow_slider_fill"));
        ImageView icon = v.findViewById(Res.id("arrow_slider_icon"));
        ImageView badge = v.findViewById(Res.id("arrow_badge"));
        v.setMinimumHeight(0);
        body.setMinimumHeight(0);
        icon.setImageResource(bright ? Res.drawable("arrow_ic_brightness") : Res.drawable("arrow_ic_volume"));
        FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) icon.getLayoutParams();
        ilp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        ilp.setMarginEnd(dp(14));
        ilp.bottomMargin = 0;
        icon.setLayoutParams(ilp);
        badge.setVisibility(edit ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(x -> { repo.removeControl(id); rebuildUi(); });
        int max = bright ? 255 : Math.max(1, volume.getMax());
        int value = bright ? brightness.getBrightness() : volume.getVolume();
        if (!edit) {
            HorizontalLevelSlider s = new HorizontalLevelSlider(body, fill, max,
                    n -> { if (bright) applyBrightness(n); else applyVolume(n); },
                    icon, color(Res.color("arrow_cc_icon")), color(Res.color("arrow_cc_on_accent")));
            s.setProgress(value);
            if (bright) brightnessH.add(s); else volumeH.add(s);
        } else {
            HorizontalLevelSlider.applyStaticProgress(body, fill, max, value, icon,
                    color(Res.color("arrow_cc_icon")), color(Res.color("arrow_cc_on_accent")));
        }
        return v;
    }

    private View circle(LayoutInflater i, ControlId id) {
        View v = i.inflate(Res.layout("arrow_item_control_circle"), panel, false);
        FrameLayout body = v.findViewById(Res.id("arrow_circle_body"));
        ImageView icon = v.findViewById(Res.id("arrow_circle_icon"));
        ImageView badge = v.findViewById(Res.id("arrow_badge"));
        icon.setImageResource(id.iconRes);
        renderCircleState(v, id);
        badge.setVisibility(edit ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(x -> { repo.removeControl(id); rebuildUi(); });
        body.setOnClickListener(x -> {
            if (edit) return;
            if (id == ControlId.AUTO_BRIGHTNESS) {
                autoBright = !autoBright;
            } else {
                mute = !mute;
                applyVolume(mute ? 0 : Math.max(1, volume.getMax() / 2));
            }
            renderCircleState(v, id);
        });
        return v;
    }

    private void renderCircleState(View v, ControlId id) {
        FrameLayout body = v.findViewById(Res.id("arrow_circle_body"));
        ImageView icon = v.findViewById(Res.id("arrow_circle_icon"));
        boolean on = id == ControlId.AUTO_BRIGHTNESS ? autoBright : mute;
        body.setBackgroundResource(on ? Res.drawable("arrow_bg_cc_tile_on") : Res.drawable("arrow_bg_cc_tile_off"));
        icon.setImageTintList(ColorStateList.valueOf(color(on ? Res.color("arrow_cc_icon_on_accent") : Res.color("arrow_cc_icon"))));
    }

    private void applyBrightness(int n) {
        if (!brightness.canWrite()) {
            brightness.openWriteSettings();
            return;
        }
        brightness.setBrightness(n);
        for (VerticalLevelSlider s : brightnessV) s.setProgress(n);
        for (HorizontalLevelSlider s : brightnessH) s.setProgress(n);
    }

    private void applyVolume(int n) {
        volume.setVolume(n);
        for (VerticalLevelSlider s : volumeV) s.setProgress(n);
        for (HorizontalLevelSlider s : volumeH) s.setProgress(n);
    }

    private void rebuildTilesOnly() {
        final LinearLayout host = panel.findViewById(Res.id("arrow_tiles_host"));
        tileViews.clear();
        host.removeAllViews();
        host.post(() -> {
            int maxW = host.getWidth();
            if (maxW <= 0) {
                host.post(this::rebuildTilesOnly);
                return;
            }
            host.removeAllViews();
            tileViews.clear();
            int cols = Math.max(2, Math.min(4, repo.getTileColumns()));
            int tile = panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_tile_size"));
            int between = Math.max(0, maxW - cols * tile) / (cols - 1);
            int rowGap = panel.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
            List<TileId> values = repo.visibleTiles();
            int count = edit ? cols * repo.getTileRows() : values.size();
            LinearLayout row = null;
            for (int k = 0; k < count; k++) {
                if (k % cols == 0) {
                    row = new LinearLayout(panel.getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                    if (host.getChildCount() > 0) rlp.topMargin = rowGap;
                    host.addView(row, rlp);
                }
                View child = k < values.size() ? tile(LayoutInflater.from(panel.getContext()), values.get(k)) : emptyTile(tile);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tile, -2);
                if (k % cols > 0) lp.leftMargin = between;
                row.addView(child, lp);
            }
            scheduleDragAttach();
        });
    }

    private View emptyTile(int size) {
        LinearLayout out = new LinearLayout(panel.getContext());
        out.setOrientation(LinearLayout.VERTICAL);
        out.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout circle = new FrameLayout(panel.getContext());
        circle.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty"));
        out.addView(circle, new LinearLayout.LayoutParams(size, size));
        return out;
    }

    private View tile(LayoutInflater i, TileId id) {
        View v = i.inflate(Res.layout("arrow_item_tile"), panel, false);
        ImageView icon = v.findViewById(Res.id("arrow_tile_icon"));
        TextView label = v.findViewById(Res.id("arrow_tile_label"));
        ImageView badge = v.findViewById(Res.id("arrow_badge"));
        icon.setImageResource(id.iconRes);
        label.setText(id.labelRes);
        registerTileView(id, v);
        renderTileState(v, id);
        badge.setVisibility(edit ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(x -> { repo.removeTile(id); rebuildTilesOnly(); });
        v.findViewById(Res.id("arrow_tile_circle")).setOnClickListener(x -> { if (!edit) onTile(id, v); });
        return v;
    }

    private void registerTileView(TileId id, View v) {
        List<View> views = tileViews.get(id);
        if (views == null) {
            views = new ArrayList<>();
            tileViews.put(id, views);
        }
        views.add(v);
    }

    private void renderTileViews(TileId id) {
        List<View> views = tileViews.get(id);
        if (views == null) return;
        Iterator<View> it = views.iterator();
        while (it.hasNext()) {
            View v = it.next();
            if (v.getParent() == null) it.remove();
            else renderTileState(v, id);
        }
    }

    private void renderTileState(View v, TileId id) {
        FrameLayout circle = v.findViewById(Res.id("arrow_tile_circle"));
        ImageView icon = v.findViewById(Res.id("arrow_tile_icon"));
        TextView label = v.findViewById(Res.id("arrow_tile_label"));
        label.setVisibility(repo.getShowTileLabels() ? View.VISIBLE : View.GONE);
        boolean on = isTileActive(id);
        circle.setBackgroundResource(on ? Res.drawable("arrow_bg_cc_tile_on") : Res.drawable("arrow_bg_cc_tile_off"));
        icon.setImageTintList(ColorStateList.valueOf(color(on ? Res.color("arrow_cc_icon_on_accent") : Res.color("arrow_cc_icon"))));
    }

    private boolean isTileActive(TileId id) {
        if (id == TileId.FLASHLIGHT) return Boolean.TRUE.equals(active.get(id));
        if (id == TileId.ROTATION) return rotation != null && rotation.isRotationLocked();
        if (id == TileId.AIRPLANE) return connectivity != null && connectivity.isAirplaneMode();
        if (id == TileId.WIFI) return connectivity != null && connectivity.isWifiEnabled();
        return Boolean.TRUE.equals(active.get(id));
    }

    private void onTile(TileId id, View source) {
        if (id == TileId.FLASHLIGHT) {
            active.put(id, !Boolean.TRUE.equals(active.get(id)));
            renderTileViews(id);
            flashlight.toggle();
            return;
        }
        if (id == TileId.ROTATION) {
            if (!rotation.toggle()) connectivity.openDisplaySettings();
            renderTileState(source, id);
            return;
        }
        if (id == TileId.AIRPLANE) {
            connectivity.openAirplaneSettings();
            panel.postDelayed(() -> renderTileViews(id), 350);
            return;
        }
        if (id == TileId.WIFI) {
            connectivity.toggleWifi();
            renderTileViews(id);
            panel.postDelayed(() -> renderTileViews(id), 350);
            return;
        }
        active.put(id, !Boolean.TRUE.equals(active.get(id)));
        renderTileViews(id);
    }

    @Override public void onFlashlightChanged(boolean on) {
        active.put(TileId.FLASHLIGHT, on);
        panel.post(() -> renderTileViews(TileId.FLASHLIGHT));
    }

    @Override public void onFlashlightError() {
        Toast.makeText(panel.getContext(), Res.string("arrow_need_camera"), Toast.LENGTH_SHORT).show();
    }

    @Override public void onFlashlightAvailabilityChanged(boolean a) { }
}
