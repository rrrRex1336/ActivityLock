package io.kernelxtra.activitylock.system;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.kernelxtra.activitylock.data.SettingsSnapshot;
import io.kernelxtra.activitylock.util.Shell;

/**
 * Root-only navigation control.
 *
 * <p>On several ROMs (including HyperOS-derived builds) writing
 * {@code navigation_mode} alone is ignored because SystemUI keeps the
 * navigation overlay unchanged. To make the change actually take effect we
 * force the AOSP navigation overlay to the three-button variant through
 * OverlayManager, then hide the bars with immersive policy control.</p>
 */
public final class SettingsController {

    public static final String NAVIGATION_MODE = "navigation_mode";
    public static final String POLICY_CONTROL = "policy_control";
    public static final String IMMERSIVE_FULL = "immersive.full=*";
    public static final String NAVBAR_THREE_BUTTON =
            "com.android.internal.systemui.navbar.threebutton";
    public static final String NAVBAR_GESTURAL =
            "com.android.internal.systemui.navbar.gestural";

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

    @NonNull
    public static SettingsSnapshot capture(Context context) {
        String nav = readSecure(context, NAVIGATION_MODE);
        String policy = readGlobal(context, POLICY_CONTROL);
        String overlay = getEnabledNavbarOverlay();
        return new SettingsSnapshot(nav != null, nav, policy != null, policy, overlay);
    }

    /**
     * Applies the aggressive lock. All writes require root because the
     * navigation overlay is a system package.
     */
    @NonNull
    public static ApplyResult apply(Context context, boolean blockGestures, boolean hideBars) {
        if (!blockGestures && !hideBars) {
            return new ApplyResult(true, true, true, null);
        }
        if (!Shell.isRootAvailable()) {
            return new ApplyResult(false, !blockGestures, !hideBars, "root required");
        }

        boolean writesOk = true;
        String error = null;

        if (blockGestures) {
            Shell.Result overlay = Shell.su("cmd overlay enable-exclusive --category "
                    + NAVBAR_THREE_BUTTON);
            Shell.Result mode = Shell.su("settings put secure " + NAVIGATION_MODE + " 0");
            writesOk = overlay.ok && mode.ok;
            if (!writesOk) {
                error = "navigation overlay";
            }
        }

        if (hideBars) {
            Shell.Result policy = Shell.su("settings put global " + POLICY_CONTROL + " "
                    + shellQuote(IMMERSIVE_FULL));
            writesOk = writesOk && policy.ok;
            if (!policy.ok) {
                error = error == null ? "immersive policy" : error + ", immersive policy";
            }
        }

        boolean gesturesVerified = !blockGestures
                || ("0".equals(readSecure(context, NAVIGATION_MODE))
                && isNavbarOverlayEnabled(NAVBAR_THREE_BUTTON));
        String policy = readGlobal(context, POLICY_CONTROL);
        boolean barsVerified = !hideBars
                || (policy != null && policy.contains("immersive.full"));

        boolean success = writesOk && gesturesVerified && barsVerified;
        if (!gesturesVerified) {
            error = error == null ? "gesture verification" : error + ", gesture verification";
        }
        if (!barsVerified) {
            error = error == null ? "immersive verification" : error + ", immersive verification";
        }
        return new ApplyResult(success, gesturesVerified, barsVerified, error);
    }

    /**
     * Restores the exact overlay, navigation mode and immersive policy from before the session.
     */
    public static boolean restore(Context context, @NonNull SettingsSnapshot snapshot) {
        if (!Shell.isRootAvailable()) {
            return false;
        }

        boolean success = true;
        String originalOverlay = snapshot.navigationOverlay;
        if (originalOverlay == null || originalOverlay.isEmpty()) {
            originalOverlay = "2".equals(snapshot.navigationMode)
                    ? NAVBAR_GESTURAL : NAVBAR_THREE_BUTTON;
        }
        success = Shell.su("cmd overlay enable-exclusive --category " + originalOverlay).ok
                && success;

        if (snapshot.navigationModePresent) {
            success = Shell.su("settings put secure " + NAVIGATION_MODE + " "
                    + shellQuote(snapshot.navigationMode)).ok && success;
        } else {
            success = Shell.su("settings delete secure " + NAVIGATION_MODE).ok && success;
        }

        if (snapshot.policyControlPresent) {
            success = Shell.su("settings put global " + POLICY_CONTROL + " "
                    + shellQuote(snapshot.policyControl)).ok && success;
        } else {
            success = Shell.su("settings delete global " + POLICY_CONTROL).ok && success;
        }
        return success;
    }

    @Nullable
    public static String getEnabledNavbarOverlay() {
        Shell.Result result = Shell.su("cmd overlay list --user 0");
        if (!result.ok) {
            return null;
        }
        for (String line : result.output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("[x]") || !trimmed.contains("navbar")) {
                continue;
            }
            String pkg = trimmed.substring(3).trim();
            int nameSeparator = pkg.indexOf(':');
            if (nameSeparator > 0) {
                pkg = pkg.substring(0, nameSeparator);
            }
            if (!pkg.isEmpty()) {
                return pkg;
            }
        }
        return null;
    }

    public static boolean isNavbarOverlayEnabled(@NonNull String overlayPackage) {
        Shell.Result result = Shell.su("cmd overlay list --user 0");
        if (!result.ok) {
            return false;
        }
        for (String line : result.output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[x]")) {
                String pkg = trimmed.substring(3).trim();
                int nameSeparator = pkg.indexOf(':');
                if (nameSeparator > 0) {
                    pkg = pkg.substring(0, nameSeparator);
                }
                if (overlayPackage.equals(pkg)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    public static String readSecure(Context context, String key) {
        try {
            return Settings.Secure.getString(context.getContentResolver(), key);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static String readGlobal(Context context, String key) {
        try {
            return Settings.Global.getString(context.getContentResolver(), key);
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private static String shellQuote(@NonNull String value) {
        if (value.matches("[A-Za-z0-9_.*=:-]+")) {
            return value;
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
