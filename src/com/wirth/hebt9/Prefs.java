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
