package com.wirth.hebt9;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T9 index over contact names.
 *
 * The built-in mapping is the Israeli keypad layout, lifted verbatim from Samsung's own
 * dialpad_N_sub_letters string resources (the "iw" variants inside SamsungDialer.apk).
 * Samsung ships those letters for display only -- its contacts provider indexes Latin and
 * CJK, so nothing Hebrew ever reaches digit_name_lookup. This class is the matching half
 * Samsung never shipped.
 *
 * Other scripts are not bundled. {@link #useLayout} swaps the mapping at runtime from a
 * layout file the user imports, so Arabic, Cyrillic or anything else is a download rather
 * than a code change.
 */
public final class T9Index {

    /** Built-in layout. Index = keypad digit; final forms share their base letter's key. */
    public static final String[] HEBREW = {
        "", "",
        "דהו",          // 2  dalet he vav
        "אבג",          // 3  alef bet gimel
        "מםנן",    // 4  mem final-mem nun final-nun
        "יכךל",    // 5  yod kaf final-kaf lamed
        "זחט",          // 6  zayin het tet
        "רשת",          // 7  resh shin tav
        "צץק",          // 8  tsadi final-tsadi qof
        "סעפף"     // 9  samekh ayin pe final-pe
    };

    /**
     * Latin A-Z matching. Off by design: with it on, "343" also matches DEF/GHI/DEF
     * words and drowns the Hebrew hits. Flip to true to get mixed-script search.
     */
    public static final boolean ENABLE_LATIN = false;

    private static final String[] LATIN = {
        "", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"
    };

    /** Any script, not just the BMP range Hebrew happens to sit in. */
    private static final Map<Character, Character> DIGIT = new HashMap<Character, Character>();

    private static String[] active = HEBREW;

    static {
        rebuild();
    }

    private T9Index() {
    }

    public static String[] layout() {
        return active;
    }

    /** Swaps the active mapping. Callers must re-index every contact afterwards. */
    public static void useLayout(String[] keyLetters) {
        active = (keyLetters == null) ? HEBREW : keyLetters;
        rebuild();
    }

    private static void rebuild() {
        DIGIT.clear();
        for (int d = 2; d <= 9; d++) {
            String letters = d < active.length ? active[d] : null;
            if (letters != null) {
                for (int i = 0; i < letters.length(); i++) {
                    DIGIT.put(Character.valueOf(Character.toLowerCase(letters.charAt(i))),
                            Character.valueOf((char) ('0' + d)));
                }
            }
            if (ENABLE_LATIN) {
                for (char c : LATIN[d].toCharArray()) {
                    DIGIT.put(Character.valueOf(c), Character.valueOf((char) ('0' + d)));
                }
            }
        }
    }

    /**
     * Characters that must not split a word. Niqqud, Arabic diacritics and combining marks
     * generally, plus the geresh/gershayim family -- real contact lists are full of names
     * like {@code "yigal sin <gershayim> gilad mai <gershayim>"}, and splitting on those
     * would strand every word after them.
     */
    private static boolean ignorable(char c) {
        if (c >= 0x0591 && c <= 0x05C7) {
            return true;   // Hebrew niqqud + cantillation
        }
        if (c >= 0x064B && c <= 0x0652) {
            return true;   // Arabic harakat
        }
        if (Character.getType(c) == Character.NON_SPACING_MARK) {
            return true;
        }
        return c == 0x05F3 || c == 0x05F4 || c == 0x2019 || c == '\'' || c == '"' || c == '`';
    }

    /** Maps every word of a name to its digit string. Unmappable runs act as separators. */
    public static String[] wordsOf(String name) {
        if (name == null || name.isEmpty()) {
            return new String[0];
        }
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            Character d = DIGIT.get(Character.valueOf(c));
            if (d != null) {
                cur.append(d.charValue());
            } else if (!ignorable(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out.toArray(new String[out.size()]);
    }

    /** Strips formatting so "+972 52-297-8262" becomes "972522978262". */
    public static String digitsOf(String number) {
        if (number == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static final class Contact {
        public long id;
        public String name;
        /** Every number on the contact, primary first. */
        public final List<String> numbers = new ArrayList<String>();
        /** Type label per number ("Mobile", "Home", ...), parallel to {@link #numbers}. */
        public final List<String> labels = new ArrayList<String>();
        public String[] words = new String[0];
        /**
         * joined[w] is words[w..end] concatenated, so typing straight through a space
         * still matches: "נדב וירטהיים" indexes 423 + 25762554, and 4232 hits it.
         */
        private String[] joined = new String[0];
        private String[] numberDigits = new String[0];
        int score;

        public void index() {
            words = wordsOf(name);
            joined = new String[words.length];
            for (int w = words.length - 1; w >= 0; w--) {
                joined[w] = (w == words.length - 1) ? words[w] : words[w] + joined[w + 1];
            }
            numberDigits = new String[numbers.size()];
            for (int i = 0; i < numbers.size(); i++) {
                numberDigits[i] = digitsOf(numbers.get(i));
            }
        }

        /** True when any of the contact's numbers contains the typed digits. */
        boolean numberContains(String query) {
            for (int i = 0; i < numberDigits.length; i++) {
                if (numberDigits[i].contains(query)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Name matches outrank number matches by this margin. */
    private static final int NUMBER_MATCH = 1000;

    /**
     * Prefix match against every word, not just the first -- "343" has to find
     * "ima berakha sela" and "adi ima shel hodiya" alike -- and against each word's
     * run-on with the words after it, so spaces can be typed straight through.
     * Falls back to a substring match on the numbers so the pad still behaves like a
     * dialer.
     */
    public static List<Contact> search(List<Contact> all, String query, int limit) {
        List<Contact> hits = new ArrayList<Contact>();
        if (query == null || query.isEmpty()) {
            return hits;
        }
        for (int i = 0, n = all.size(); i < n; i++) {
            Contact c = all.get(i);
            int best = Integer.MAX_VALUE;
            for (int w = 0; w < c.words.length; w++) {
                int score;
                if (c.words[w].startsWith(query)) {
                    // Whole-word hits beat prefix hits at the same position: typing 343
                    // for אמא must not rank behind אמגד, which merely starts with it.
                    score = w * 3 + (c.words[w].length() == query.length() ? 0 : 1);
                } else if (c.joined[w].startsWith(query)) {
                    // Ran past the end of this word into the next one.
                    score = w * 3 + 2;
                } else {
                    continue;
                }
                if (score < best) {
                    best = score;
                }
            }
            if (best == Integer.MAX_VALUE && c.numberContains(query)) {
                best = NUMBER_MATCH;
            }
            if (best == Integer.MAX_VALUE) {
                continue;
            }
            c.score = best;
            hits.add(c);
        }
        Collections.sort(hits, RANK);
        return hits.size() > limit ? new ArrayList<Contact>(hits.subList(0, limit)) : hits;
    }

    /** Whole-word and earlier matches win, then the shorter name, then alphabetical. */
    private static final Comparator<Contact> RANK = new Comparator<Contact>() {
        public int compare(Contact a, Contact b) {
            if (a.score != b.score) {
                return a.score - b.score;
            }
            int la = a.name == null ? 0 : a.name.length();
            int lb = b.name == null ? 0 : b.name.length();
            if (la != lb) {
                return la - lb;
            }
            String na = a.name == null ? "" : a.name;
            String nb = b.name == null ? "" : b.name;
            return na.compareTo(nb);
        }
    };
}
