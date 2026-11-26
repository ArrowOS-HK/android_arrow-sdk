package com.arrow.systemui.cc.widget;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.arrow.systemui.cc.PanelSettingsActivity;
import com.arrow.systemui.cc.Res;


public class QsPanelView extends LinearLayout {
    private static final Typeface GOOGLE_SANS = Typeface.create("google-sans", Typeface.NORMAL);
    private static final Typeface GOOGLE_SANS_MEDIUM = Typeface.create("google-sans-medium", Typeface.NORMAL);

    private static final String TAG = "ArrowQsPanelView";

    private QsPanelController controller;
    private boolean bound;

    public QsPanelView(Context context) {
        this(context, null);
    }

    public QsPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsPanelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        try {
            Res.init(context);
            setOrientation(VERTICAL);
            setClipChildren(false);
            setClipToPadding(false);
            buildContent(context);
            controller = new QsPanelController(this);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "failed to build Control Center", t);
            controller = null;
        }
    }

    public synchronized void bind() {
        if (bound || controller == null) return;
        bound = true;
        controller.bind();
        ((TextView) findViewById(Res.id("arrow_txt_date"))).setText(
                new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(new Date()));
        findViewById(Res.id("arrow_btn_edit")).setOnClickListener(v -> controller.setEditMode(true));
        findViewById(Res.id("arrow_btn_done")).setOnClickListener(v -> controller.setEditMode(false));
        View.OnClickListener open = v -> {
            Intent intent = new Intent(getContext(), PanelSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        };
        findViewById(Res.id("arrow_btn_settings")).setOnClickListener(open);
        findViewById(Res.id("arrow_btn_panel_settings")).setOnClickListener(open);
        findViewById(Res.id("arrow_btn_more")).setOnClickListener(open);
    }

    public void onHostResume() {
        if (controller != null) controller.onResume();
    }

    public synchronized void onHostDestroy() {
        if (controller != null) controller.onDestroy();
        // allow rebind on re-attach
        bound = false;
    }

    public void rebuild() {
        if (controller != null) controller.rebuildUi();
    }

    public boolean isEditMode() {
        return controller != null && controller.isEditMode();
    }

    private void buildContent(Context context) {
        addView(normalHeader(context), matchWrap());
        addView(editHeader(context), matchWrap());
        addView(text("txt_edit_hint", Res.string("arrow_touch_hold_reorder"), 14, true), matchWrap());
        addView(label("label_controls", Res.string("arrow_controls"), 0, 8));
        addView(box("controls_box", "controls_host", 0));
        addView(label("label_tiles", Res.string("arrow_edit_tiles"), 16, 8));
        addView(box("tiles_box", "tiles_host", 14));
    }

    private LinearLayout normalHeader(Context context) {
        LinearLayout header = row("header_normal");
        header.setPadding(0, 0, 0, dp(14));

        TextView date = new TextView(context);
        date.setId(Res.id("arrow_txt_date"));
        date.setTextColor(color(Res.color("arrow_cc_text_primary")));
        date.setTextSize(22);
        date.setTypeface(GOOGLE_SANS_MEDIUM);
        header.addView(date, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        header.addView(headerButton("btn_edit", Res.drawable("arrow_ic_edit"), Res.string("arrow_edit"), true));
        header.addView(headerButton("btn_settings", Res.drawable("arrow_ic_settings"), Res.string("arrow_settings"), true));
        header.addView(headerButton("btn_more", Res.drawable("arrow_ic_more"), Res.string("arrow_more"), false));
        return header;
    }

    private LinearLayout editHeader(Context context) {
        LinearLayout header = row("header_edit");
        header.setPadding(0, 0, 0, dp(8));
        header.setVisibility(GONE);

        TextView settings = pillButton("btn_panel_settings", Res.string("arrow_panel_settings"),
                Res.drawable("arrow_bg_btn_secondary_pill"), color(Res.color("arrow_cc_text_primary")), 16);
        header.addView(settings, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)));

        View spacer = new View(context);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView done = pillButton("btn_done", Res.string("arrow_done"), Res.drawable("arrow_bg_btn_done"),
                color(Res.color("arrow_cc_text_on_accent")), 22);
        header.addView(done, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)));
        return header;
    }

    private LinearLayout row(String idName) {
        LinearLayout row = new LinearLayout(getContext());
        row.setId(Res.id(idName));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(HORIZONTAL);
        return row;
    }

    private TextView pillButton(String idName, int textRes, int bgRes, int textColor, int horizontalPadding) {
        TextView button = new TextView(getContext());
        button.setId(Res.id(idName));
        button.setBackgroundResource(bgRes);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(horizontalPadding), 0, dp(horizontalPadding), 0);
        button.setText(textRes);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(GOOGLE_SANS_MEDIUM);
        return button;
    }

    private ImageButton headerButton(String idName, int iconRes, int contentDescriptionRes, boolean hasEndMargin) {
        ImageButton button = new ImageButton(getContext());
        button.setId(Res.id(idName));
        button.setBackgroundResource(Res.drawable("arrow_bg_cc_header_btn"));
        button.setContentDescription(getContext().getString(contentDescriptionRes));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setImageResource(iconRes);
        button.setImageTintList(ColorStateList.valueOf(color(Res.color("arrow_cc_icon"))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(Res.dimen("arrow_cc_header_btn")),
                getResources().getDimensionPixelSize(Res.dimen("arrow_cc_header_btn")));
        if (hasEndMargin) lp.setMarginEnd(dp(8));
        button.setLayoutParams(lp);
        return button;
    }

    private TextView text(String idName, int textRes, int textSize, boolean centered) {
        TextView value = new TextView(getContext());
        value.setId(Res.id(idName));
        value.setText(textRes);
        value.setTextColor(color(Res.color("arrow_cc_text_secondary")));
        value.setTextSize(textSize);
        value.setPadding(0, 0, 0, dp(10));
        if (centered) value.setGravity(Gravity.CENTER);
        value.setVisibility(GONE);
        return value;
    }

    private TextView label(String idName, int textRes, int topMargin, int bottomPadding) {
        TextView value = new TextView(getContext());
        value.setId(Res.id(idName));
        value.setText(textRes);
        value.setTextColor(color(Res.color("arrow_cc_text_primary")));
        value.setTextSize(16);
        value.setTypeface(GOOGLE_SANS_MEDIUM);
        value.setPadding(0, 0, 0, dp(bottomPadding));
        value.setVisibility(GONE);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topMargin);
        value.setLayoutParams(lp);
        return value;
    }

    private FrameLayout box(String boxId, String hostId, int topMargin) {
        FrameLayout box = new FrameLayout(getContext());
        box.setId(Res.id(boxId));
        box.setClipChildren(false);
        box.setClipToPadding(false);

        LinearLayout host = new LinearLayout(getContext());
        host.setId(Res.id(hostId));
        host.setClipChildren(false);
        host.setClipToPadding(false);
        host.setOrientation(VERTICAL);
        box.addView(host, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topMargin);
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private int color(int resId) {
        return getContext().getColor(resId);
    }
}
