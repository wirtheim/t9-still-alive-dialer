import com.wirth.hebt9.Layouts;
import com.wirth.hebt9.T9Index;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Checks the shipped .t9 sample layouts actually parse and map. They are published as
 * working examples, so a typo in one of them is a broken promise, not a cosmetic bug.
 */
public class LayoutTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : "layouts";

        Layouts.Layout greek = load(root + "/Greek.t9");
        eq("greek name", "Greek", greek.name);
        T9Index.useLayout(greek.keys);
        // γιώργος = γ(2) ι(4) ώ(9) ρ(7) γ(2) ο(6) ς(7)
        eq("Γιώργος maps", "2497267", T9Index.wordsOf("Γιώργος")[0]);
        // Accented and bare forms must land on the same key.
        eq("greek accent folds", T9Index.wordsOf("ω")[0], T9Index.wordsOf("ώ")[0]);
        eq("greek final sigma", T9Index.wordsOf("σ")[0], T9Index.wordsOf("ς")[0]);

        Layouts.Layout russian = load(root + "/Russian.t9");
        eq("russian name", "Russian", russian.name);
        T9Index.useLayout(russian.keys);
        // иван = и(4) в(2) а(2) н(5)
        eq("Иван maps", "4225", T9Index.wordsOf("Иван")[0]);
        eq("russian yo folds", T9Index.wordsOf("е")[0], T9Index.wordsOf("ё")[0]);

        Layouts.Layout arabic = load(root + "/Arabic.t9");
        eq("arabic name", "Arabic", arabic.name);
        T9Index.useLayout(arabic.keys);
        // أحمد -> ح(3) م(8) د(4); the hamza-alef is not in the table and is skipped.
        yes("Arabic word maps to digits", T9Index.wordsOf("احمد")[0].length() == 4);

        // Back to the built-in layout, and make sure it still works.
        T9Index.useLayout(null);
        eq("hebrew restored", "343", T9Index.wordsOf("אמא")[0]);

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL LAYOUT TESTS PASSED");
        } else {
            System.out.println(failures + " TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static Layouts.Layout load(String path) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        Layouts.Layout l = Layouts.parse(text, "?");
        if (l == null) {
            System.out.println("  FAIL  " + path + " -- no key definitions parsed");
            failures++;
            System.exit(1);
        }
        return l;
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
