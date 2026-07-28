package com.wirth.hebt9;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hebrew T9 contact search.
 *
 * Deliberately NOT a dialer: it never claims RoleManager.ROLE_DIALER and never
 * implements InCallService. Placing a call goes out as ACTION_CALL, so Telecom
 * still hands the call to Samsung's dialer -- call recording, call log and the
 * in-call UI stay exactly as they are.
 */
public class MainActivity extends Activity {

    private static final int REQ_CONTACTS = 1;
    private static final int REQ_CALL = 2;
    private static final int MENU_SETTINGS = 100;
    private static final int MAX_RESULTS = 60;

    private final List<T9Index.Contact> all = new ArrayList<T9Index.Contact>();
    private final List<T9Index.Contact> shown = new ArrayList<T9Index.Contact>();
    private final StringBuilder query = new StringBuilder();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView queryView;
    private TextView statusView;
    private ListView listView;
    private LinearLayout frequentRow;
    private HorizontalScrollView frequentScroll;
    private LinearLayout historyRow;
    private HorizontalScrollView historyScroll;
    private BaseAdapter adapter;
    private String pendingNumber;
    private long pendingContactId = -1;
    private String appliedTheme;
    private String appliedLayout;

    @Override
    protected void onCreate(Bundle state) {
        appliedTheme = Prefs.theme(this);
        setTheme(Prefs.themeRes(this));
        super.onCreate(state);
        // Component enabled-states survive upgrades, so an alias enabled by an older
        // build stays enabled even after the manifest default changes -- which shows the
        // app twice in the launcher. Re-assert the stored choice on every start.
        Prefs.applyIcon(this, Prefs.icon(this));
        // Must precede buildUi(): the keypad draws its letters from the active layout.
        appliedLayout = Prefs.layoutName(this);
        T9Index.useLayout(Layouts.load(this, appliedLayout).keys);
        setContentView(buildUi());
        // The launcher and the store carry the full name; the ActionBar gets the short
        // one so it does not truncate on narrow screens.
        if (getActionBar() != null) {
            getActionBar().setTitle(R.string.app_name_short);
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        } else {
            loadContacts();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A layout swap changes every contact's digits, so the whole keypad and index
        // have to be rebuilt -- easiest done by recreating the activity.
        if (!appliedTheme.equals(Prefs.theme(this))
                || !appliedLayout.equals(Prefs.layoutName(this))) {
            recreate();
            return;
        }
        // The query was cleared on the way out (see onStop), and defaults/usage/history may
        // have changed in the panel -- refresh() redraws all of it from scratch.
        if (adapter != null) {
            refresh();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the dialer resets the search so a later visit starts clean. The query is
        // first filed into the recent-search history so the tiles can bring it back.
        if (query.length() > 0) {
            Prefs.addSearch(this, query.toString());
            query.setLength(0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SETTINGS, 0, "Settings")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------- ui

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fitSystemBars(this, root);

        statusView = new TextView(this);
        statusView.setPadding(dp(16), dp(12), dp(16), dp(4));
        statusView.setText("Loading contacts...");
        root.addView(statusView);

        listView = new ListView(this);
        listView.setStackFromBottom(true);
        adapter = new ResultAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                onTap(shown.get(i));
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int i, long id) {
                showPanel(shown.get(i));
                return true;
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        queryView = new TextView(this);
        queryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        queryView.setGravity(Gravity.CENTER);
        queryView.setPadding(dp(16), dp(10), dp(16), dp(10));
        queryView.setMinHeight(dp(56));
        GradientDrawable queryBox = new GradientDrawable();
        queryBox.setCornerRadius(dp(14));
        queryBox.setStroke(dp(1), strokeColor());
        queryView.setBackground(queryBox);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.setMargins(dp(10), dp(4), dp(10), dp(6));
        queryView.setLayoutParams(qlp);
        root.addView(queryView);

        frequentRow = new LinearLayout(this);
        frequentRow.setOrientation(LinearLayout.HORIZONTAL);
        frequentRow.setPadding(dp(10), 0, dp(10), dp(6));
        frequentScroll = new HorizontalScrollView(this);
        frequentScroll.setHorizontalScrollBarEnabled(false);
        frequentScroll.addView(frequentRow);
        root.addView(frequentScroll);

        // Recent typed searches, a second tile strip under the frequent contacts. Tapping a
        // tile re-runs that search.
        historyRow = new LinearLayout(this);
        historyRow.setOrientation(LinearLayout.HORIZONTAL);
        historyRow.setPadding(dp(10), 0, dp(10), dp(6));
        historyScroll = new HorizontalScrollView(this);
        historyScroll.setHorizontalScrollBarEnabled(false);
        historyScroll.addView(historyRow);
        root.addView(historyScroll);

        root.addView(buildPad());
        root.addView(buildActions());
        return root;
    }

    private View buildPad() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(dp(8), 0, dp(8), 0);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (String key : keys) {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(58);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(buildKey(key), lp);
        }
        return grid;
    }

    private View buildKey(final String key) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setBackground(borderedBackground());

        TextView digit = new TextView(this);
        digit.setText(key);
        digit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        digit.setGravity(Gravity.CENTER);
        cell.addView(digit);

        // The whole point of the app: Hebrew letters are drawn by us, so they show
        // regardless of the device language staying English.
        String letters = "";
        if (key.length() == 1 && key.charAt(0) >= '0' && key.charAt(0) <= '9') {
            String[] layout = T9Index.layout();
            int d = key.charAt(0) - '0';
            letters = d < layout.length && layout[d] != null ? layout[d] : "";
        }
        if (!letters.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(spaced(letters));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            sub.setGravity(Gravity.CENTER);
            sub.setTextColor(Color.GRAY);
            cell.addView(sub);
        }

        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                haptic();
                query.append(key);
                refresh();
            }
        });
        return cell;
    }

    private View buildActions() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(10));

        bar.addView(actionButton("Clear", new View.OnClickListener() {
            public void onClick(View v) {
                haptic();
                query.setLength(0);
                refresh();
            }
        }, null));

        bar.addView(actionButton("Call", new View.OnClickListener() {
            public void onClick(View v) {
                haptic();
                if (!shown.isEmpty()) {
                    onTap(shown.get(shown.size() - 1));
                } else if (query.length() > 0) {
                    call(query.toString(), -1);
                }
            }
        }, null));

        bar.addView(actionButton("Del", new View.OnClickListener() {
            public void onClick(View v) {
                haptic();
                if (query.length() > 0) {
                    query.setLength(query.length() - 1);
                    refresh();
                }
            }
        }, new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                query.setLength(0);
                refresh();
                return true;
            }
        }));
        return bar;
    }

    private View actionButton(String label, View.OnClickListener click,
                              View.OnLongClickListener longClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(0, dp(12), 0, dp(12));
        t.setClickable(true);
        t.setFocusable(true);
        t.setBackground(themedBackground());
        t.setOnClickListener(click);
        if (longClick != null) {
            t.setOnLongClickListener(longClick);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(lp);
        return t;
    }

    // ---------------------------------------------------------- frequent tiles

    private static final int FREQUENT_TILES = 8;

    /**
     * Most-called contacts, ranked by the app's own tally. Hidden once the user starts
     * typing -- at that point the result list is the better target and the extra row
     * would only steal height from it.
     */
    private void refreshFrequent() {
        if (query.length() > 0) {
            frequentScroll.setVisibility(View.GONE);
            return;
        }
        List<T9Index.Contact> ranked = new ArrayList<T9Index.Contact>();
        for (int i = 0; i < all.size(); i++) {
            if (Prefs.uses(this, all.get(i).id) > 0) {
                ranked.add(all.get(i));
            }
        }
        Collections.sort(ranked, new Comparator<T9Index.Contact>() {
            public int compare(T9Index.Contact a, T9Index.Contact b) {
                return Prefs.uses(MainActivity.this, b.id) - Prefs.uses(MainActivity.this, a.id);
            }
        });
        frequentRow.removeAllViews();
        int n = Math.min(FREQUENT_TILES, ranked.size());
        for (int i = 0; i < n; i++) {
            frequentRow.addView(buildTile(ranked.get(i)));
        }
        frequentScroll.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
    }

    /** Recent typed searches, newest first. Hidden while typing, like the frequent row. */
    private void refreshHistory() {
        if (query.length() > 0) {
            historyScroll.setVisibility(View.GONE);
            return;
        }
        List<String> recent = Prefs.searches(this);
        historyRow.removeAllViews();
        for (int i = 0; i < recent.size(); i++) {
            historyRow.addView(buildHistoryTile(recent.get(i)));
        }
        historyScroll.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View buildHistoryTile(final String q) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setPadding(dp(10), dp(6), dp(10), dp(6));

        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(dp(14));
        box.setStroke(dp(1), strokeColor());
        tile.setBackground(box);

        TextView digits = new TextView(this);
        digits.setText(q);
        digits.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        digits.setGravity(Gravity.CENTER);
        tile.addView(digits);

        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                query.setLength(0);
                query.append(q);
                refresh();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        lp.setMargins(dp(4), 0, dp(4), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        tile.setLayoutParams(lp);
        return tile;
    }

    private View buildTile(final T9Index.Contact c) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(dp(14));
        box.setColor(tileColor());
        tile.setBackground(box);

        TextView initials = new TextView(this);
        initials.setText(initialsOf(c.name));
        initials.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        initials.setGravity(Gravity.CENTER);
        tile.addView(initials);

        TextView label = new TextView(this);
        label.setText(firstWord(c.name));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setAlpha(0.8f);
        tile.addView(label);

        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                call(resolveNumber(c), c.id);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showPanel(c);
                return true;
            }
        });

        // A tile is a fixed 66dp square; the WhatsApp badge floats in its top-end corner,
        // so it needs a FrameLayout wrapper. Without a badge the wrapper is a harmless
        // single-child frame.
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(66), dp(66));
        flp.setMargins(dp(4), 0, dp(4), 0);
        frame.setLayoutParams(flp);
        frame.addView(tile, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (Prefs.waInTiles(this) && c.hasWhatsApp()) {
            ImageView badge = new ImageView(this);
            badge.setImageResource(R.drawable.ic_whatsapp);
            int s = dp(20);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(s, s);
            bp.gravity = Gravity.TOP | Gravity.END;
            bp.setMargins(0, dp(2), dp(2), 0);
            badge.setLayoutParams(bp);
            badge.setClickable(true);
            badge.setContentDescription("Open WhatsApp");
            badge.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openContactWhatsApp(c);
                }
            });
            frame.addView(badge);
        }
        return frame;
    }

    /** A subtle outline colour that reads on both themes: the text colour at low alpha. */
    private int strokeColor() {
        TypedValue tv = new TypedValue();
        int base = getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true)
                ? getResources().getColor(tv.resourceId, getTheme())
                : Color.GRAY;
        return Color.argb(90, Color.red(base), Color.green(base), Color.blue(base));
    }

    /** A rounded, outlined background with the platform ripple layered on top for touch feedback. */
    private android.graphics.drawable.Drawable borderedBackground() {
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(dp(12));
        box.setStroke(dp(1), strokeColor());
        android.graphics.drawable.Drawable ripple = themedBackground();
        android.graphics.drawable.LayerDrawable layers =
                new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{box, ripple});
        return layers;
    }

    /** Accent at low alpha, so the tiles read on both the light and dark themes. */
    private int tileColor() {
        TypedValue tv = new TypedValue();
        int accent = getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)
                ? tv.data : Color.parseColor("#5B4BE8");
        return Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    private static String initialsOf(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(parts[0].charAt(0));
        if (parts.length > 1 && !parts[1].isEmpty()) {
            sb.append(parts[1].charAt(0));
        }
        return sb.toString();
    }

    private static String firstWord(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String[] parts = name.trim().split("\\s+");
        return parts[0];
    }

    /** A faint green circle behind the WhatsApp badge, so the enlarged tap area reads as a button. */
    private android.graphics.drawable.Drawable pillBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.argb(28, 0x25, 0xD3, 0x66));
        return g;
    }

    /** Borrows the platform's own ripple so the app follows One UI's theme. */
    private android.graphics.drawable.Drawable themedBackground() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getDrawable(tv.resourceId);
    }

    private static String spaced(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Vibrator vibrator() {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    /** A short key-press tick whose strength is the panel's amplitude (0 = off). */
    private void haptic() {
        int amp = Prefs.haptic(this);
        if (amp <= Prefs.HAPTIC_OFF) {
            return;
        }
        Vibrator v = vibrator();
        if (v == null || !v.hasVibrator()) {
            return;
        }
        if (amp > 255) {
            amp = 255;
        }
        try {
            v.vibrate(VibrationEffect.createOneShot(18, amp));
        } catch (Exception e) {
            // Some devices reject explicit amplitudes; a missing tick is harmless.
        }
    }

    // ------------------------------------------------------------ data

    private void loadContacts() {
        statusView.setText("Loading contacts...");
        new Thread(new Runnable() {
            public void run() {
                final List<T9Index.Contact> loaded = readContacts();
                main.post(new Runnable() {
                    public void run() {
                        all.clear();
                        all.addAll(loaded);
                        refresh();
                    }
                });
            }
        }).start();
    }

    /**
     * Synthetic data for store screenshots, so a public listing never shows a real
     * contact. In-memory only -- nothing is written to the device. Reachable solely via
     * an explicit intent extra:
     *
     * <pre>adb shell am start -n com.wirth.hebt9/.MainActivity --ez demo true</pre>
     */
    private static List<T9Index.Contact> demoContacts() {
        // Sized so a screenshot of "343" fills the result list the way a real contact
        // list would. Roughly half of these match: אמא is exactly 343, while אמגד (3432),
        // ג'מאל (3435), במבה (3432) and גמגום (34324) merely start with it -- which is
        // what makes the whole-word ranking visible in the shot.
        String[][] rows = {
            {"אמא", "050-111-2233"},
            {"אמא של דנה", "052-444-5566"},
            {"אמא רחל", "052-118-2299"},
            {"אמא של תום", "054-330-6677"},
            {"אמא ואבא בית", "03-611-2000"},
            {"רותי אמא שלי", "054-777-8899"},
            {"במבה פיצוחים", "03-555-0101"},
            {"גמגום סטודיו", "077-300-4040"},
            {"אמגד מוסך", "04-812-3456"},
            {"ג'מאל אלקטריק", "050-909-1122"},
            {"נדב וירטהיים", "050-123-4567"},
            {"מוסך בדיקה", "03-900-1234"},
            {"דנה כהן", "052-321-7654"},
            {"יוסי חשמלאי", "054-808-4321"},
            {"שירה לוי", "050-222-3344"},
            {"אבי גנן", "053-444-1212"},
            {"מרפאת שיניים", "08-655-7788"},
            {"טל אברהם", "058-770-9090"},
        };
        List<T9Index.Contact> out = new ArrayList<T9Index.Contact>();
        for (int i = 0; i < rows.length; i++) {
            T9Index.Contact c = new T9Index.Contact();
            c.id = 900000 + i;
            c.name = rows[i][0];
            c.numbers.add(rows[i][1]);
            c.labels.add("Mobile");
            // Seed a WhatsApp badge on a subset so a store screenshot can show the feature.
            c.whatsapp.add(Integer.valueOf(i % 2 == 0 ? T9Index.WA_STD : 0));
            c.index();
            out.add(c);
        }
        return out;
    }

    private List<T9Index.Contact> readContacts() {
        if (getIntent() != null && getIntent().getBooleanExtra("demo", false)) {
            return demoContacts();
        }
        List<T9Index.Contact> out = new ArrayList<T9Index.Contact>();
        Map<Long, T9Index.Contact> byId = new HashMap<Long, T9Index.Contact>();
        Map<Long, Map<String, Integer>> wa = readWhatsApp();
        String[] projection = {
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        };
        Cursor c = null;
        try {
            // Primary flags first, so numbers.get(0) is the line the user marked as
            // default in Contacts -- otherwise a multi-number contact hands us an
            // arbitrary one and we would dial the wrong line.
            String order = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC, "
                    + ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC, "
                    + ContactsContract.CommonDataKinds.Phone.IS_PRIMARY + " DESC";
            c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection, null, null, order);
            if (c == null) {
                return out;
            }
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String number = c.getString(2);
                if (number == null || number.isEmpty()) {
                    continue;
                }
                T9Index.Contact contact = byId.get(Long.valueOf(id));
                if (contact == null) {
                    contact = new T9Index.Contact();
                    contact.id = id;
                    contact.name = c.getString(1);
                    byId.put(Long.valueOf(id), contact);
                    out.add(contact);
                } else if (contact.numbers.contains(number)) {
                    continue;  // same line listed twice across accounts
                }
                contact.numbers.add(number);
                contact.labels.add(typeLabel(c.getInt(3), c.getString(4)));
                contact.whatsapp.add(waLookup(wa, id, number));
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).index();
            }
        } catch (SecurityException e) {
            // Permission revoked mid-flight; leave the list empty.
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    private String typeLabel(int type, String custom) {
        CharSequence label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                getResources(), type, custom);
        return label == null ? "" : label.toString();
    }

    // WhatsApp publishes one Data row per registered number. These are the rows the contacts
    // app itself keys its "Message"/"Voice call" quick actions off -- consumer WhatsApp and
    // WhatsApp Business each ship their own pair. We read them ONLY to detect which saved
    // numbers are on which app; opening a chat never touches these rows, it goes through a
    // wa.me link, so a typed number with no contact row works too. The MIME strings are
    // undocumented and version-dependent; if WhatsApp ever renames them we simply find no
    // rows and show no badges (the read degrades on failure).
    private static final String WA_PKG_STD = "com.whatsapp";
    private static final String WA_PKG_BIZ = "com.whatsapp.w4b";
    private static final String WA_STD_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.profile";
    private static final String WA_STD_VOIP = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call";
    private static final String WA_BIZ_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile";
    private static final String WA_BIZ_VOIP = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call";

    /**
     * Maps contactId -> (number tail -> WhatsApp flags). Read once per contact reload on the
     * same background thread as {@link #readContacts()}. The existing READ_CONTACTS grant
     * covers the Data table, so no extra permission is involved.
     */
    private Map<Long, Map<String, Integer>> readWhatsApp() {
        Map<Long, Map<String, Integer>> map = new HashMap<Long, Map<String, Integer>>();
        String[] projection = {
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.MIMETYPE
        };
        String selection = ContactsContract.Data.MIMETYPE + " IN (?, ?, ?, ?)";
        String[] args = {WA_STD_PROFILE, WA_STD_VOIP, WA_BIZ_PROFILE, WA_BIZ_VOIP};
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI, projection, selection, args, null);
            if (c == null) {
                return map;
            }
            while (c.moveToNext()) {
                long contactId = c.getLong(0);
                String data1 = c.getString(1);
                String mime = c.getString(2);
                if (data1 == null) {
                    continue;
                }
                String key = T9Index.tail(data1);
                if (key.isEmpty()) {
                    continue;
                }
                int bit = mime != null && mime.contains(".w4b.") ? T9Index.WA_BIZ : T9Index.WA_STD;
                Map<String, Integer> byNumber = map.get(Long.valueOf(contactId));
                if (byNumber == null) {
                    byNumber = new HashMap<String, Integer>();
                    map.put(Long.valueOf(contactId), byNumber);
                }
                Integer prev = byNumber.get(key);
                byNumber.put(key, Integer.valueOf((prev == null ? 0 : prev.intValue()) | bit));
            }
        } catch (SecurityException e) {
            // Permission revoked mid-flight; treat everything as "no WhatsApp".
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return map;
    }

    private static Integer waLookup(Map<Long, Map<String, Integer>> wa, long contactId, String number) {
        Map<String, Integer> byNumber = wa.get(Long.valueOf(contactId));
        Integer flags = byNumber == null ? null : byNumber.get(T9Index.tail(number));
        return Integer.valueOf(flags == null ? 0 : flags.intValue());
    }

    /** WhatsApp packages actually installed, in {std, biz} order. */
    private List<String> installedWhatsApps() {
        List<String> out = new ArrayList<String>();
        for (String pkg : new String[]{WA_PKG_STD, WA_PKG_BIZ}) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                out.add(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                // not installed
            }
        }
        return out;
    }

    private static String waAppName(String pkg) {
        return WA_PKG_BIZ.equals(pkg) ? "WhatsApp Business" : "WhatsApp";
    }

    /** SIM/network region for turning a locally-typed number into E.164. Falls back to IL. */
    private String regionIso() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm != null) {
                String iso = tm.getSimCountryIso();
                if (iso == null || iso.isEmpty()) {
                    iso = tm.getNetworkCountryIso();
                }
                if (iso != null && !iso.isEmpty()) {
                    return iso.toUpperCase();
                }
            }
        } catch (Exception e) {
            // fall through to the default
        }
        return "IL";
    }

    /** Opens a specific WhatsApp package on a number's wa.me link, or the browser as a last resort. */
    private void openWaPackage(String digits, String pkg) {
        Uri uri = Uri.parse("https://wa.me/" + digits);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            if (pkg != null) {
                i.setPackage(pkg);
            }
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "WhatsApp not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Opens a WhatsApp chat for any number -- contact or freshly typed -- via wa.me, so we
     * never depend on the number already being a contact. The number is normalised to E.164
     * against the SIM region ("0522978262" -> "972522978262"); if that fails we fall back to
     * its raw digits.
     *
     * Which app: the panel preference wins when its app is available; otherwise we offer the
     * installed apps that fit -- for a contact, those it is actually registered on; for a
     * typed number, every installed WhatsApp. One candidate opens straight away; several
     * raise a small chooser.
     */
    private void openWhatsApp(String number, int regFlags, boolean typed) {
        if (number == null || number.isEmpty()) {
            return;
        }
        String e164 = PhoneNumberUtils.formatNumberToE164(number, regionIso());
        final String digits = e164 != null
                ? (e164.startsWith("+") ? e164.substring(1) : e164)
                : T9Index.digitsOf(number);
        if (digits.isEmpty()) {
            return;
        }

        List<String> installed = installedWhatsApps();
        if (installed.isEmpty()) {
            openWaPackage(digits, null);   // nothing installed: browser fallback
            return;
        }

        // Candidates: installed apps that fit this number.
        final List<String> candidates = new ArrayList<String>();
        for (String pkg : installed) {
            int bit = WA_PKG_BIZ.equals(pkg) ? T9Index.WA_BIZ : T9Index.WA_STD;
            if (typed || regFlags == 0 || (regFlags & bit) != 0) {
                candidates.add(pkg);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(installed);   // registered app not installed: offer what is
        }

        // Panel preference narrows the candidates when the chosen app is among them.
        String pref = Prefs.waApp(this);
        if (Prefs.WA_APP_STD.equals(pref) && candidates.contains(WA_PKG_STD)) {
            openWaPackage(digits, WA_PKG_STD);
            return;
        }
        if (Prefs.WA_APP_BIZ.equals(pref) && candidates.contains(WA_PKG_BIZ)) {
            openWaPackage(digits, WA_PKG_BIZ);
            return;
        }
        if (candidates.size() == 1) {
            openWaPackage(digits, candidates.get(0));
            return;
        }
        // Ask: several apps fit.
        final String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = waAppName(candidates.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("Open with")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        openWaPackage(digits, candidates.get(which));
                    }
                })
                .show();
    }

    /** Contact-level badge tap: pick the registered number, then hand off to {@link #openWhatsApp}. */
    private void openContactWhatsApp(T9Index.Contact c) {
        String num = c.waNumber(resolveNumber(c));
        if (num == null) {
            return;
        }
        openWhatsApp(num, c.waFlags(c.numbers.indexOf(num)), false);
    }

    private void refresh() {
        queryView.setText(query.toString());
        shown.clear();
        List<T9Index.Contact> hits = T9Index.search(all, query.toString(), MAX_RESULTS);
        // Results grow upward toward the keypad, so the best match sits closest to it.
        for (int i = hits.size() - 1; i >= 0; i--) {
            shown.add(hits.get(i));
        }
        // No contact matched but the user has typed enough to be dialling a number: offer
        // that raw number as the bottom (closest-to-keypad) row -- one tap calls it, and its
        // WhatsApp badge messages it, even though it is nobody in the contact list.
        boolean typedOffer = hits.isEmpty() && dialableQuery();
        if (typedOffer) {
            shown.add(typedNumberContact());
        }
        adapter.notifyDataSetChanged();
        refreshFrequent();
        refreshHistory();
        if (typedOffer) {
            statusView.setText("Not in contacts");
        } else if (query.length() > 0 && hits.isEmpty()) {
            statusView.setText("No match for " + query);
        } else {
            statusView.setText(all.size() + " contacts indexed");
        }
    }

    /** Enough digits typed that the query is plausibly a phone number rather than a name stub. */
    private boolean dialableQuery() {
        int digits = 0;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits++;
            }
        }
        return digits >= 3;
    }

    /** A throwaway contact (id -1) wrapping the typed number, for the offer row. */
    private T9Index.Contact typedNumberContact() {
        T9Index.Contact t = new T9Index.Contact();
        t.id = -1;
        t.name = query.toString();
        t.numbers.add(dialable(query.toString()));
        t.labels.add("");
        t.whatsapp.add(Integer.valueOf(0));
        return t;
    }

    // ------------------------------------------------------------ actions

    /** Prefers the user's saved choice, then the contact's own primary number. */
    private String resolveNumber(T9Index.Contact c) {
        String saved = Prefs.defaultNumber(this, c.id);
        if (saved != null && c.numbers.contains(saved)) {
            return saved;
        }
        return c.numbers.isEmpty() ? null : c.numbers.get(0);
    }

    private void onTap(T9Index.Contact c) {
        if (c.id < 0) {
            // The typed-number offer row: there is no contact to open, so always just dial.
            call(resolveNumber(c), -1);
            return;
        }
        if (Prefs.TAP_OPEN.equals(Prefs.tapAction(this))) {
            openContact(c.id);
            return;
        }
        // Several lines and nothing chosen yet: ask rather than guess.
        if (c.numbers.size() > 1 && Prefs.defaultNumber(this, c.id) == null) {
            showPanel(c);
            return;
        }
        call(resolveNumber(c), c.id);
    }

    private void showPanel(final T9Index.Contact c) {
        if (c.id < 0) {
            // Typed-number offer row: nothing to choose between, so long-press just dials.
            call(resolveNumber(c), -1);
            return;
        }
        if (c.numbers.isEmpty()) {
            openContact(c.id);
            return;
        }
        int checked = c.numbers.indexOf(resolveNumber(c));
        final int[] selected = {checked < 0 ? 0 : checked};
        final boolean showWa = Prefs.waInPanel(this);

        // A hand-built row list rather than setSingleChoiceItems: each line needs its own
        // tappable WhatsApp badge, which the stock single-choice adapter cannot carry.
        // Single-selection is managed by hand, mirroring the icon picker in Settings.
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));

        final RadioButton[] radios = new RadioButton[c.numbers.size()];
        for (int i = 0; i < c.numbers.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            row.setBackground(themedBackground());

            RadioButton rb = new RadioButton(this);
            rb.setChecked(i == selected[0]);
            radios[i] = rb;
            row.addView(rb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(this);
            String type = c.labels.get(i);
            label.setText(type.isEmpty()
                    ? c.numbers.get(i)
                    : c.numbers.get(i) + "   (" + type + ")");
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final int waFlags = c.waFlags(i);
            if (showWa && waFlags != 0) {
                final String waNumber = c.numbers.get(i);
                ImageView badge = new ImageView(this);
                badge.setImageResource(R.drawable.ic_whatsapp);
                int s = dp(44);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(s, s);
                bp.setMarginStart(dp(4));
                badge.setLayoutParams(bp);
                badge.setBackground(themedBackground());
                badge.setPadding(dp(9), dp(9), dp(9), dp(9));
                badge.setContentDescription("Open WhatsApp");
                badge.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        openWhatsApp(waNumber, waFlags, false);
                    }
                });
                row.addView(badge);
            }

            View.OnClickListener pick = new View.OnClickListener() {
                public void onClick(View v) {
                    selected[0] = idx;
                    for (int k = 0; k < radios.length; k++) {
                        radios[k].setChecked(k == idx);
                    }
                }
            };
            row.setOnClickListener(pick);
            rb.setOnClickListener(pick);
            list.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setView(scroll)
                .setPositiveButton("Call", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        call(c.numbers.get(selected[0]), c.id);
                    }
                })
                .setNeutralButton("Set default", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Prefs.setDefaultNumber(MainActivity.this, c.id,
                                c.numbers.get(selected[0]));
                        adapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, "Default number saved",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Open contact", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        openContact(c.id);
                    }
                })
                .show();
    }

    /** contactId < 0 for a raw typed number, which is not worth ranking. */
    private void call(String number, long contactId) {
        if (number == null || number.isEmpty()) {
            return;
        }
        pendingNumber = number;
        pendingContactId = contactId;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }
        Prefs.recordUse(this, contactId);
        startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", dialable(number), null)));
    }

    /**
     * Uri.fromParts leaves the scheme-specific part unencoded, so a stored number like
     * "+972 52-297-8262" would reach Telecom with spaces and dashes intact. Keep only
     * what a tel: URI is actually allowed to carry.
     */
    private static String dialable(String number) {
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if ((c >= '0' && c <= '9') || c == '+' || c == '*' || c == '#' || c == ',' || c == ';') {
                sb.append(c);
            }
        }
        return sb.length() > 0 ? sb.toString() : number;
    }

    private void openContact(long id) {
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] granted) {
        boolean ok = granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_CONTACTS) {
            if (ok) {
                loadContacts();
            } else {
                statusView.setText("Contacts permission denied");
            }
        } else if (req == REQ_CALL) {
            if (ok) {
                call(pendingNumber, pendingContactId);
            } else {
                Toast.makeText(this, "Call permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------------------------------ adapter

    private final class ResultAdapter extends BaseAdapter {
        public int getCount() {
            return shown.size();
        }

        public Object getItem(int i) {
            return shown.get(i);
        }

        public long getItemId(int i) {
            return shown.get(i).id;
        }

        public View getView(int i, View reuse, ViewGroup parent) {
            Row row;
            View v = reuse;
            if (v == null) {
                row = buildRow();
                v = row.root;
                v.setTag(row);
            } else {
                row = (Row) v.getTag();
            }
            final T9Index.Contact c = shown.get(i);
            final boolean typed = c.id < 0;
            String number = resolveNumber(c);
            if (!typed && c.numbers.size() > 1) {
                number = number + "   +" + (c.numbers.size() - 1) + " more";
            }
            row.name.setText(c.name);
            // The typed-number row explains its two taps; a contact row shows the number.
            row.number.setText(typed ? "Tap to call · badge for WhatsApp" : number);
            // The typed row always offers WhatsApp (wa.me works for any number, even one not
            // in contacts); a contact row shows the badge only when a line is registered.
            final boolean showBadge = typed
                    || (Prefs.waInResults(MainActivity.this) && c.hasWhatsApp());
            row.badge.setVisibility(showBadge ? View.VISIBLE : View.GONE);
            row.badge.setOnClickListener(showBadge ? new View.OnClickListener() {
                public void onClick(View x) {
                    if (typed) {
                        openWhatsApp(c.numbers.get(0), 0, true);
                    } else {
                        openContactWhatsApp(c);
                    }
                }
            } : null);
            return v;
        }
    }

    /** Recycled row view for the result list: name/number on the start, WhatsApp badge on the end. */
    private static final class Row {
        LinearLayout root;
        TextView name;
        TextView number;
        ImageView badge;
    }

    private Row buildRow() {
        Row r = new Row();
        r.root = new LinearLayout(this);
        r.root.setOrientation(LinearLayout.HORIZONTAL);
        r.root.setGravity(Gravity.CENTER_VERTICAL);
        r.root.setPadding(dp(16), dp(10), dp(12), dp(10));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        r.name = new TextView(this);
        r.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        r.number = new TextView(this);
        r.number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        r.number.setAlpha(0.7f);
        text.addView(r.name);
        text.addView(r.number);
        r.root.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        r.badge = new ImageView(this);
        r.badge.setImageResource(R.drawable.ic_whatsapp);
        // A generous 48dp square so the tap target is comfortable; the glyph stays small
        // thanks to the inner padding.
        int s = dp(48);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(s, s);
        bp.setMarginStart(dp(4));
        r.badge.setLayoutParams(bp);
        r.badge.setBackground(pillBackground());
        r.badge.setPadding(dp(11), dp(11), dp(11), dp(11));
        r.badge.setContentDescription("Open WhatsApp");
        r.root.addView(r.badge);
        return r;
    }
}
