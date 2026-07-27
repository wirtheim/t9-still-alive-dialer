package com.wirth.hebt9;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Keypad layouts as external files.
 *
 * Only Hebrew ships inside the app. Every other script is a plain-text .t9 file the user
 * imports, so adding Arabic or Cyrillic never needs a new build. Format:
 *
 * <pre>
 * # comment
 * name = Arabic
 * 2 = ابت
 * 3 = ثجح
 * </pre>
 *
 * Keys 2..9 only; anything else is ignored.
 */
public final class Layouts {

    public static final String BUILT_IN = "Hebrew";
    private static final String DIR = "layouts";
    private static final String EXT = ".t9";

    public static final class Layout {
        public final String name;
        public final String[] keys;

        Layout(String name, String[] keys) {
            this.name = name;
            this.keys = keys;
        }
    }

    private Layouts() {
    }

    // ------------------------------------------------------------- parsing

    /** Returns null when the text carries no usable key definitions. */
    public static Layout parse(String text, String fallbackName) {
        if (text == null) {
            return null;
        }
        String[] keys = new String[10];
        Arrays.fill(keys, "");
        String name = fallbackName;
        boolean any = false;

        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if ("name".equalsIgnoreCase(key)) {
                if (!value.isEmpty()) {
                    name = value;
                }
                continue;
            }
            if (key.length() == 1 && key.charAt(0) >= '2' && key.charAt(0) <= '9') {
                // Spaces are allowed between letters for readability: "2 = א ב ג".
                keys[key.charAt(0) - '0'] = value.replace(" ", "");
                any = true;
            }
        }
        return any ? new Layout(name, keys) : null;
    }

    public static String serialize(Layout layout) {
        StringBuilder sb = new StringBuilder();
        sb.append("name = ").append(layout.name).append('\n');
        for (int d = 2; d <= 9; d++) {
            sb.append(d).append(" = ").append(layout.keys[d] == null ? "" : layout.keys[d])
                    .append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- storage

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    /** Built-in first, then imported ones alphabetically. */
    public static List<String> names(Context c) {
        List<String> out = new ArrayList<String>();
        out.add(BUILT_IN);
        File[] files = dir(c).listFiles();
        if (files != null) {
            List<String> imported = new ArrayList<String>();
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(EXT)) {
                    imported.add(n.substring(0, n.length() - EXT.length()));
                }
            }
            Collections.sort(imported);
            out.addAll(imported);
        }
        return out;
    }

    public static Layout load(Context c, String name) {
        if (name == null || BUILT_IN.equals(name)) {
            return new Layout(BUILT_IN, T9Index.HEBREW);
        }
        File f = new File(dir(c), name + EXT);
        if (!f.exists()) {
            return new Layout(BUILT_IN, T9Index.HEBREW);
        }
        try {
            return parse(read(new java.io.FileInputStream(f)), name);
        } catch (IOException e) {
            return new Layout(BUILT_IN, T9Index.HEBREW);
        }
    }

    /** Imports a layout stream, returning the stored name or null when unparseable. */
    public static String importFrom(Context c, InputStream in, String fallbackName)
            throws IOException {
        Layout parsed = parse(read(in), fallbackName);
        if (parsed == null) {
            return null;
        }
        String safe = sanitize(parsed.name);
        FileOutputStream out = new FileOutputStream(new File(dir(c), safe + EXT));
        try {
            out.write(serialize(parsed).getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return safe;
    }

    public static boolean delete(Context c, String name) {
        if (name == null || BUILT_IN.equals(name)) {
            return false;
        }
        return new File(dir(c), name + EXT).delete();
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return s.isEmpty() ? "Layout" : s;
    }

    private static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
