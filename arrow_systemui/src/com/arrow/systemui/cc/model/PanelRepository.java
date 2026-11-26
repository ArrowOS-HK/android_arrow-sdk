package com.arrow.systemui.cc.model;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;


public class PanelRepository {
    private static final String PREFS = "invmod_panel";
    private static final String CONTROLS = "controls";
    private static final String TILES = "tiles";
    private static final String LABELS = "show_labels";
    private static final String CTRL_COLS = "ctrl_cols";
    private static final String CTRL_ROWS = "ctrl_rows";
    private static final String TILE_COLS = "tile_cols";
    private static final String TILE_ROWS = "tile_rows";
    private static final String MEDIA_SIZE = "media_size";


    private static final int[] MEDIA_SPAN_H = new int[] { 1, 2, 2, 2, 1, 1 };
    private static final int[] MEDIA_SPAN_W = new int[] { 2, 2, 3, 4, 3, 4 };
    private static final String[] MEDIA_SIZE_NAMES = new String[] {
            "1×2", "2×2", "2×3", "2×4", "1×3", "1×4"
    };

    private final SharedPreferences prefs;

    public PanelRepository(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int limit(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public boolean getShowTileLabels() {
        return prefs.getBoolean(LABELS, false);
    }

    public void setShowTileLabels(boolean v) {
        prefs.edit().putBoolean(LABELS, v).apply();
    }

    public int getControlColumns() {
        return limit(prefs.getInt(CTRL_COLS, 4), 2, 4);
    }

    public void setControlColumns(int v) {
        prefs.edit().putInt(CTRL_COLS, limit(v, 2, 4)).apply();
        normalizeMediaSizeForGrid();
        trimControlsToCapacity();
    }

    public int getControlRows() {
        return limit(prefs.getInt(CTRL_ROWS, 3), 1, 3);
    }

    public void setControlRows(int v) {
        prefs.edit().putInt(CTRL_ROWS, limit(v, 1, 3)).apply();
        normalizeMediaSizeForGrid();
        trimControlsToCapacity();
    }

    public int getTileColumns() {
        return limit(prefs.getInt(TILE_COLS, 4), 2, 4);
    }

    public void setTileColumns(int v) {
        prefs.edit().putInt(TILE_COLS, limit(v, 2, 4)).apply();
        trimTilesToCapacity();
    }

    public int getTileRows() {
        return limit(prefs.getInt(TILE_ROWS, 3), 1, 4);
    }

    public void setTileRows(int v) {
        prefs.edit().putInt(TILE_ROWS, limit(v, 1, 4)).apply();
        trimTilesToCapacity();
    }


    public int getMediaSizeIndex() {
        int index = limit(prefs.getInt(MEDIA_SIZE, 1), 0, MEDIA_SPAN_W.length - 1);
        int cols = getControlColumns();
        int rows = getControlRows();
        while (index > 0 && (MEDIA_SPAN_W[index] > cols || MEDIA_SPAN_H[index] > rows)) index--;
        return index;
    }

    public void setMediaSizeIndex(int v) {
        int index = limit(v, 0, MEDIA_SPAN_W.length - 1);
        SharedPreferences.Editor editor = prefs.edit().putInt(MEDIA_SIZE, index);
        if (getControlColumns() < MEDIA_SPAN_W[index]) editor.putInt(CTRL_COLS, MEDIA_SPAN_W[index]);
        if (getControlRows() < MEDIA_SPAN_H[index]) editor.putInt(CTRL_ROWS, MEDIA_SPAN_H[index]);
        editor.apply();
        trimControlsToCapacity();
    }

    public String getMediaSizeName() {
        return MEDIA_SIZE_NAMES[getMediaSizeIndex()];
    }

    public int getMediaSpanW() {
        return MEDIA_SPAN_W[getMediaSizeIndex()];
    }

    public int getMediaSpanH() {
        return MEDIA_SPAN_H[getMediaSizeIndex()];
    }

    public int getControlSpanW(ControlId id) {
        if (id == ControlId.MEDIA) return getMediaSpanW();
        return id.spanW;
    }

    public int getControlSpanH(ControlId id) {
        if (id == ControlId.MEDIA) return getMediaSpanH();
        return id.spanH;
    }

    private void normalizeMediaSizeForGrid() {
        int raw = limit(prefs.getInt(MEDIA_SIZE, 1), 0, MEDIA_SPAN_W.length - 1);
        int fitted = raw;
        int cols = getControlColumns();
        int rows = getControlRows();
        while (fitted > 0 && (MEDIA_SPAN_W[fitted] > cols || MEDIA_SPAN_H[fitted] > rows)) fitted--;
        if (fitted != raw) prefs.edit().putInt(MEDIA_SIZE, fitted).apply();
    }

    @SuppressWarnings("unchecked")
    private <T> ArrayList<T> parse(String raw, boolean controls) {
        ArrayList<T> out = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String s : raw.split(",")) {
                Object o = controls ? ControlId.fromKey(s.trim()) : TileId.fromKey(s.trim());
                if (o != null) out.add((T) o);
            }
        }
        return out;
    }

    public ArrayList<ControlId> getControls() {
        ArrayList<ControlId> r = parse(prefs.getString(CONTROLS, null), true);
        if (r.isEmpty()) r = new ArrayList<>(ControlId.defaultOrder());
        ArrayList<ControlId> fitted = fitControls(r);
        if (fitted.size() != r.size()) setControls(fitted);
        return fitted;
    }

    public List<ControlId> visibleControls() {
        return getControls();
    }

    public void setControls(List<ControlId> list) {
        save(CONTROLS, fitControls(list));
    }

    public ArrayList<TileId> getTiles() {
        ArrayList<TileId> r = parse(prefs.getString(TILES, null), false);
        if (r.isEmpty()) r = new ArrayList<>(TileId.defaultOrder());
        if (r.size() > maxVisibleTiles()) {
            r = new ArrayList<>(r.subList(0, maxVisibleTiles()));
            setTiles(r);
        }
        return r;
    }

    public void setTiles(List<TileId> list) {
        save(TILES, list);
    }

    private void save(String key, List<?> list) {
        StringBuilder b = new StringBuilder();
        for (Object x : list) {
            if (b.length() > 0) b.append(',');
            b.append(((Enum<?>) x).name());
        }
        prefs.edit().putString(key, b.toString()).apply();
    }

    public boolean swapControlIds(ControlId a, ControlId b) {
        ArrayList<ControlId> x = getControls();
        int i = x.indexOf(a), j = x.indexOf(b);
        if (i < 0 || j < 0 || i == j) return false;
        x.set(i, b);
        x.set(j, a);
        setControls(x);
        return true;
    }

    public boolean swapTileIds(TileId a, TileId b) {
        ArrayList<TileId> x = getTiles();
        int i = x.indexOf(a), j = x.indexOf(b);
        if (i < 0 || j < 0 || i == j) return false;
        x.set(i, b);
        x.set(j, a);
        setTiles(x);
        return true;
    }

    public void moveControlToIndex(ControlId id, int index) {
        ArrayList<ControlId> x = getControls();
        if (!x.remove(id)) return;
        x.add(Math.max(0, Math.min(index, x.size())), id);
        setControls(x);
    }

    public void moveTileToIndex(TileId id, int index) {
        ArrayList<TileId> x = getTiles();
        if (!x.remove(id)) return;
        x.add(Math.max(0, Math.min(index, x.size())), id);
        setTiles(x);
    }

    public int maxVisibleTiles() {
        return getTileColumns() * getTileRows();
    }

    public List<TileId> visibleTiles() {
        ArrayList<TileId> x = getTiles();
        return x.subList(0, Math.min(x.size(), maxVisibleTiles()));
    }

    public String addControl(ControlId id) {
        ArrayList<ControlId> x = getControls();
        if (x.contains(id)) return "exists";
        int media = x.indexOf(ControlId.MEDIA);
        if (id == ControlId.BRIGHTNESS_H || id == ControlId.VOLUME_H
                || id == ControlId.BRIGHTNESS_V || id == ControlId.VOLUME_V) {
            x.add(media < 0 ? x.size() : media + 1, id);
        } else {
            x.add(id);
        }
        if (fitControls(x).size() != x.size()) return "control_full";
        setControls(x);
        return null;
    }

    public void removeControl(ControlId id) {
        ArrayList<ControlId> x = getControls();
        x.remove(id);
        setControls(x);
    }

    public String addTile(TileId id) {
        ArrayList<TileId> x = getTiles();
        if (x.contains(id)) return "exists";
        if (x.size() >= maxVisibleTiles()) return "tile_full";
        x.add(id);
        setTiles(x);
        return null;
    }

    public void removeTile(TileId id) {
        ArrayList<TileId> x = getTiles();
        x.remove(id);
        setTiles(x);
    }

    public void moveTile(int from, int to) {
        ArrayList<TileId> x = getTiles();
        if (from < 0 || from >= x.size() || to < 0 || to >= x.size()) return;
        x.add(to, x.remove(from));
        setTiles(x);
    }

    public void moveControl(int from, int to) {
        ArrayList<ControlId> x = getControls();
        if (from < 0 || from >= x.size() || to < 0 || to >= x.size()) return;
        x.add(to, x.remove(from));
        setControls(x);
    }

    private void trimTilesToCapacity() {
        ArrayList<TileId> x = getTiles();
        if (x.size() > maxVisibleTiles()) setTiles(x.subList(0, maxVisibleTiles()));
    }

    private void trimControlsToCapacity() {
        ArrayList<ControlId> x = parse(prefs.getString(CONTROLS, null), true);
        if (x.isEmpty()) x = new ArrayList<>(ControlId.defaultOrder());
        ArrayList<ControlId> fitted = fitControls(x);
        if (fitted.size() != x.size()) save(CONTROLS, fitted);
    }

    private ArrayList<ControlId> fitControls(List<ControlId> source) {
        int cols = getControlColumns();
        int rows = getControlRows();
        ArrayList<ControlId> out = new ArrayList<>();
        for (ControlId id : source) {
            out.add(id);
            if (canLayout(out, cols, rows)) continue;

            out.remove(out.size() - 1);
            if (id != ControlId.MEDIA) continue;


            out.add(id);
            while (!out.isEmpty() && !canLayout(out, cols, rows)) {
                int removeIndex = out.size() - 2;
                if (removeIndex < 0) {
                    out.remove(out.size() - 1);
                    break;
                }
                out.remove(removeIndex);
            }
        }
        return out;
    }

    private boolean canLayout(List<ControlId> source, int cols, int rows) {
        boolean[][] occupied = new boolean[rows][cols];
        for (ControlId id : source) {
            int sw = Math.max(1, Math.min(cols, getControlSpanW(id)));
            int sh = Math.max(1, getControlSpanH(id));
            boolean placed = false;
            outer:
            for (int r = 0; r <= rows - sh; r++) {
                for (int c = 0; c <= cols - sw; c++) {
                    if (free(occupied, r, c, sw, sh)) {
                        mark(occupied, r, c, sw, sh);
                        placed = true;
                        break outer;
                    }
                }
            }
            if (!placed) return false;
        }
        return true;
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

    public void resetDefaults() {
        prefs.edit().clear().apply();
    }
}
