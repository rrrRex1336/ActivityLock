package io.kernelxtra.activitylock.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Crash-safe record of the system settings we temporarily changed.
 */
public final class SessionStore {

    private static final String FILE = "activitylock_session";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_TARGET = "target";
    private static final String KEY_TARGET_NAME = "target_name";
    private static final String KEY_NAV_PRESENT = "nav_present";
    private static final String KEY_NAV_VALUE = "nav_value";
    private static final String KEY_POLICY_PRESENT = "policy_present";
    private static final String KEY_POLICY_VALUE = "policy_value";
    private static final String KEY_NAV_OVERLAY = "nav_overlay";

    private SessionStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @SuppressLint("ApplySharedPref")
    public static void begin(Context context,
                             @NonNull String target,
                             @NonNull String targetName,
                             @NonNull SettingsSnapshot snapshot) {
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_TARGET, target)
                .putString(KEY_TARGET_NAME, targetName)
                .putBoolean(KEY_NAV_PRESENT, snapshot.navigationModePresent)
                .putString(KEY_NAV_VALUE, snapshot.navigationMode)
                .putBoolean(KEY_POLICY_PRESENT, snapshot.policyControlPresent)
                .putString(KEY_POLICY_VALUE, snapshot.policyControl)
                .putString(KEY_NAV_OVERLAY, snapshot.navigationOverlay)
                .commit();
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    @Nullable
    public static String getTarget(Context context) {
        return prefs(context).getString(KEY_TARGET, null);
    }

    @NonNull
    public static String getTargetName(Context context) {
        String value = prefs(context).getString(KEY_TARGET_NAME, "");
        return value == null ? "" : value;
    }

    @NonNull
    public static SettingsSnapshot getSnapshot(Context context) {
        SharedPreferences p = prefs(context);
        return new SettingsSnapshot(
                p.getBoolean(KEY_NAV_PRESENT, false),
                p.getString(KEY_NAV_VALUE, null),
                p.getBoolean(KEY_POLICY_PRESENT, false),
                p.getString(KEY_POLICY_VALUE, null),
                p.getString(KEY_NAV_OVERLAY, null));
    }

    public static void updateTarget(Context context, @NonNull String target,
                                    @NonNull String targetName) {
        prefs(context).edit()
                .putString(KEY_TARGET, target)
                .putString(KEY_TARGET_NAME, targetName)
                .apply();
    }

    public static void end(Context context) {
        prefs(context).edit().clear().apply();
    }
}
