package com.rex.activitylock.data;

import androidx.annotation.Nullable;

public final class SettingsSnapshot {

    public final boolean navigationModePresent;
    @Nullable
    public final String navigationMode;
    public final boolean policyControlPresent;
    @Nullable
    public final String policyControl;

    public SettingsSnapshot(boolean navigationModePresent,
                            @Nullable String navigationMode,
                            boolean policyControlPresent,
                            @Nullable String policyControl) {
        this.navigationModePresent = navigationModePresent;
        this.navigationMode = navigationMode;
        this.policyControlPresent = policyControlPresent;
        this.policyControl = policyControl;
    }
}
