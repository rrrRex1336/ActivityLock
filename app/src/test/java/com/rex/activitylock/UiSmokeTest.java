package com.rex.activitylock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
public class UiSmokeTest {

    @Test
    public void mainScreenInflates() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        assertNotNull(activity.findViewById(R.id.btn_start));
        assertNotNull(activity.findViewById(R.id.mode_group));
        assertNotNull(activity.findViewById(R.id.target_card));
        assertTrue(activity.findViewById(R.id.btn_start).isEnabled());
        controller.pause().stop().destroy();
    }

    @Test
    public void overlayExitInflates() {
        Context base = ApplicationProvider.getApplicationContext();
        Context themed = new ContextThemeWrapper(base, R.style.Theme_ActivityLock);
        View view = LayoutInflater.from(themed).inflate(R.layout.overlay_exit, null);
        assertNotNull(view.findViewById(R.id.btn_exit_confirm));
        assertNotNull(view.findViewById(R.id.btn_exit_keep));
    }

    @Test
    public void pickerSheetInflates() {
        Context base = ApplicationProvider.getApplicationContext();
        Context themed = new ContextThemeWrapper(base, R.style.Theme_ActivityLock);
        View view = LayoutInflater.from(themed).inflate(R.layout.sheet_app_picker, null);
        assertNotNull(view.findViewById(R.id.app_list));
        assertNotNull(view.findViewById(R.id.search_input));
    }
}
