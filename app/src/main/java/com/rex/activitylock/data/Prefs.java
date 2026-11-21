package com.rex.activitylock.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Prefs {

    public static final String MODE_ROOT = "root";
    public static final String MODE_ADB = "adb";

    private static final String FILE = "activitylock_settings";
    private static final String KEY_TARGET = "target_package";
    private static final String KEY_MODE = "setup_mode";
    private static final String KEY_BLOCK_GESTURES = "block_gestures";
    private static final String KEY_HIDE_BARS = "hide_bars";
    private static final String KEY_KEEP_AWAKE = "keep_awake";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Nullable
    public static String getTarget(Context context) {
        return prefs(context).getString(KEY_TARGET, null);
    }

    public static void setTarget(Context context, @Nullable String packageName) {
        prefs(context).edit().putString(KEY_TARGET, packageName).apply();
    }

    @NonNull
    public static String getMode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_ADB);
    }

    public static void setMode(Context context, @NonNull String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
    }

    public static boolean blockGestures(Context context) {
        return prefs(context).getBoolean(KEY_BLOCK_GESTURES, true);
    }

    public static void setBlockGestures(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BLOCK_GESTURES, enabled).apply();
    }

    public static boolean hideBars(Context context) {
        return prefs(context).getBoolean(KEY_HIDE_BARS, true);
    }

    public static void setHideBars(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HIDE_BARS, enabled).apply();
    }

    public static boolean keepAwake(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_AWAKE, true);
    }

    public static void setKeepAwake(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply();
    }
}
