package com.arrow.systemui.cc.widget;

import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

public class VerticalLevelSlider implements View.OnTouchListener {
    public interface Listener { void onChanged(int value); }

    private final View box, fill;
    private final int max;
    private final Listener listener;
    private final ImageView icon;
    private final int onTrack, onFill;
    private int progress;
    private boolean tracking;

    public VerticalLevelSlider(View box, View fill, int max, Listener listener,
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
        enableClipToRounded(box);
        box.addOnLayoutChangeListener((v, a, b, c, d, oldA, oldB, oldC, oldD) -> update());
    }

    public void setProgress(int value) { setProgress(value, false); }

    public void setProgress(int value, boolean notify) {
        progress = Math.max(0, Math.min(max, value));
        update();
        if (notify) listener.onChanged(progress);
    }

    public int getProgress() { return progress; }

    @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true;
                view.getParent().requestDisallowInterceptTouchEvent(true);
                apply(event.getY(), view.getHeight());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking) {
                    apply(event.getY(), view.getHeight());
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    tracking = false;
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    apply(event.getY(), view.getHeight());
                    return true;
                }
        }
        return false;
    }

    private void apply(float y, int height) {
        if (height <= 0) return;
        int next = Math.round((1 - y / height) * max);
        next = Math.max(0, Math.min(max, next));
        if (next != progress) {
            progress = next;
            update();
            listener.onChanged(progress);
        } else update();
    }

    private void update() {
        applyStaticProgress(box, fill, max, progress, icon, onTrack, onFill);
    }

    public static void applyStaticProgress(View box, View fill, int max, int progress,
                                           ImageView icon, int trackColor, int fillColor) {
        enableClipToRounded(box);
        Runnable apply = () -> {
            int height = box.getHeight();
            if (height <= 0) return;
            int safeMax = Math.max(1, max);
            int safeProgress = Math.max(0, Math.min(max, progress));
            int fillHeight = Math.round(height * ((float) safeProgress / safeMax));
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            if (lp.height != fillHeight) {
                lp.height = fillHeight;
                fill.setLayoutParams(lp);
            }
            if (icon != null) {


                int requiredFillHeight;
                if (icon.getBottom() > 0) {
                    requiredFillHeight = height - icon.getTop();
                } else {
                    int bottomMargin = icon.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                            ? ((ViewGroup.MarginLayoutParams) icon.getLayoutParams()).bottomMargin : 0;
                    int iconHeight = icon.getLayoutParams().height > 0
                            ? icon.getLayoutParams().height : icon.getHeight();
                    requiredFillHeight = Math.max(0, bottomMargin) + Math.max(0, iconHeight);
                }
                boolean fillPassedIcon = fillHeight > requiredFillHeight;
                icon.setImageTintList(ColorStateList.valueOf(
                        fillPassedIcon ? fillColor : trackColor));
            }
        };
        if (box.getHeight() > 0) apply.run(); else box.post(apply);
    }

    public static void enableClipToRounded(View view) { enableClip(view, true, 18); }
    public static void enableClipRoundedRect(View view, float corner) { enableClip(view, false, corner); }

    public static void enableClip(View view, boolean circle, float dp) {
        if (view instanceof ViewGroup) {
            ((ViewGroup) view).setClipChildren(true);
            ((ViewGroup) view).setClipToPadding(true);
        }
        view.setClipToOutline(true);
        float corner = dp * view.getResources().getDisplayMetrics().density;
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                if (v.getWidth() <= 0 || v.getHeight() <= 0) {
                    outline.setEmpty();
                    return;
                }
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(),
                        circle ? Math.min(v.getWidth(), v.getHeight()) / 2f : corner);
            }
        });
        view.invalidateOutline();
    }
}
