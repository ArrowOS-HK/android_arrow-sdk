package com.arrow.systemui.cc;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import com.arrow.systemui.cc.controller.BrightnessController;
import com.arrow.systemui.cc.controller.MediaSessionController;
import com.arrow.systemui.cc.controller.VolumeController;
import com.arrow.systemui.cc.model.ControlId;
import com.arrow.systemui.cc.model.PanelRepository;
import com.arrow.systemui.cc.model.TileId;
import com.arrow.systemui.cc.widget.HorizontalLevelSlider;
import com.arrow.systemui.cc.widget.MediaViewRenderer;
import com.arrow.systemui.cc.widget.VerticalLevelSlider;


public class PanelSettingsActivity extends Activity {
    private PanelRepository repo;
    private BrightnessController brightness;
    private VolumeController volume;
    private MediaSessionController mediaSession;
    private View mediaPreview;
    private Switch labels;
    private SeekBar ctrlCols, ctrlRows, tileCols, tileRows;
    private TextView ctrlText, ctrlRowText, tileText, rowText;
    private GridLayout controls, tiles;
    private LinearLayout strip;

    private static final Typeface GOOGLE_SANS = Typeface.create("google-sans", Typeface.NORMAL);
    private static final Typeface GOOGLE_SANS_MEDIUM = Typeface.create("google-sans-medium", Typeface.NORMAL);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Res.init(this);
        setContentView(Res.layout("arrow_activity_panel_settings"));
        repo = new PanelRepository(this);
        brightness = new BrightnessController(this);
        volume = new VolumeController(this);
        mediaSession = new MediaSessionController(this);
        mediaSession.start(() -> {
            if (mediaPreview != null) MediaViewRenderer.render(mediaPreview, mediaSession, repo.getMediaSpanW(), repo.getMediaSpanH());
        });

        labels = findViewById(Res.id("arrow_switch_labels"));
        ctrlCols = findViewById(Res.id("arrow_seek_ctrl_cols"));
        ctrlRows = findViewById(Res.id("arrow_seek_ctrl_rows"));
        tileCols = findViewById(Res.id("arrow_seek_tile_cols"));
        tileRows = findViewById(Res.id("arrow_seek_tile_rows"));
        ctrlText = findViewById(Res.id("arrow_txt_ctrl_cols"));
        ctrlRowText = findViewById(Res.id("arrow_txt_ctrl_rows"));
        tileText = findViewById(Res.id("arrow_txt_tile_cols"));
        rowText = findViewById(Res.id("arrow_txt_tile_rows"));
        controls = findViewById(Res.id("arrow_catalog_controls"));
        tiles = findViewById(Res.id("arrow_catalog_tiles"));
        strip = findViewById(Res.id("arrow_preview_tile_strip"));

        findViewById(Res.id("arrow_btn_back")).setOnClickListener(v -> finish());
        findViewById(Res.id("arrow_btn_reset")).setOnClickListener(v -> {
            repo.resetDefaults();
            bind();
            catalog();
            strip();
            Toast.makeText(this, Res.string("arrow_layout_reset"), Toast.LENGTH_SHORT).show();
        });
        labels.setOnCheckedChangeListener((x, on) -> repo.setShowTileLabels(on));
        ctrlCols.setOnSeekBarChangeListener(listen(v -> {
            repo.setControlColumns(v + 2);
            bind();
            catalog();
        }));
        ctrlRows.setOnSeekBarChangeListener(listen(v -> {
            repo.setControlRows(v + 1);
            bind();
            catalog();
        }));
        tileCols.setOnSeekBarChangeListener(listen(v -> {
            repo.setTileColumns(v + 2);
            text();
            catalog();
            strip();
        }));
        tileRows.setOnSeekBarChangeListener(listen(v -> {
            repo.setTileRows(v + 1);
            text();
            catalog();
            strip();
        }));
        bind();
        catalog();
        strip();
    }

    private interface Change { void value(int x); }

    private SeekBar.OnSeekBarChangeListener listen(Change c) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) c.value(p); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        };
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    private void bind() {
        labels.setChecked(repo.getShowTileLabels());
        ctrlCols.setProgress(repo.getControlColumns() - 2);
        ctrlRows.setProgress(repo.getControlRows() - 1);
        tileCols.setProgress(repo.getTileColumns() - 2);
        tileRows.setProgress(repo.getTileRows() - 1);
        text();
    }

    private void text() {
        ctrlText.setText(getString(Res.string("arrow_columns_fmt"), repo.getControlColumns()));
        ctrlRowText.setText(getString(Res.string("arrow_rows_fmt"), repo.getControlRows()));
        tileText.setText(getString(Res.string("arrow_columns_fmt"), repo.getTileColumns()));
        rowText.setText(getString(Res.string("arrow_rows_fmt"), repo.getTileRows()));
    }

    private void strip() {
        strip.removeAllViews();
        strip.post(() -> {
            int w = strip.getWidth();
            if (w <= 0) {
                strip.post(this::strip);
                return;
            }
            int n = Math.min(4, repo.visibleTiles().size());
            int m = dp(6);
            int s = Math.max(dp(40), Math.min(getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")),
                    (w - m * 2 * Math.max(1, n)) / Math.max(1, n)));
            for (int i = 0; i < n; i++) {
                TileId id = repo.visibleTiles().get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);

                FrameLayout c = new FrameLayout(this);
                c.setBackgroundResource(Res.drawable("arrow_bg_cc_tile_off"));
                ImageView icon = new ImageView(this);
                icon.setImageResource(id.iconRes);
                icon.setImageTintList(ColorStateList.valueOf(getColor(Res.color("arrow_cc_icon"))));
                c.addView(icon, new FrameLayout.LayoutParams((int) (s * .4f), (int) (s * .4f), Gravity.CENTER));
                item.addView(c, new LinearLayout.LayoutParams(s, s));

                TextView title = new TextView(this);
                title.setText(id.labelRes);
                title.setTextColor(getColor(Res.color("arrow_cc_text_secondary")));
                title.setTextSize(14);
                title.setTypeface(GOOGLE_SANS);
                title.setGravity(Gravity.CENTER);
                title.setSingleLine(false);
                title.setMaxLines(2);
                title.setIncludeFontPadding(false);
                title.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(s, dp(38));
                tlp.topMargin = dp(4);
                item.addView(title, tlp);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s + dp(44));
                lp.setMarginStart(m);
                lp.setMarginEnd(m);
                strip.addView(item, lp);
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (mediaSession != null) mediaSession.refresh();
    }

    @Override protected void onDestroy() {
        if (mediaSession != null) mediaSession.stop();
        super.onDestroy();
    }

    private void catalog() {
        mediaPreview = null;
        controls.removeAllViews();
        tiles.removeAllViews();
        controls.post(this::buildControlGrid);
        tiles.post(this::buildTileGrid);
    }

    private void buildControlGrid() {
        int width = controls.getWidth();
        if (width <= 0) {
            controls.post(this::buildControlGrid);
            return;
        }
        final int cols = 4;
        final int unit = Math.min(getResources().getDimensionPixelSize(Res.dimen("arrow_cc_unit")), width / cols);
        final int gap = Math.max(0, width - cols * unit) / (cols - 1);
        controls.setColumnCount(cols);
        controls.setUseDefaultMargins(false);
        controls.removeAllViews();

        List<ControlId> list = new ArrayList<>();
        for (ControlId id : ControlId.all()) if (!repo.getControls().contains(id)) list.add(id);

        boolean[][] occupied = new boolean[Math.max(2, list.size() * 2 + 2)][cols];
        int rows = 0;
        for (ControlId id : list) {
            int sw = Math.min(cols, Math.max(1, repo.getControlSpanW(id)));
            int sh = Math.max(1, repo.getControlSpanH(id));
            outer:
            for (int r = 0; r < occupied.length - sh; r++) for (int c = 0; c <= cols - sw; c++) {
                if (free(occupied, r, c, sw, sh)) {
                    mark(occupied, r, c, sw, sh);
                    rows = Math.max(rows, r + sh);
                    addControlCell(id, r, c, sw, sh, unit, gap);
                    break outer;
                }
            }
        }
        controls.setRowCount(Math.max(1, rows));
    }

    private void addControlCell(ControlId id, int row, int col, int sw, int sh, int unit, int gap) {
        int w = unit * sw + gap * (sw - 1);
        int previewH = unit * sh + gap * (sh - 1);
        int titleH = dp(46);
        FrameLayout cell = new FrameLayout(this);
        cell.setClipChildren(false);

        View preview = previewControl(id, sw, sh);
        cell.addView(preview, new FrameLayout.LayoutParams(w, previewH, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(this);
        title.setText(id.labelRes);
        title.setTextColor(getColor(Res.color("arrow_cc_text_secondary")));
        title.setTextSize(14);
        title.setTypeface(GOOGLE_SANS);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setIncludeFontPadding(false);
        title.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(title, new FrameLayout.LayoutParams(w, titleH, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        ImageView add = new ImageView(this);
        add.setBackgroundResource(Res.drawable("arrow_bg_cc_badge"));
        add.setImageResource(Res.drawable("arrow_ic_add"));
        add.setImageTintList(ColorStateList.valueOf(getColor(Res.color("arrow_cc_badge_on_badge"))));
        add.setPadding(dp(3), dp(3), dp(3), dp(3));
        add.setContentDescription(getString(Res.string("arrow_add")));
        add.setOnClickListener(v -> {
            String result = repo.addControl(id);
            if ("control_full".equals(result)) {
                Toast.makeText(this, Res.string("arrow_toast_control_full"), Toast.LENGTH_SHORT).show();
            } else if ("exists".equals(result)) {
                Toast.makeText(this, Res.string("arrow_toast_already_added"), Toast.LENGTH_SHORT).show();
            }
            catalog();
        });
        cell.addView(add, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(row, sh, GridLayout.FILL), GridLayout.spec(col, sw, GridLayout.FILL));
        lp.width = w;
        lp.height = previewH + titleH;
        lp.rightMargin = col + sw < 4 ? gap : 0;
        lp.bottomMargin = gap;
        controls.addView(cell, lp);
    }

    private View previewControl(ControlId id, int spanW, int spanH) {
        LayoutInflater in = LayoutInflater.from(this);
        View v;
        switch (id) {
            case MEDIA:
                v = in.inflate(Res.layout("arrow_item_media"), controls, false);
                mediaPreview = v;
                MediaViewRenderer.render(v, mediaSession, spanW, spanH);
                break;
            case BRIGHTNESS_V:
            case VOLUME_V: {
                v = in.inflate(Res.layout("arrow_item_slider_vertical"), controls, false);
                FrameLayout body = v.findViewById(Res.id("arrow_slider_body"));
                View fill = v.findViewById(Res.id("arrow_slider_fill"));
                ImageView icon = v.findViewById(Res.id("arrow_slider_icon"));
                icon.setImageResource(id.iconRes);
                boolean bright = id == ControlId.BRIGHTNESS_V;
                int max = bright ? 255 : Math.max(1, volume.getMax());
                int value = bright ? brightness.getBrightness() : volume.getVolume();
                VerticalLevelSlider.applyStaticProgress(body, fill, max, value, icon,
                        getColor(Res.color("arrow_cc_icon")), getColor(Res.color("arrow_cc_on_accent")));
                break;
            }
            case BRIGHTNESS_H:
            case VOLUME_H: {
                v = in.inflate(Res.layout("arrow_item_slider_horizontal"), controls, false);
                FrameLayout body = v.findViewById(Res.id("arrow_slider_body"));
                View fill = v.findViewById(Res.id("arrow_slider_fill"));
                ImageView icon = v.findViewById(Res.id("arrow_slider_icon"));
                icon.setImageResource(id.iconRes);
                boolean bright = id == ControlId.BRIGHTNESS_H;
                int max = bright ? 255 : Math.max(1, volume.getMax());
                int value = bright ? brightness.getBrightness() : volume.getVolume();
                HorizontalLevelSlider.applyStaticProgress(body, fill, max, value, icon,
                        getColor(Res.color("arrow_cc_icon")), getColor(Res.color("arrow_cc_on_accent")));
                break;
            }
            case INTERNET:
            case BLUETOOTH:
            case RINGER:
                v = in.inflate(Res.layout("arrow_item_pill"), controls, false);
                ((ImageView) v.findViewById(Res.id("arrow_pill_icon"))).setImageResource(id.iconRes);
                ((TextView) v.findViewById(Res.id("arrow_pill_label"))).setText(id.labelRes);
                break;
            default:
                v = in.inflate(Res.layout("arrow_item_control_circle"), controls, false);
                ((ImageView) v.findViewById(Res.id("arrow_circle_icon"))).setImageResource(id.iconRes);
                break;
        }
        View badge = v.findViewById(Res.id("arrow_badge"));
        if (badge != null) badge.setVisibility(View.GONE);
        return v;
    }

    private void buildTileGrid() {
        int width = tiles.getWidth();
        if (width <= 0) {
            tiles.post(this::buildTileGrid);
            return;
        }
        int cols = 4;
        int cell = Math.max(dp(40), width / cols);
        int i = 0;
        tiles.setColumnCount(cols);
        tiles.removeAllViews();
        for (TileId id : TileId.all()) if (!repo.getTiles().contains(id)) {
            View item = LayoutInflater.from(this).inflate(Res.layout("arrow_item_catalog_tile"), tiles, false);
            FrameLayout body = item.findViewById(Res.id("arrow_tile_body"));
            int size = Math.max(dp(40), cell - dp(8));
            body.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL));
            ImageView icon = item.findViewById(Res.id("arrow_icon"));
            int iconSize = Math.min(getResources().getDimensionPixelSize(Res.dimen("arrow_cc_tile_icon")), (int) (size * .4f));
            icon.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            icon.setImageResource(id.iconRes);
            TextView title = item.findViewById(Res.id("arrow_tile_title"));
            title.setText(id.labelRes);
            item.findViewById(Res.id("arrow_btn_add")).setOnClickListener(v -> {
                String result = repo.addTile(id);
                if ("tile_full".equals(result)) {
                    Toast.makeText(this, getString(Res.string("arrow_toast_tile_full"), repo.getTiles().size(), repo.maxVisibleTiles()), Toast.LENGTH_SHORT).show();
                } else if ("exists".equals(result)) {
                    Toast.makeText(this, Res.string("arrow_toast_already_added"), Toast.LENGTH_SHORT).show();
                }
                catalog();
                strip();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / cols), GridLayout.spec(i % cols, 1f));
            lp.width = cell;
            lp.height = -2;
            tiles.addView(item, lp);
            i++;
        }
    }

    private static boolean free(boolean[][] a, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) for (int x = c; x < c + w; x++) if (a[y][x]) return false;
        return true;
    }

    private static void mark(boolean[][] a, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) for (int x = c; x < c + w; x++) a[y][x] = true;
    }
}
