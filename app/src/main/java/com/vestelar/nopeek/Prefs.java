package com.vestelar.nopeek;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Set;

final class Prefs {
    static final String SETTING_LEVEL = "privacy_display_level";
    static final int LEVEL_NORMAL = 0;
    static final int LEVEL_MAX = 1;

    static final String KEY_ENABLED = "enabled";
    static final String KEY_MAX_APPS = "max_apps";
    static final Set<String> EMPTY = Collections.emptySet();

    private Prefs() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("nopeek", Context.MODE_PRIVATE);
    }
}
