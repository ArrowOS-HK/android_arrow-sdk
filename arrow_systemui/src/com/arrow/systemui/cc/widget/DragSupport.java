package com.arrow.systemui.cc.widget;

import android.content.ClipData;
import android.graphics.Rect;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.arrow.systemui.cc.Res;
import com.arrow.systemui.cc.model.ControlId;
import com.arrow.systemui.cc.model.PanelRepository;
import com.arrow.systemui.cc.model.TileId;


final class DragSupport {
    private static final Map<QsPanelView, Session> SESSIONS = new WeakHashMap<>();

    private static final class Session {
        boolean active;
        boolean changed;
        boolean tile;
        View source;
        View target;
    }

    private DragSupport() { }

    static void attach(QsPanelView p, PanelRepository repo) {
        unclip(p);

        LinearLayout host = p.findViewById(Res.id("arrow_controls_host"));
        if (host.getChildCount() > 0 && host.getChildAt(0) instanceof FrameLayout) {
            FrameLayout wrap = (FrameLayout) host.getChildAt(0);
            GridLayout grid = findGrid(wrap);
            if (grid != null) {
                List<ControlId> ids = repo.getControls();
                for (int i = 0; i < Math.min(ids.size(), grid.getChildCount()); i++) {
                    control(p, repo, grid.getChildAt(i), ids.get(i));
                }
                emptyControls(p, repo, wrap);
            }
        }

        LinearLayout tileHost = p.findViewById(Res.id("arrow_tiles_host"));
        List<TileId> ids = repo.visibleTiles();
        ArrayList<View> cells = new ArrayList<>();
        for (int r = 0; r < tileHost.getChildCount(); r++) {
            if (tileHost.getChildAt(r) instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) tileHost.getChildAt(r);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                for (int c = 0; c < row.getChildCount(); c++) cells.add(row.getChildAt(c));
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            if (i < ids.size()) tile(p, repo, cells.get(i), ids.get(i));
            else emptyTile(p, repo, cells.get(i), i);
        }
    }

    private static GridLayout findGrid(FrameLayout wrap) {
        for (int i = 0; i < wrap.getChildCount(); i++) {
            if (wrap.getChildAt(i) instanceof GridLayout) return (GridLayout) wrap.getChildAt(i);
        }
        return null;
    }

    private static void unclip(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            g.setClipChildren(false);
            g.setClipToPadding(false);
            for (int i = 0; i < g.getChildCount(); i++) unclip(g.getChildAt(i));
        }
    }

    private static Session session(QsPanelView p) {
        Session s = SESSIONS.get(p);
        if (s == null) {
            s = new Session();
            SESSIONS.put(p, s);
        }
        return s;
    }

    private static void lift(QsPanelView p, View root, boolean tile) {
        Session s = session(p);
        s.active = true;
        s.changed = false;
        s.tile = tile;
        s.source = root;
        s.target = null;
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        root.animate().cancel();
        root.animate()
                .scaleX(.96f)
                .scaleY(.96f)
                .alpha(.50f)
                .translationZ(18f)
                .setDuration(120L)
                .start();
    }

    private static void hover(View v, boolean on) {
        v.animate().cancel();
        v.animate().scaleX(on ? 1.035f : 1f).scaleY(on ? 1.035f : 1f)
                .alpha(on ? .9f : 1f).setDuration(150L).start();
    }

    private static void changed(QsPanelView p) { session(p).changed = true; }

    private static void finish(QsPanelView p, PanelRepository repo) {
        Session s = session(p);
        if (!s.active) return;
        s.active = false;
        View source = s.source;
        View target = s.target;
        boolean changed = s.changed;
        boolean tile = s.tile;
        s.source = null;
        s.target = null;

        if (source != null) {
            source.animate().cancel();
            source.animate().alpha(1).scaleX(1).scaleY(1).translationZ(0).setDuration(220L).start();
        }
        if (!changed) return;

        p.post(() -> {
            if (tile && source != null && target != null) {
                swapViews(source, target);
            } else if (!tile) {
                if (!relayoutControls(p, repo)) p.rebuild();
            } else {
                p.rebuild();
            }
            p.post(() -> attach(p, repo));
        });
    }

    private static boolean relayoutControls(QsPanelView p, PanelRepository repo) {
        LinearLayout host = p.findViewById(Res.id("arrow_controls_host"));
        if (host.getChildCount() == 0 || !(host.getChildAt(0) instanceof FrameLayout)) return false;
        FrameLayout wrap = (FrameLayout) host.getChildAt(0);
        GridLayout grid = findGrid(wrap);
        if (grid == null) return false;

        Map<ControlId, View> existing = new HashMap<>();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof ControlId) existing.put((ControlId) tag, child);
        }

        List<ControlId> ids = repo.getControls();
        if (existing.size() < ids.size()) return false;
        for (ControlId id : ids) if (!existing.containsKey(id)) return false;

        int maxW = host.getWidth();
        int cols = Math.max(2, Math.min(4, repo.getControlColumns()));
        int rowLimit = Math.max(1, Math.min(3, repo.getControlRows()));
        int cell = Math.min(p.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")), Math.max(1, maxW / cols));
        int gapV = p.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
        int gapH = cols > 1 ? Math.max(0, maxW - cols * cell) / (cols - 1) : 0;
        boolean[][] occ = new boolean[rowLimit][cols];
        ArrayList<ControlPlace> places = new ArrayList<>();

        for (ControlId id : ids) {
            int sw = Math.max(1, Math.min(cols, repo.getControlSpanW(id)));
            int sh = Math.max(1, repo.getControlSpanH(id));
            boolean placed = false;
            outer:
            for (int r = 0; r <= rowLimit - sh; r++) {
                for (int c = 0; c <= cols - sw; c++) {
                    if (free(occ, r, c, sw, sh)) {
                        mark(occ, r, c, sw, sh);
                        places.add(new ControlPlace(id, r, c, sw, sh));
                        placed = true;
                        break outer;
                    }
                }
            }
            if (!placed) return false;
        }

        int rows = rowLimit;
        for (ControlPlace place : places) rows = Math.max(rows, place.r + place.h);
        Map<View, Rect> beforeBounds = SmoothLayout.capture(grid);
        grid.removeAllViews();
        grid.setColumnCount(cols);
        grid.setRowCount(rows);

        for (ControlPlace place : places) {
            View child = existing.get(place.id);
            int w = cell * place.w + gapH * (place.w - 1);
            int h = cell * place.h + gapV * (place.h - 1);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(place.r, place.h, GridLayout.FILL),
                    GridLayout.spec(place.c, place.w, GridLayout.FILL));
            lp.width = w;
            lp.height = h;
            lp.rightMargin = place.c + place.w < cols ? gapH : 0;
            lp.bottomMargin = place.r + place.h < rows ? gapV : 0;
            lp.setGravity(android.view.Gravity.FILL);
            child.setLayoutParams(lp);
            child.setAlpha(1f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setTranslationZ(0f);
            grid.addView(child);
        }

        emptyControls(p, repo, wrap);
        SmoothLayout.play(grid, beforeBounds);
        return true;
    }

    private static final class ControlPlace {
        final ControlId id;
        final int r, c, w, h;
        ControlPlace(ControlId id, int r, int c, int w, int h) {
            this.id = id;
            this.r = r;
            this.c = c;
            this.w = w;
            this.h = h;
        }
    }

    private static void swapViews(View first, View second) {
        ViewGroup a = (ViewGroup) first.getParent();
        ViewGroup b = (ViewGroup) second.getParent();
        if (a == null || b == null) return;
        Map<View, Rect> beforeBounds = SmoothLayout.capture(a, b);
        int ai = a.indexOfChild(first);
        int bi = b.indexOfChild(second);
        ViewGroup.LayoutParams alp = first.getLayoutParams();
        ViewGroup.LayoutParams blp = second.getLayoutParams();
        if (a == b) {
            a.removeViewAt(Math.max(ai, bi));
            a.removeViewAt(Math.min(ai, bi));
            if (ai < bi) {
                a.addView(second, ai, alp);
                a.addView(first, bi, blp);
            } else {
                a.addView(first, bi, blp);
                a.addView(second, ai, alp);
            }
        } else {
            a.removeView(first);
            b.removeView(second);
            a.addView(second, ai, alp);
            b.addView(first, bi, blp);
        }
        first.setAlpha(1f);
        second.setAlpha(1f);
        SmoothLayout.play(a, beforeBounds);
    }

    private static void control(QsPanelView p, PanelRepository repo, View root, ControlId id) {
        root.setTag(id);
        View.OnLongClickListener start = v -> {
            lift(p, root, false);
            root.startDragAndDrop(ClipData.newPlainText("control", id.name()),
                    new View.DragShadowBuilder(root), id, 0);
            return true;
        };
        View.OnDragListener listener = (v, e) -> {
            if (!(e.getLocalState() instanceof ControlId)) return false;
            ControlId from = (ControlId) e.getLocalState();
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    if (from != id) hover(root, true);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    hover(root, false);
                    return true;
                case DragEvent.ACTION_DROP:
                    hover(root, false);
                    if (from != id && repo.swapControlIds(from, id)) {
                        Session s = session(p);
                        s.target = root;
                        changed(p);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    hover(root, false);
                    finish(p, repo);
                    return true;
                default:
                    return true;
            }
        };
        wire(root, start, listener);
    }

    private static void tile(QsPanelView p, PanelRepository repo, View root, TileId id) {
        View.OnLongClickListener start = v -> {
            lift(p, root, true);
            root.startDragAndDrop(ClipData.newPlainText("tile", id.name()),
                    new View.DragShadowBuilder(root), id, 0);
            return true;
        };
        View.OnDragListener listener = (v, e) -> {
            if (!(e.getLocalState() instanceof TileId)) return false;
            TileId from = (TileId) e.getLocalState();
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    if (from != id) hover(root, true);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    hover(root, false);
                    return true;
                case DragEvent.ACTION_DROP:
                    hover(root, false);
                    if (from != id && repo.swapTileIds(from, id)) {
                        Session s = session(p);
                        s.target = root;
                        changed(p);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    hover(root, false);
                    finish(p, repo);
                    return true;
                default:
                    return true;
            }
        };
        wire(root, start, listener);
    }

    private static void emptyTile(QsPanelView p, PanelRepository repo, View root, int index) {
        root.setOnDragListener((v, e) -> {
            if (!(e.getLocalState() instanceof TileId)) return false;
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    hover(v, true);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    hover(v, false);
                    return true;
                case DragEvent.ACTION_DROP:
                    hover(v, false);
                    repo.moveTileToIndex((TileId) e.getLocalState(), index);
                    Session s = session(p);
                    s.target = root;
                    changed(p);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    hover(v, false);
                    finish(p, repo);
                    return true;
                default:
                    return true;
            }
        });
    }

    private static void wire(View v, View.OnLongClickListener start, View.OnDragListener drop) {
        if (v.getId() == Res.id("arrow_media_resize_handle")) {
            v.setOnLongClickListener(null);
            v.setOnDragListener(null);
            v.setLongClickable(false);
            return;
        }
        v.setOnLongClickListener(start);
        v.setOnDragListener(drop);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) wire(g.getChildAt(i), start, drop);
        }
    }

    private static void emptyControls(QsPanelView p, PanelRepository repo, FrameLayout wrap) {
        while (wrap.getChildCount() > 1) wrap.removeViewAt(1);
        int maxW = wrap.getWidth();
        if (maxW <= 0 && wrap.getParent() instanceof View) maxW = ((View) wrap.getParent()).getWidth();
        int cols = Math.max(2, Math.min(4, repo.getControlColumns()));
        int rows = Math.max(1, Math.min(3, repo.getControlRows()));
        if (maxW <= 0) maxW = cols * p.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")) + (cols - 1) * p.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
        int cell = Math.min(p.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")), Math.max(1, maxW / cols));
        int gapV = p.getResources().getDimensionPixelSize(Res.dimen("arrow_cc_ctrl_item_gap"));
        int gapH = cols > 1 ? Math.max(0, maxW - cols * cell) / (cols - 1) : 0;
        boolean[][] used = new boolean[rows][cols];
        for (ControlId id : repo.getControls()) {
            int sw = Math.min(cols, Math.max(1, repo.getControlSpanW(id)));
            int sh = Math.max(1, repo.getControlSpanH(id));
            boolean ok = false;
            outer:
            for (int r = 0; r <= rows - sh; r++) {
                for (int c = 0; c <= cols - sw; c++) {
                    if (free(used, r, c, sw, sh)) {
                        mark(used, r, c, sw, sh);
                        ok = true;
                        break outer;
                    }
                }
            }
        }
        FrameLayout layer = new FrameLayout(p.getContext());
        layer.setClipChildren(false);
        wrap.addView(layer, new FrameLayout.LayoutParams(-1, rows * cell + (rows - 1) * gapV));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!used[r][c]) emptyControl(p, repo, layer, r, c, cell, gapH, gapV, cols);
            }
        }
    }

    private static boolean free(boolean[][] a, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) {
            for (int x = c; x < c + w; x++) if (a[y][x]) return false;
        }
        return true;
    }

    private static void mark(boolean[][] a, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) {
            for (int x = c; x < c + w; x++) a[y][x] = true;
        }
    }

    private static void emptyControl(QsPanelView p, PanelRepository repo, FrameLayout parent,
                                     int row, int col, int cell, int gapH, int gapV, int cols) {
        FrameLayout slot = new FrameLayout(p.getContext());
        slot.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty"));
        int index = row * cols + col;
        slot.setOnDragListener((v, e) -> {
            if (!(e.getLocalState() instanceof ControlId)) return false;
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    slot.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty_active"));
                    hover(v, true);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    slot.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty"));
                    hover(v, false);
                    return true;
                case DragEvent.ACTION_DROP:
                    slot.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty"));
                    hover(v, false);
                    repo.moveControlToIndex((ControlId) e.getLocalState(), index);
                    changed(p);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    slot.setBackgroundResource(Res.drawable("arrow_bg_cc_grid_empty"));
                    hover(v, false);
                    finish(p, repo);
                    return true;
                default:
                    return true;
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cell, cell);
        lp.leftMargin = col * (cell + gapH);
        lp.topMargin = row * (cell + gapV);
        parent.addView(slot, lp);
        SmoothLayout.appear(slot);
    }
}
