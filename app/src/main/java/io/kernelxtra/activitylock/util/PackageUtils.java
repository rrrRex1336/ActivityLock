package io.kernelxtra.activitylock.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import io.kernelxtra.activitylock.data.AppEntry;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PackageUtils {

    private PackageUtils() {
    }

    public static List<AppEntry> loadLaunchableApps(Context context) {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        Map<String, AppEntry> byPackage = new HashMap<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(app.getPackageName()) || byPackage.containsKey(pkg)) {
                continue;
            }
            try {
                CharSequence loadedLabel = info.loadLabel(pm);
                String label = loadedLabel == null ? pkg : loadedLabel.toString().trim();
                if (label.isEmpty()) {
                    label = pkg;
                }
                Drawable icon = info.loadIcon(pm);
                byPackage.put(pkg, new AppEntry(pkg, label, icon));
            } catch (Exception ignored) {
                // A broken package should not hide the rest of the list.
            }
        }

        List<AppEntry> result = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance(Locale.CHINA);
        result.sort((left, right) -> collator.compare(left.label, right.label));
        return result;
    }
}
