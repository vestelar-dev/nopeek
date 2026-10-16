package com.vestelar.nopeek;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Watches which app is in the foreground and writes Samsung's
 * Settings.System "privacy_display_level" (0 = normal, 1 = maximum) for it.
 * Whether Privacy display is on at all is still decided by One UI.
 */
public class LevelService extends AccessibilityService {
    private static final String TAG = "NoPeek";

    private final Map<String, Boolean> activityCache = new HashMap<>();
    private SharedPreferences prefs;

    @Override protected void onServiceConnected() {
        prefs = Prefs.get(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgSeq = event.getPackageName();
        CharSequence clsSeq = event.getClassName();
        if (pkgSeq == null || clsSeq == null) return;
        String pkg = pkgSeq.toString();
        if (isIgnored(pkg) || !isActivity(pkg, clsSeq.toString())) return;

        if (!prefs.getBoolean(Prefs.KEY_ENABLED, true)) return;
        Set<String> maxApps = prefs.getStringSet(Prefs.KEY_MAX_APPS, Prefs.EMPTY);
        apply(maxApps.contains(pkg) ? Prefs.LEVEL_MAX : Prefs.LEVEL_NORMAL);
    }

    private void apply(int level) {
        try {
            int current = Settings.System.getInt(getContentResolver(), Prefs.SETTING_LEVEL, -1);
            if (current != level) {
                Settings.System.putInt(getContentResolver(), Prefs.SETTING_LEVEL, level);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Cannot write " + Prefs.SETTING_LEVEL, t);
        }
    }

    /** Popups, toasts and dialogs also fire window events; only real activities count. */
    private boolean isActivity(String pkg, String cls) {
        String key = pkg + "/" + cls;
        Boolean cached = activityCache.get(key);
        if (cached != null) return cached;
        boolean result;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            result = true;
        } catch (PackageManager.NameNotFoundException e) {
            result = false;
        }
        activityCache.put(key, result);
        return result;
    }

    private boolean isIgnored(String pkg) {
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return true;
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(pkg + "/");
    }

    @Override public void onInterrupt() { }
}
