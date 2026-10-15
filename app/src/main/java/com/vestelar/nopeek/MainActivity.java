package com.vestelar.nopeek;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final class AppEntry {
        final String pkg;
        final String label;
        final ApplicationInfo info;

        AppEntry(String pkg, String label, ApplicationInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    private SharedPreferences prefs;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private AppAdapter adapter;
    private TextView levelValue;
    private TextView appsCount;
    private TextView empty;
    private ListView list;
    private Button writeButton;
    private Button serviceButton;
    private String filter = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = Prefs.get(this);

        levelValue = findViewById(R.id.levelValue);
        appsCount = findViewById(R.id.appsCount);
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.list);

        writeButton = findViewById(R.id.writeButton);
        writeButton.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
        serviceButton = findViewById(R.id.serviceButton);
        serviceButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Switch enabledSwitch = findViewById(R.id.enabled);
        enabledSwitch.setChecked(prefs.getBoolean(Prefs.KEY_ENABLED, true));
        enabledSwitch.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply());

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                refreshList();
            }
        });

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        // Toggling doesn't re-sort, so the row stays under the finger.
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = shownApps.get(position);
            setMax(entry.pkg, !isMax(entry.pkg));
            adapter.notifyDataSetChanged();
            updateCount();
        });

        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        styleGrantButton(writeButton, Settings.System.canWrite(this));
        styleGrantButton(serviceButton, isServiceEnabled());
        int level = Settings.System.getInt(getContentResolver(), Prefs.SETTING_LEVEL, -1);
        levelValue.setText(level == Prefs.LEVEL_MAX ? R.string.level_max
                : level == Prefs.LEVEL_NORMAL ? R.string.level_normal : R.string.level_unknown);
    }

    private void styleGrantButton(Button button, boolean granted) {
        button.setText(granted ? R.string.granted : R.string.grant);
        button.setBackgroundResource(granted ? R.drawable.btn_done : R.drawable.btn_primary);
        button.setTextColor(getColor(granted ? R.color.gold : R.color.on_gold));
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, LevelService.class);
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);
            Map<String, AppEntry> unique = new HashMap<>();
            for (ResolveInfo r : resolved) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
                ApplicationInfo ai = r.activityInfo.applicationInfo;
                unique.put(pkg, new AppEntry(pkg, String.valueOf(ai.loadLabel(pm)), ai));
            }
            List<AppEntry> apps = new ArrayList<>(unique.values());
            Collator collator = Collator.getInstance();
            apps.sort((a, b) -> collator.compare(a.label, b.label));
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(apps);
                refreshList();
            });
        }).start();
    }

    /** Apps already set to maximum go first so they are easy to review. */
    private void refreshList() {
        Set<String> max = maxApps();
        List<AppEntry> first = new ArrayList<>();
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry e : allApps) {
            if (!filter.isEmpty() && !e.label.toLowerCase(Locale.ROOT).contains(filter)
                    && !e.pkg.contains(filter)) continue;
            (max.contains(e.pkg) ? first : rest).add(e);
        }
        shownApps.clear();
        shownApps.addAll(first);
        shownApps.addAll(rest);
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private void updateCount() {
        int count = 0;
        Set<String> max = maxApps();
        for (AppEntry e : allApps) if (max.contains(e.pkg)) count++;
        appsCount.setText(getString(R.string.apps_count, count, allApps.size()));
    }

    private Set<String> maxApps() {
        return prefs.getStringSet(Prefs.KEY_MAX_APPS, Prefs.EMPTY);
    }

    private boolean isMax(String pkg) {
        return maxApps().contains(pkg);
    }

    private void setMax(String pkg, boolean max) {
        Set<String> set = new HashSet<>(maxApps());
        if (max) set.add(pkg); else set.remove(pkg);
        prefs.edit().putStringSet(Prefs.KEY_MAX_APPS, set).apply();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int position) { return shownApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : getLayoutInflater().inflate(R.layout.row_app, parent, false);
            AppEntry entry = shownApps.get(position);
            boolean max = isMax(entry.pkg);

            Drawable d = icons.get(entry.pkg);
            if (d == null) {
                d = entry.info.loadIcon(getPackageManager());
                icons.put(entry.pkg, d);
            }
            ((ImageView) row.findViewById(R.id.icon)).setImageDrawable(d);
            TextView label = row.findViewById(R.id.label);
            label.setText(entry.label);
            label.setTextColor(getColor(max ? R.color.gold : R.color.text));
            ((TextView) row.findViewById(R.id.pkg)).setText(entry.pkg);
            ((Switch) row.findViewById(R.id.toggle)).setChecked(max);
            return row;
        }
    }
}
