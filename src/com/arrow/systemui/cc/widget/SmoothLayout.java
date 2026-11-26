package com.arrow.systemui.cc.widget;

import android.graphics.Rect;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import java.util.HashMap;
import java.util.Map;


final class SmoothLayout {
    private static final long DURATION_MS = 220L;
    private static final PathInterpolator EASE_OUT = new PathInterpolator(.16f, 1f, .3f, 1f);

    private SmoothLayout() { }

    static boolean beginSmoothBounds(ViewGroup root) {
        if (root == null) return false;
        try {
            TransitionManager.endTransitions(root);
            ChangeBounds change = new ChangeBounds();
            change.setDuration(260L);
            change.setInterpolator(EASE_OUT);
            change.setResizeClip(false);
            change.setReparent(false);
            TransitionManager.beginDelayedTransition(root, change);
            return true;
        } catch (Throwable ignored) {


            return false;
        }
    }

    static Map<View, Rect> capture(ViewGroup... parents) {
        HashMap<View, Rect> out = new HashMap<>();
        if (parents == null) return out;
        int[] loc = new int[2];
        for (ViewGroup parent : parents) {
            if (parent == null) continue;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                child.getLocationOnScreen(loc);
                out.put(child, new Rect(loc[0], loc[1], loc[0] + Math.max(1, child.getWidth()), loc[1] + Math.max(1, child.getHeight())));
            }
        }
        return out;
    }

    static void play(View root, Map<View, Rect> before) {
        if (root == null || before == null || before.isEmpty()) return;
        root.post(() -> playNow(before));
    }

    private static void playNow(Map<View, Rect> before) {
        int[] loc = new int[2];
        for (Map.Entry<View, Rect> entry : before.entrySet()) {
            View child = entry.getKey();
            Rect old = entry.getValue();
            if (child == null || old == null || child.getParent() == null || child.getVisibility() == View.GONE) continue;
            child.getLocationOnScreen(loc);
            int newW = Math.max(1, child.getWidth());
            int newH = Math.max(1, child.getHeight());
            float dx = old.left - loc[0];
            float dy = old.top - loc[1];
            boolean moved = Math.abs(dx) >= 1f || Math.abs(dy) >= 1f;
            boolean resized = Math.abs(old.width() - newW) >= 2 || Math.abs(old.height() - newH) >= 2;
            if (!moved && !resized) continue;

            child.animate().cancel();
            child.setScaleX(1f);
            child.setScaleY(1f);


            if (moved) {
                child.setTranslationX(dx);
                child.setTranslationY(dy);
                child.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(DURATION_MS)
                        .setInterpolator(EASE_OUT)
                        .start();
            }
        }
    }

    static void appear(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(.94f);
        v.setScaleY(.94f);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(EASE_OUT)
                .start();
    }

    static void pulse(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(.78f);
        v.animate()
                .alpha(1f)
                .setDuration(140L)
                .setInterpolator(EASE_OUT)
                .start();
    }

    static void softRefresh(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setAlpha(.88f);
        v.animate()
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(EASE_OUT)
                .start();
    }
}
