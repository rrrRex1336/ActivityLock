package com.rex.activitylock.system;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rex.activitylock.data.Prefs;
import com.rex.activitylock.data.SettingsSnapshot;
import com.rex.activitylock.util.Shell;

/**
 * Temporarily switches the device to three-button navigation and enables immersive mode.
 * The original values are stored by {@link com.rex.activitylock.data.SessionStore}.
 */
public final class SettingsController {

    public static final String NAVIGATION_MODE = "navigation_mode";
    public static final String POLICY_CONTROL = "policy_control";
    public static final String IMMERSIVE_FULL = "immersive.full=*";

    private SettingsController() {
    }

    public static final class ApplyResult {
        public final boolean success;
        public final boolean gesturesVerified;
        public final boolean barsVerified;
        @Nullable
        public final String error;

        ApplyResult(boolean success, boolean gesturesVerified, boolean barsVerified,
                    @Nullable String error) {
            this.success = success;
            this.gesturesVerified = gesturesVerified;
            this.barsVerified = barsVerified;
            this.error = error;
        }
    }

    public static boolean hasWritePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    public static SettingsSnapshot capture(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String nav = Settings.Secure.getString(resolver, NAVIGATION_MODE);
        String policy = Settings.Global.getString(resolver, POLICY_CONTROL);
        return new SettingsSnapshot(nav != null, nav, policy != null, policy);
    }

    @NonNull
    public static ApplyResult apply(Context context, boolean blockGestures, boolean hideBars) {
        if (!blockGestures && !hideBars) {
            return new ApplyResult(true, true, true, null);
        }

        boolean direct = hasWritePermission(context);
        boolean root = !direct
                && Prefs.MODE_ROOT.equals(Prefs.getMode(context))
                && Shell.isRootAvailable();
        if (!direct && !root) {
            return new ApplyResult(false, !blockGestures, !hideBars,
                    "missing WRITE_SECURE_SETTINGS");
        }

        boolean navWrite = true;
        boolean policyWrite = true;
        String error = null;

        if (blockGestures) {
            navWrite = writeSecure(context, NAVIGATION_MODE, "0", direct);
            if (!navWrite) {
                error = "navigation_mode";
            }
        }
        if (hideBars) {
            policyWrite = writeGlobal(context, POLICY_CONTROL, IMMERSIVE_FULL, direct);
            if (!policyWrite) {
                error = error == null ? "policy_control" : error + ", policy_control";
            }
        }

        boolean navVerified = !blockGestures
                || "0".equals(readSecure(context, NAVIGATION_MODE));
        String policy = readGlobal(context, POLICY_CONTROL);
        boolean barsVerified = !hideBars
                || (policy != null && policy.contains("immersive.full"));

        return new ApplyResult(navWrite && policyWrite, navVerified, barsVerified, error);
    }

    /**
     * Restores the exact values captured before the session. Safe to call more than once.
     */
    public static boolean restore(Context context, @NonNull SettingsSnapshot snapshot) {
        boolean direct = hasWritePermission(context);
        boolean root = !direct
                && Prefs.MODE_ROOT.equals(Prefs.getMode(context))
                && Shell.isRootAvailable();
        if (!direct && !root) {
            return false;
        }

        boolean success = true;
        if (snapshot.navigationModePresent) {
            success = writeSecure(context, NAVIGATION_MODE, snapshot.navigationMode, direct);
        } else {
            success = deleteSecure(context, NAVIGATION_MODE, direct);
        }

        if (snapshot.policyControlPresent) {
            success = writeGlobal(context, POLICY_CONTROL, snapshot.policyControl, direct) && success;
        } else {
            success = deleteGlobal(context, POLICY_CONTROL, direct) && success;
        }
        return success;
    }

    @Nullable
    public static String readSecure(Context context, String key) {
        return Settings.Secure.getString(context.getContentResolver(), key);
    }

    @Nullable
    public static String readGlobal(Context context, String key) {
        return Settings.Global.getString(context.getContentResolver(), key);
    }

    private static boolean writeSecure(Context context, String key, @Nullable String value,
                                       boolean direct) {
        if (direct) {
            try {
                return Settings.Secure.putString(context.getContentResolver(), key, value);
            } catch (Exception ignored) {
                return false;
            }
        }
        return Shell.su(value == null
                ? "settings delete secure " + key
                : "settings put secure " + key + " " + shellQuote(value)).ok;
    }

    private static boolean writeGlobal(Context context, String key, @Nullable String value,
                                       boolean direct) {
        if (direct) {
            try {
                return Settings.Global.putString(context.getContentResolver(), key, value);
            } catch (Exception ignored) {
                return false;
            }
        }
        return Shell.su(value == null
                ? "settings delete global " + key
                : "settings put global " + key + " " + shellQuote(value)).ok;
    }

    private static boolean deleteSecure(Context context, String key, boolean direct) {
        if (direct) {
            try {
                context.getContentResolver().delete(Settings.Secure.getUriFor(key), null, null);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return Shell.su("settings delete secure " + key).ok;
    }

    private static boolean deleteGlobal(Context context, String key, boolean direct) {
        if (direct) {
            try {
                context.getContentResolver().delete(Settings.Global.getUriFor(key), null, null);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return Shell.su("settings delete global " + key).ok;
    }

    @NonNull
    private static String shellQuote(@NonNull String value) {
        if (value.matches("[A-Za-z0-9_.*=:-]+")) {
            return value;
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
