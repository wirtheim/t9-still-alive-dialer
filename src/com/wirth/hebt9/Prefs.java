package com.wirth.hebt9;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.Map;

/** All persisted settings, plus the launcher-icon swap. */
public final class Prefs {

    private static final String FILE = "hebt9";
    private static final String KEY_THEME = "theme";
    private static final String KEY_TAP = "tap";
    private static final String KEY_ICON = "icon";
    private static final String KEY_LAYOUT = "layout";
    private static final String NUM_PREFIX = "num_";
    private static final String KEY_WA = "wa_enabled";
    private static final String KEY_WA_RESULTS = "wa_results";
    private static final String KEY_WA_TILES = "wa_tiles";
    private static final String KEY_WA_PANEL = "wa_panel";
    private static final String KEY_WA_APP = "wa_app";
    private static final String KEY_HAPTIC = "haptic";
    private static final String KEY_SEARCHES = "searches";

    /** Which WhatsApp app a badge tap targets when both are installed. */
    public static final String WA_APP_ASK = "ask";
    public static final String WA_APP_STD = "std";
    public static final String WA_APP_BIZ = "biz";

    /** Vibration amplitude for a key press, 0 (off) .. 255. Default is deliberately gentle. */
    public static final int HAPTIC_OFF = 0;
    public static final int HAPTIC_MAX = 255;
    public static final int HAPTIC_DEFAULT = 55;

    private static final int SEARCH_HISTORY_MAX = 12;

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String TAP_CALL = "call";
    public static final String TAP_OPEN = "open";

    /** Launcher aliases, one per icon. Index matches KEY_ICON. */
    public static final String[] ICON_ALIASES = {
        "com.wirth.hebt9.IconLogo",
        "com.wirth.hebt9.IconIndigo",
        "com.wirth.hebt9.IconGreen",
        "com.wirth.hebt9.IconGraphite",
        "com.wirth.hebt9.IconLight"
    };

    public static final int[] ICON_DRAWABLES = {
        R.drawable.ic_logo,
        R.drawable.ic_phone_indigo,
        R.drawable.ic_phone_green,
        R.drawable.ic_phone_graphite,
        R.drawable.ic_phone_light
    };

    public static final String[] ICON_NAMES = {
        "W-are-theim", "Indigo", "Green", "Graphite", "Light"
    };

    // ---------------------------------------------------------------- layout

    public static String layoutName(Context c) {
        return of(c).getString(KEY_LAYOUT, Layouts.BUILT_IN);
    }

    public static void setLayoutName(Context c, String name) {
        of(c).edit().putString(KEY_LAYOUT, name).apply();
    }

    private Prefs() {
    }

    public static SharedPreferences of(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- theme

    public static String theme(Context c) {
        return of(c).getString(KEY_THEME, THEME_SYSTEM);
    }

    public static void setTheme(Context c, String value) {
        of(c).edit().putString(KEY_THEME, value).apply();
    }

    /** Platform themes only -- no AppCompat, so DayNight does the system-follow work. */
    public static int themeRes(Context c) {
        String t = theme(c);
        if (THEME_LIGHT.equals(t)) {
            return android.R.style.Theme_DeviceDefault_Light;
        }
        if (THEME_DARK.equals(t)) {
            return android.R.style.Theme_DeviceDefault;
        }
        return android.R.style.Theme_DeviceDefault_DayNight;
    }

    // ------------------------------------------------------------ behaviour

    public static String tapAction(Context c) {
        return of(c).getString(KEY_TAP, TAP_CALL);
    }

    public static void setTapAction(Context c, String value) {
        of(c).edit().putString(KEY_TAP, value).apply();
    }

    // ---------------------------------------------------------------- whatsapp

    /**
     * Master switch for the WhatsApp badges. When off, no placement shows regardless of
     * its own toggle -- the three placement getters all AND with this.
     */
    public static boolean waEnabled(Context c) {
        return of(c).getBoolean(KEY_WA, true);
    }

    public static void setWaEnabled(Context c, boolean v) {
        of(c).edit().putBoolean(KEY_WA, v).apply();
    }

    /** Search-result rows -- the one placement on by default. */
    public static boolean waInResults(Context c) {
        return waEnabled(c) && of(c).getBoolean(KEY_WA_RESULTS, true);
    }

    public static void setWaInResults(Context c, boolean v) {
        of(c).edit().putBoolean(KEY_WA_RESULTS, v).apply();
    }

    public static boolean waInTiles(Context c) {
        return waEnabled(c) && of(c).getBoolean(KEY_WA_TILES, false);
    }

    public static void setWaInTiles(Context c, boolean v) {
        of(c).edit().putBoolean(KEY_WA_TILES, v).apply();
    }

    public static boolean waInPanel(Context c) {
        return waEnabled(c) && of(c).getBoolean(KEY_WA_PANEL, false);
    }

    public static void setWaInPanel(Context c, boolean v) {
        of(c).edit().putBoolean(KEY_WA_PANEL, v).apply();
    }

    /** Raw stored value of a placement toggle, ignoring the master (for the settings UI). */
    public static boolean waResultsRaw(Context c) {
        return of(c).getBoolean(KEY_WA_RESULTS, true);
    }

    public static boolean waTilesRaw(Context c) {
        return of(c).getBoolean(KEY_WA_TILES, false);
    }

    public static boolean waPanelRaw(Context c) {
        return of(c).getBoolean(KEY_WA_PANEL, false);
    }

    /** Preferred WhatsApp app for a badge tap; default "ask" offers whatever is installed. */
    public static String waApp(Context c) {
        return of(c).getString(KEY_WA_APP, WA_APP_ASK);
    }

    public static void setWaApp(Context c, String value) {
        of(c).edit().putString(KEY_WA_APP, value).apply();
    }

    // --------------------------------------------------------------- haptics

    public static int haptic(Context c) {
        int v = of(c).getInt(KEY_HAPTIC, HAPTIC_DEFAULT);
        return v < HAPTIC_OFF ? HAPTIC_OFF : (v > HAPTIC_MAX ? HAPTIC_MAX : v);
    }

    public static void setHaptic(Context c, int amplitude) {
        of(c).edit().putInt(KEY_HAPTIC, amplitude).apply();
    }

    // --------------------------------------------------------- search history

    /** Most-recent-first, de-duplicated, capped. Stored as newline-joined text. */
    public static java.util.List<String> searches(Context c) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String raw = of(c).getString(KEY_SEARCHES, "");
        if (raw.isEmpty()) {
            return out;
        }
        for (String s : raw.split("\n")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Pushes a query to the front, dropping any earlier copy and trimming to the cap. */
    public static void addSearch(Context c, String query) {
        if (query == null || query.isEmpty()) {
            return;
        }
        java.util.List<String> list = searches(c);
        list.remove(query);
        list.add(0, query);
        while (list.size() > SEARCH_HISTORY_MAX) {
            list.remove(list.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(list.get(i));
        }
        of(c).edit().putString(KEY_SEARCHES, sb.toString()).apply();
    }

    public static int searchesCount(Context c) {
        return searches(c).size();
    }

    public static void clearSearches(Context c) {
        of(c).edit().remove(KEY_SEARCHES).apply();
    }

    // ---------------------------------------------------------- default number

    public static String defaultNumber(Context c, long contactId) {
        return of(c).getString(NUM_PREFIX + contactId, null);
    }

    public static void setDefaultNumber(Context c, long contactId, String number) {
        of(c).edit().putString(NUM_PREFIX + contactId, number).apply();
    }

    // ---------------------------------------------------------------- usage

    private static final String USE_PREFIX = "use_";

    /** Bumped every time a call is placed from the app, to rank the frequent tiles. */
    public static void recordUse(Context c, long contactId) {
        if (contactId < 0) {
            return;
        }
        String key = USE_PREFIX + contactId;
        of(c).edit().putInt(key, of(c).getInt(key, 0) + 1).apply();
    }

    public static int uses(Context c, long contactId) {
        return of(c).getInt(USE_PREFIX + contactId, 0);
    }

    public static int usageCount(Context c) {
        int n = 0;
        for (String key : of(c).getAll().keySet()) {
            if (key.startsWith(USE_PREFIX)) {
                n++;
            }
        }
        return n;
    }

    public static void clearUsage(Context c) {
        SharedPreferences p = of(c);
        SharedPreferences.Editor e = p.edit();
        for (String key : p.getAll().keySet()) {
            if (key.startsWith(USE_PREFIX)) {
                e.remove(key);
            }
        }
        e.apply();
    }

    public static int defaultsCount(Context c) {
        int n = 0;
        for (Map.Entry<String, ?> e : of(c).getAll().entrySet()) {
            if (e.getKey().startsWith(NUM_PREFIX)) {
                n++;
            }
        }
        return n;
    }

    public static void clearDefaults(Context c) {
        SharedPreferences p = of(c);
        SharedPreferences.Editor e = p.edit();
        for (String key : p.getAll().keySet()) {
            if (key.startsWith(NUM_PREFIX)) {
                e.remove(key);
            }
        }
        e.apply();
    }

    // ----------------------------------------------------------------- icon

    public static int icon(Context c) {
        int i = of(c).getInt(KEY_ICON, 0);
        return i >= 0 && i < ICON_ALIASES.length ? i : 0;
    }

    /**
     * Swaps the launcher icon by toggling activity-aliases. The wanted alias is enabled
     * before the others are disabled -- disable-first would briefly leave the app with no
     * launcher entry at all, and some launchers drop the shortcut permanently when that
     * happens.
     */
    public static void applyIcon(Context c, int index) {
        if (index < 0 || index >= ICON_ALIASES.length) {
            return;
        }
        of(c).edit().putInt(KEY_ICON, index).apply();
        PackageManager pm = c.getPackageManager();
        setAlias(pm, c, index, true);
        for (int i = 0; i < ICON_ALIASES.length; i++) {
            if (i != index) {
                setAlias(pm, c, i, false);
            }
        }
    }

    private static void setAlias(PackageManager pm, Context c, int index, boolean enabled) {
        ComponentName cn = new ComponentName(c.getPackageName(), ICON_ALIASES[index]);
        int want = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(cn) != want) {
            pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP);
        }
    }
}
