package com.arrow.systemui.cc;

import android.content.Context;


public final class Res {
    private static Context appContext;
    private Res() {}
    public static void init(Context context) { appContext = context.getApplicationContext(); }
    private static int get(String type, String name) {
        if (appContext == null) throw new IllegalStateException("Res.init(context) must run before resource lookup");
        return appContext.getResources().getIdentifier(name, type, appContext.getPackageName());
    }
    public static int id(String n) { return get("id", n); }
    public static int layout(String n) { return get("layout", n); }
    public static int drawable(String n) { return get("drawable", n); }
    public static int string(String n) { return get("string", n); }
    public static int color(String n) { return get("color", n); }
    public static int dimen(String n) { return get("dimen", n); }
}
