import com.wirth.hebt9.T9Index;

import java.util.ArrayList;
import java.util.List;

/**
 * Desktop-JVM check of the matching engine. T9Index has no Android dependencies.
 *
 * All fixture names and numbers are synthetic. They are chosen for their digit shape,
 * not their meaning -- see the comment on each group.
 */
public class T9Test {

    private static int failures = 0;

    public static void main(String[] args) {
        List<T9Index.Contact> db = new ArrayList<T9Index.Contact>();

        // אמא = alef(3) mem(4) alef(3) -> exactly "343".
        add(db, 1, "אמא", "050-0000001");
        add(db, 2, "אמא של דנה", "050-0000002");
        // אמא as the second word, so word-position ranking can be checked.
        add(db, 3, "רותי אמא שלי", "050-0000003");
        // Prefix collisions: both start with 343 without being it.
        // במבה = 3432, גמגום = 34324.
        add(db, 4, "במבה", "050-0000004");
        add(db, 5, "גמגום", "050-0000005");
        // Gershayim inside a word.
        add(db, 6, "צה״ל מוקד", "050-0000006");
        // Multi-number contact; only the SECOND number carries 343.
        add(db, 7, "מוסך בדיקה", "03-0000007", "050-0000343");
        // Latin name, to prove Latin matching stays off.
        add(db, 8, "Alice Example", "050-0000008");
        // Cross-space matching: נדב = 423, then ו = 2.
        add(db, 9, "נדב וירטהיים", "050-0000009");

        eq("wordsOf ima", "343", T9Index.wordsOf("אמא")[0]);

        // Final forms share their base letter's key.
        eq("final mem -> 4", T9Index.wordsOf("ם")[0], T9Index.wordsOf("מ")[0]);
        eq("final nun -> 4", T9Index.wordsOf("ן")[0], T9Index.wordsOf("נ")[0]);
        eq("final kaf -> 5", T9Index.wordsOf("ך")[0], T9Index.wordsOf("כ")[0]);
        eq("final pe -> 9", T9Index.wordsOf("ף")[0], T9Index.wordsOf("פ")[0]);
        eq("final tsadi -> 8", T9Index.wordsOf("ץ")[0], T9Index.wordsOf("צ")[0]);

        // Standalone gershayim contribute no word of their own.
        eq("standalone gershayim", 2, T9Index.wordsOf("אבג ״ דהו ״").length);

        // ...but inside a word they must not split it: צה״ל = tsadi(8) he(2) lamed(5).
        eq("gershayim inside word", 1, T9Index.wordsOf("צה״ל").length);
        eq("gershayim inside word digits", "825", T9Index.wordsOf("צה״ל")[0]);

        // Same for geresh: ג׳ורג׳ = gimel(3) vav(2) resh(7) gimel(3).
        eq("geresh inside word", 1, T9Index.wordsOf("ג׳ורג׳").length);
        eq("geresh inside word digits", "3273", T9Index.wordsOf("ג׳ורג׳")[0]);

        // Niqqud must not split either.
        eq("niqqud ignored", "343", T9Index.wordsOf("אִמָּא")[0]);

        // Typing straight through the space.
        eq("nadav word digits", "423", T9Index.wordsOf("נדב וירטהיים")[0]);
        yes("4232 crosses the space",
                contains(T9Index.search(db, "4232", 60), "נדב וירטהיים"));
        yes("42325 crosses the space",
                contains(T9Index.search(db, "42325", 60), "נדב וירטהיים"));
        yes("full name ignoring spaces still matches",
                contains(T9Index.search(db, "42325762554", 60), "נדב וירטהיים"));
        yes("423 still matches the first word alone",
                contains(T9Index.search(db, "423", 60), "נדב וירטהיים"));

        List<T9Index.Contact> hits = T9Index.search(db, "343", 60);
        System.out.println("\nsearch(\"343\") ->");
        for (T9Index.Contact c : hits) {
            System.out.println("   " + c.name + "   " + c.numbers);
        }

        // The exact failure the stock dialer had: 343 must surface the ima contacts.
        eq("343 top hit", "אמא", hits.get(0).name);

        // Whole word beats prefix. במבה/גמגום also start with 343 but are longer words.
        yes("collisions are matched at all",
                contains(hits, "במבה") && contains(hits, "גמגום"));
        yes("whole-word אמא beats prefix במבה",
                indexOf(hits, "אמא של דנה") < indexOf(hits, "במבה"));
        yes("whole-word אמא beats prefix גמגום",
                indexOf(hits, "אמא של דנה") < indexOf(hits, "גמגום"));

        yes("343 finds ima-shel-dana", contains(hits, "אמא של דנה"));
        // Matches on a later word too, not just the first.
        yes("343 finds ruti-ima (word 2)", contains(hits, "רותי אמא שלי"));
        // Number substring still works, and ranks below name hits.
        yes("343 still finds number match", contains(hits, "מוסך בדיקה"));
        yes("name hits outrank number hits",
                indexOf(hits, "אמא") < indexOf(hits, "מוסך בדיקה"));

        // The 343 lives on the contact's SECOND number, so a first-number-only
        // index would miss it entirely.
        yes("matches a non-first number", contains(hits, "מוסך בדיקה"));

        // Latin is disabled, so a Latin name must not pollute Hebrew results.
        yes("latin off: Alice absent from 343", !contains(hits, "Alice Example"));
        eq("latin off: 6337 has no hit", 0, T9Index.search(db, "6337", 60).size());

        yes("empty query returns nothing", T9Index.search(db, "", 60).isEmpty());

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println(failures + " TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static void add(List<T9Index.Contact> db, long id, String name, String... numbers) {
        T9Index.Contact c = new T9Index.Contact();
        c.id = id;
        c.name = name;
        for (String n : numbers) {
            c.numbers.add(n);
            c.labels.add("Mobile");
        }
        c.index();
        db.add(c);
    }

    private static boolean contains(List<T9Index.Contact> l, String name) {
        return indexOf(l, name) >= 0;
    }

    private static int indexOf(List<T9Index.Contact> l, String name) {
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void eq(String label, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        report(label, ok, "expected <" + expected + "> got <" + actual + ">");
    }

    private static void yes(String label, boolean ok) {
        report(label, ok, "expected true");
    }

    private static void report(String label, boolean ok, String detail) {
        if (ok) {
            System.out.println("  PASS  " + label);
        } else {
            System.out.println("  FAIL  " + label + " -- " + detail);
            failures++;
        }
    }
}
