package com.vestelar.privacyprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {
    private static final String[] TABLES = {"system", "secure", "global"};
    private static final String[] KEYWORDS = {
            "privacy", "magicpixel", "magic_pixel", "flexpixel", "flex_pixel",
            "viewingangle", "viewing_angle", "private_display", "privatedisplay"
    };
    private static final byte[][] KEYWORD_BYTES = new byte[KEYWORDS.length][];
    static {
        for (int i = 0; i < KEYWORDS.length; i++) {
            KEYWORD_BYTES[i] = KEYWORDS[i].getBytes(StandardCharsets.US_ASCII);
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder report = new StringBuilder();
    private final Map<String, Set<String>> dexHits = new TreeMap<>();
    private final Set<String> candidateKeys = new TreeSet<>();
    private Map<String, String> snapshot;
    private TextView output;
    private ScrollView scroll;
    private ContentObserver observer;
    private Map<String, String> watchState;
    private Button watchButton;
    private volatile boolean busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(8 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 4, pad, pad);

        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(button("1. Escanear", v -> runScan()));
        watchButton = button("2. Vigilar", v -> toggleWatch());
        buttons.addView(watchButton);
        buttons.addView(button("3. Probar escritura", v -> testWrite()));
        buttons.addView(button("4. Volcar API", v -> dumpPrivacyApi()));
        buttons.addView(button("Foto", v -> takeSnapshot()));
        buttons.addView(button("Comparar", v -> compareSnapshot()));
        buttons.addView(button("Guardar", v -> saveReport()));
        buttons.addView(button("Copiar", v -> copyReport()));
        buttons.addView(button("Limpiar", v -> { report.setLength(0); output.setText(""); }));
        HorizontalScrollView buttonScroll = new HorizontalScrollView(this);
        buttonScroll.addView(buttons);
        root.addView(buttonScroll);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(11);
        output.setTextIsSelectable(true);
        scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        log("S26 Privacy Probe v0.2\n"
                + "1. Escanear: busca claves/métodos de Privacy Display (1-3 min).\n"
                + "2. Vigilar: activa y luego cambia Privacy Display / Máxima desde el panel rápido;\n"
                + "   aquí aparecerá cada ajuste que cambie.\n"
                + "Foto + Comparar: alternativa manual a Vigilar.\n"
                + "Guardar: escribe el informe en Descargas.\n");
        exemptHiddenApi();
    }

    @Override protected void onDestroy() {
        stopWatch();
        super.onDestroy();
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    // ---------------------------------------------------------------- logging

    private void log(String line) {
        synchronized (report) {
            report.append(line).append('\n');
        }
        main.post(() -> {
            output.append(line + "\n");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    /** Goes to the report only; keeps the on-screen view short. */
    private void record(String line) {
        synchronized (report) {
            report.append(line).append('\n');
        }
    }

    private void status(String text) {
        main.post(() -> setTitle(text));
    }

    // ---------------------------------------------------------------- scan

    private void runScan() {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            try {
                scan();
            } catch (Throwable t) {
                log("ERROR scan: " + t);
            } finally {
                busy = false;
                log("\n=== Escaneo terminado. Pulsa Guardar para el informe completo. ===");
            }
        }).start();
    }

    private void scan() {
        dexHits.clear();
        candidateKeys.clear();
        log("\n=== DISPOSITIVO ===");
        log(Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
        log("Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT
                + " build " + Build.DISPLAY);

        log("\n=== REFLEXIÓN: clases conocidas ===");
        Class<?>[] known = {
                View.class, Window.class, WindowManager.LayoutParams.class,
                WindowManager.class, Settings.System.class, Settings.Secure.class,
                Settings.Global.class, android.hardware.display.DisplayManager.class,
                android.view.Display.class, android.view.SurfaceControl.class,
                android.os.PowerManager.class
        };
        for (Class<?> c : known) dumpClass(c, true);

        log("\n=== ESCANEO DEX (framework + apps Samsung) ===");
        scanFrameworkDirs();
        scanSystemApps();
        log("Cadenas encontradas: " + dexHits.size());

        log("\n=== REFLEXIÓN: clases con 'privacy' en el nombre ===");
        int classes = 0;
        for (String s : dexHits.keySet()) {
            if (!s.startsWith("L") || !s.endsWith(";") || s.contains("$$")) continue;
            String name = s.substring(1, s.length() - 1).replace('/', '.');
            try {
                Class<?> c = Class.forName(name, false, getClassLoader());
                dumpClass(c, false);
                classes++;
            } catch (Throwable ignored) {
                record("  (no cargable) " + name + "  <- " + sources(s));
            }
        }
        log("Clases del framework volcadas: " + classes);

        log("\n=== CADENAS candidatas a clave de ajustes ===");
        for (String s : dexHits.keySet()) {
            if (s.matches("[a-zA-Z0-9_.]{3,80}") && !s.contains("..")) candidateKeys.add(s);
        }
        log("Candidatas: " + candidateKeys.size());

        log("\n=== VALORES ACTUALES (claves candidatas) ===");
        for (String key : candidateKeys) {
            for (String table : TABLES) {
                String value = readSetting(table, key);
                if (value != null) log(table + "/" + key + " = " + value);
            }
        }

        log("\n=== TABLAS COMPLETAS (filas que coinciden) ===");
        for (String table : TABLES) {
            Map<String, String> rows = queryTable(table);
            if (rows == null) {
                log(table + ": consulta completa NO permitida");
                continue;
            }
            log(table + ": " + rows.size() + " filas legibles");
            for (Map.Entry<String, String> e : rows.entrySet()) {
                if (matchesKeyword(e.getKey()) || matchesKeyword(String.valueOf(e.getValue()))) {
                    log("  " + table + "/" + e.getKey() + " = " + e.getValue());
                }
            }
        }

        record("\n=== TODAS LAS CADENAS ENCONTRADAS (cadena  <- origen) ===");
        for (Map.Entry<String, Set<String>> e : dexHits.entrySet()) {
            record(e.getKey() + "  <- " + sources(e.getKey()));
        }
        log("(Lista completa de cadenas solo en el informe guardado)");
    }

    private String sources(String hit) {
        Set<String> set = dexHits.get(hit);
        if (set == null) return "";
        List<String> list = new ArrayList<>(set);
        if (list.size() > 4) return String.join(", ", list.subList(0, 4)) + " +" + (list.size() - 4);
        return String.join(", ", list);
    }

    private void dumpClass(Class<?> c, boolean filterMembers) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (filterMembers && !matchesKeyword(m.getName())) continue;
                sb.append("  M ").append(Modifier.toString(m.getModifiers())).append(' ')
                        .append(m.getReturnType().getSimpleName()).append(' ')
                        .append(m.getName()).append('(');
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getSimpleName());
                }
                sb.append(")\n");
            }
        } catch (Throwable t) {
            sb.append("  métodos: ").append(t).append('\n');
        }
        try {
            for (Field f : c.getDeclaredFields()) {
                if (filterMembers && !matchesKeyword(f.getName())) continue;
                sb.append("  F ").append(Modifier.toString(f.getModifiers())).append(' ')
                        .append(f.getType().getSimpleName()).append(' ').append(f.getName());
                if (Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        sb.append(" = ").append(formatValue(f.get(null)));
                    } catch (Throwable ignored) { }
                }
                sb.append('\n');
            }
        } catch (Throwable t) {
            sb.append("  campos: ").append(t).append('\n');
        }
        if (sb.length() > 0 || !filterMembers) {
            log("[" + c.getName() + "]\n" + sb.toString().replaceAll("\n$", ""));
        }
    }

    private static String formatValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "\"" + v + "\"";
        if (v instanceof Integer) return v + " (0x" + Integer.toHexString((Integer) v) + ")";
        if (v.getClass().isArray()) {
            if (v instanceof int[]) return Arrays.toString((int[]) v);
            if (v instanceof Object[]) return Arrays.toString((Object[]) v);
        }
        return String.valueOf(v);
    }

    // ---------------------------------------------------------------- dex string scan

    private void scanFrameworkDirs() {
        String[] dirs = {"/system/framework", "/system_ext/framework", "/product/framework",
                "/vendor/framework", "/system/system_ext/framework"};
        Set<String> seen = new LinkedHashSet<>();
        for (String dir : dirs) collectFiles(new File(dir), seen, 0);
        int i = 0;
        for (String path : seen) {
            status("framework " + (++i) + "/" + seen.size());
            scanFile(new File(path));
        }
        log("Ficheros framework analizados: " + seen.size());
    }

    private void collectFiles(File dir, Set<String> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 3) return;
        for (File f : files) {
            if (f.isDirectory()) collectFiles(f, out, depth + 1);
            else {
                String n = f.getName();
                if (n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".vdex")) {
                    try { out.add(f.getCanonicalPath()); } catch (Exception ignored) { }
                }
            }
        }
    }

    private void scanSystemApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<ApplicationInfo> targets = new ArrayList<>();
        for (ApplicationInfo a : apps) {
            String p = a.packageName;
            boolean system = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!system) continue;
            if (p.equals("android") || p.contains("samsung") || p.contains(".sec.")
                    || p.startsWith("com.sec") || p.contains("settings")
                    || p.contains("systemui") || p.contains("display")) {
                targets.add(a);
            }
        }
        int i = 0;
        for (ApplicationInfo a : targets) {
            status("apps " + (++i) + "/" + targets.size() + " " + a.packageName);
            scanFile(new File(a.sourceDir));
            if (a.splitSourceDirs != null) {
                for (String split : a.splitSourceDirs) scanFile(new File(split));
            }
            // Pre-optimised system apps keep their dex in oat/<abi>/<name>.vdex.
            File parent = new File(a.sourceDir).getParentFile();
            if (parent != null) {
                Set<String> vdex = new LinkedHashSet<>();
                collectFiles(new File(parent, "oat"), vdex, 0);
                for (String v : vdex) scanFile(new File(v));
            }
        }
        log("Apps del sistema analizadas: " + targets.size() + " de " + apps.size());
    }

    private void scanFile(File file) {
        String label = file.getName();
        try {
            if (label.endsWith(".vdex")) {
                byte[] data = readFile(file, 256 * 1024 * 1024);
                if (data != null) scanEmbeddedDex(data, label);
                return;
            }
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (!e.getName().matches("classes\\d*\\.dex")) continue;
                    if (e.getSize() > 200L * 1024 * 1024) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        byte[] data = readAll(in);
                        scanDex(data, 0, label);
                    }
                }
            }
        } catch (Throwable t) {
            record("  ! " + file + ": " + t);
        }
    }

    private static byte[] readFile(File f, long limit) throws Exception {
        if (!f.canRead() || f.length() > limit) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void scanEmbeddedDex(byte[] b, String label) {
        // Look for "dex\n0" magic anywhere (vdex containers embed whole dex files).
        for (int i = 0; i + 8 < b.length; i++) {
            if (b[i] == 'd' && b[i + 1] == 'e' && b[i + 2] == 'x' && b[i + 3] == '\n'
                    && b[i + 4] == '0' && b[i + 7] == 0) {
                scanDex(b, i, label);
            }
        }
    }

    private void scanDex(byte[] b, int base, String label) {
        if (base + 0x70 > b.length) return;
        int count = le32(b, base + 0x38);
        int idsOff = le32(b, base + 0x3C);
        if (count <= 0 || idsOff <= 0 || base + idsOff + (long) count * 4 > b.length) return;
        for (int i = 0; i < count; i++) {
            int p = base + le32(b, base + idsOff + i * 4);
            if (p <= 0 || p >= b.length) continue;
            // skip ULEB128 utf16 length
            while (p < b.length && (b[p] & 0x80) != 0) p++;
            p++;
            int end = p;
            while (end < b.length && b[end] != 0) end++;
            if (end - p < 4 || end - p > 300) continue;
            if (!containsKeyword(b, p, end)) continue;
            String s = new String(b, p, end - p, StandardCharsets.UTF_8);
            Set<String> set = dexHits.get(s);
            if (set == null) {
                set = new TreeSet<>();
                dexHits.put(s, set);
            }
            set.add(label);
        }
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8 | (b[o + 2] & 0xff) << 16 | (b[o + 3] & 0xff) << 24;
    }

    private static boolean containsKeyword(byte[] b, int start, int end) {
        for (byte[] k : KEYWORD_BYTES) {
            outer:
            for (int i = start; i + k.length <= end; i++) {
                for (int j = 0; j < k.length; j++) {
                    int c = b[i + j];
                    if (c >= 'A' && c <= 'Z') c += 32;
                    if (c != k[j]) continue outer;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean matchesKeyword(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) if (lower.contains(k)) return true;
        return false;
    }

    // ---------------------------------------------------------------- settings

    private String readSetting(String table, String key) {
        ContentResolver cr = getContentResolver();
        try {
            switch (table) {
                case "system": return Settings.System.getString(cr, key);
                case "secure": return Settings.Secure.getString(cr, key);
                default: return Settings.Global.getString(cr, key);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private Map<String, String> queryTable(String table) {
        try (Cursor c = getContentResolver().query(Uri.parse("content://settings/" + table),
                new String[]{"name", "value"}, null, null, null)) {
            if (c == null) return null;
            Map<String, String> rows = new TreeMap<>();
            while (c.moveToNext()) rows.put(c.getString(0), c.getString(1));
            return rows;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Full tables when readable, plus every candidate key read individually. */
    private Map<String, String> allSettings() {
        Map<String, String> all = new TreeMap<>();
        for (String table : TABLES) {
            Map<String, String> rows = queryTable(table);
            if (rows != null) {
                for (Map.Entry<String, String> e : rows.entrySet()) {
                    all.put(table + "/" + e.getKey(), e.getValue());
                }
            }
            for (String key : candidateKeys) {
                String value = readSetting(table, key);
                if (value != null) all.put(table + "/" + key, value);
            }
        }
        return all;
    }

    private void takeSnapshot() {
        new Thread(() -> {
            snapshot = allSettings();
            log("\nFoto tomada: " + snapshot.size() + " ajustes. Cambia el modo y pulsa Comparar.");
        }).start();
    }

    private void compareSnapshot() {
        if (snapshot == null) {
            log("Primero pulsa Foto.");
            return;
        }
        new Thread(() -> {
            Map<String, String> now = allSettings();
            log("\n=== DIFERENCIAS " + time() + " ===");
            int changes = diff(snapshot, now);
            log(changes == 0 ? "Sin cambios." : changes + " cambios.");
            snapshot = now;
        }).start();
    }

    private int diff(Map<String, String> before, Map<String, String> after) {
        int changes = 0;
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            String a = before.get(k), b = after.get(k);
            if (a == null ? b != null : !a.equals(b)) {
                log("  " + k + ": " + a + " -> " + b);
                changes++;
            }
        }
        return changes;
    }

    private void toggleWatch() {
        if (observer != null) {
            stopWatch();
            log("Vigilancia detenida.");
            return;
        }
        watchState = allSettings();
        observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                onSettingChanged(uri);
            }
        };
        getContentResolver().registerContentObserver(
                Uri.parse("content://settings"), true, observer);
        watchButton.setText("2. Parar");
        log("\nVigilando " + watchState.size() + " ajustes. Cambia Privacy Display / Máxima ahora.");
    }

    private void onSettingChanged(Uri uri) {
        List<String> seg = uri == null ? null : uri.getPathSegments();
        if (seg != null && seg.size() >= 2) {
            String table = seg.get(0), key = seg.get(1);
            String value = readSetting(table, key);
            String old = watchState.put(table + "/" + key, value);
            log(time() + "  " + table + "/" + key + ": " + old + " -> " + value);
        } else {
            // Some changes arrive without a key; fall back to a full diff.
            new Thread(() -> {
                Map<String, String> now = allSettings();
                log(time() + "  cambio sin clave (" + uri + "):");
                diff(watchState, now);
                watchState = now;
            }).start();
        }
    }

    private void stopWatch() {
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
        if (watchButton != null) watchButton.setText("2. Vigilar");
    }

    private static String time() {
        return new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
    }

    // ---------------------------------------------------------------- output

    private void saveReport() {
        String name = "privacy-probe-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                .format(new Date()) + ".txt";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("insert devolvió null");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                synchronized (report) {
                    out.write(report.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            Toast.makeText(this, "Guardado en Descargas/" + name, Toast.LENGTH_LONG).show();
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Compartir informe"));
        } catch (Throwable t) {
            log("ERROR guardando: " + t);
        }
    }

    private void copyReport() {
        String text;
        synchronized (report) {
            text = report.toString();
        }
        if (text.length() > 500_000) {
            text = text.substring(0, 500_000);
            Toast.makeText(this, "Recortado; usa Guardar para el informe completo", Toast.LENGTH_LONG).show();
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("privacy-probe", text));
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- hidden API

    private void exemptHiddenApi() {
        try {
            boolean ok = org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("");
            record("hidden API exemption: " + ok);
        } catch (Throwable t) {
            record("hidden API exemption: " + t);
        }
    }

    // ---------------------------------------------------------------- privacy display API

    private static boolean matchesMember(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return matchesKeyword(lower) || lower.startsWith("pd_") || lower.startsWith("sem_pd");
    }

    private void dumpPrivacyApi() {
        new Thread(() -> {
            log("\n=== API PRIVACY DISPLAY " + time() + " ===");
            String[] names = {
                    "android.window.PrivacyDisplayManager",
                    "android.window.PrivacyDisplayManager$PrivacyDisplayLevel",
                    "android.window.PrivacyDisplayManager$PrivacyDisplayAppCustom",
                    "android.view.View$PrivacyDisplayRegion"
            };
            Set<Class<?>> related = new LinkedHashSet<>();
            for (String name : names) {
                try {
                    Class<?> c = Class.forName(name);
                    dumpClass(c, false);
                    for (Field f : c.getDeclaredFields()) {
                        Class<?> type = f.getType();
                        if (type.isInterface() && !type.getName().startsWith("java")) related.add(type);
                    }
                } catch (Throwable t) {
                    log(name + ": " + t);
                }
            }
            for (Class<?> c : related) dumpClass(c, false);

            log("\n--- Miembros privacy/pd_ en clases del sistema ---");
            Class<?>[] filtered = {View.class, Window.class, WindowManager.LayoutParams.class,
                    Settings.System.class, Settings.Secure.class, Settings.Global.class};
            for (Class<?> c : filtered) dumpFilteredClass(c);

            log("\n--- Instancia ---");
            try {
                Object pdm = getSystemService(Class.forName("android.window.PrivacyDisplayManager"));
                log("getSystemService(PrivacyDisplayManager) = " + pdm);
            } catch (Throwable t) {
                log("getSystemService(PrivacyDisplayManager): " + t);
            }

            log("\n--- Ajustes pd_* / privacy ---");
            for (String table : TABLES) {
                Map<String, String> rows = queryTable(table);
                if (rows == null) continue;
                for (Map.Entry<String, String> e : rows.entrySet()) {
                    if (matchesMember(e.getKey())) log("  " + table + "/" + e.getKey() + " = " + e.getValue());
                }
            }
            log("=== Fin volcado API ===");
        }).start();
    }

    private void dumpFilteredClass(Class<?> c) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!matchesMember(m.getName())) continue;
                sb.append("  M ").append(m.getReturnType().getSimpleName()).append(' ')
                        .append(m.getName()).append(Arrays.toString(m.getParameterTypes())).append('\n');
            }
            for (Field f : c.getDeclaredFields()) {
                if (!matchesMember(f.getName())) continue;
                sb.append("  F ").append(f.getType().getSimpleName()).append(' ').append(f.getName());
                if (Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        sb.append(" = ").append(formatValue(f.get(null)));
                    } catch (Throwable ignored) { }
                }
                sb.append('\n');
            }
        } catch (Throwable t) {
            sb.append("  ").append(t).append('\n');
        }
        if (sb.length() > 0) log("[" + c.getName() + "]\n" + sb.toString().replaceAll("\n$", ""));
    }

    // ---------------------------------------------------------------- write test

    private void testWrite() {
        if (!Settings.System.canWrite(this)) {
            log("\nFalta permiso 'Modificar ajustes del sistema'. Concédelo y vuelve a pulsar.");
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        String key = "privacy_display_level";
        String old = readSetting("system", key);
        String target = "1".equals(old) ? "0" : "1";
        log("\n=== PRUEBA ESCRITURA " + time() + " ===");
        try {
            boolean ok = Settings.System.putString(getContentResolver(), key, target);
            log("system/" + key + ": " + old + " -> " + target + "  putString=" + ok
                    + "  leído ahora=" + readSetting("system", key));
        } catch (Throwable t) {
            log("Escritura RECHAZADA: " + t);
        }
    }
}
