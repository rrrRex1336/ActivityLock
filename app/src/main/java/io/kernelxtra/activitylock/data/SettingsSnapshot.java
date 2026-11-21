package io.kernelxtra.activitylock.data;

import androidx.annotation.Nullable;

public final class SettingsSnapshot {

    public final boolean navigationModePresent;
    @Nullable
    public final String navigationMode;
    public final boolean policyControlPresent;
    @Nullable
    public final String policyControl;
    @Nullable
    public final String navigationOverlay;

    public SettingsSnapshot(boolean navigationModePresent,
                            @Nullable String navigationMode,
                            boolean policyControlPresent,
                            @Nullable String policyControl,
                            @Nullable String navigationOverlay) {
        this.navigationModePresent = navigationModePresent;
        this.navigationMode = navigationMode;
        this.policyControlPresent = policyControlPresent;
        this.policyControl = policyControl;
        this.navigationOverlay = navigationOverlay;
    }
}
