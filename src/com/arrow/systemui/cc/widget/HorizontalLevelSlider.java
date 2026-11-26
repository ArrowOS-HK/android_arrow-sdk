package com.arrow.systemui.cc.widget;

import android.content.res.ColorStateList;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class HorizontalLevelSlider implements View.OnTouchListener {
    public interface Listener { void onChanged(int value); }

    private final View box, fill;
    private final int max;
    private final Listener listener;
    private final ImageView icon;
    private final int onTrack, onFill;
    private int progress;

    public HorizontalLevelSlider(View box, View fill, int max, Listener listener,
                                 ImageView icon, int trackColor, int fillColor) {
        this.box = box;
        this.fill = fill;
        this.max = max;
        this.listener = listener;
        this.icon = icon;
        this.onTrack = trackColor;
        this.onFill = fillColor;
        box.setOnTouchListener(this);
        box.setClickable(true);
        VerticalLevelSlider.enableClipToRounded(box);
        box.addOnLayoutChangeListener((v, a, b, c, d, oldA, oldB, oldC, oldD) -> update());
    }

    public void setProgress(int value) {
        progress = Math.max(0, Math.min(max, value));
        update();
    }

    public int getProgress() { return progress; }

    @Override public boolean onTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE
                || event.getActionMasked() == MotionEvent.ACTION_UP) {
            int next = Math.round(event.getX() / Math.max(1, view.getWidth()) * max);
            next = Math.max(0, Math.min(max, next));
            if (next != progress) {
                progress = next;
                update();
                listener.onChanged(next);
            }
            return true;
        }
        return true;
    }

    private void update() {
        applyStaticProgress(box, fill, max, progress, icon, onTrack, onFill);
    }

    public static void applyStaticProgress(View box, View fill, int max, int progress,
                                           ImageView icon, int trackColor, int fillColor) {
        VerticalLevelSlider.enableClipToRounded(box);
        Runnable apply = () -> {
            int width = box.getWidth();
            if (width <= 0) return;
            int safeMax = Math.max(1, max);
            int safeProgress = Math.max(0, Math.min(max, progress));
            int fillWidth = Math.round(width * ((float) safeProgress / safeMax));
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            if (lp.width != fillWidth) {
                lp.width = fillWidth;
                fill.setLayoutParams(lp);
            }
            if (icon != null) {


                int iconFarEdge = icon.getRight();
                if (iconFarEdge <= 0) {
                    int endMargin = 0;
                    if (icon.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        endMargin = ((ViewGroup.MarginLayoutParams) icon.getLayoutParams())
                                .getMarginEnd();
                    }
                    iconFarEdge = width - Math.max(0, endMargin);
                }
                boolean fillPassedIcon = fillWidth > iconFarEdge;
                icon.setImageTintList(ColorStateList.valueOf(
                        fillPassedIcon ? fillColor : trackColor));
            }
        };
        if (box.getWidth() > 0) apply.run(); else box.post(apply);
    }
}
