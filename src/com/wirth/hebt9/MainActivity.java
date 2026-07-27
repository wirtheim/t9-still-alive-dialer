package com.wirth.hebt9;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
        // Defaults or usage stats may have been cleared in the panel while we were away.
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            refreshFrequent();
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
        queryView.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(queryView);

        frequentRow = new LinearLayout(this);
        frequentRow.setOrientation(LinearLayout.HORIZONTAL);
        frequentRow.setPadding(dp(10), 0, dp(10), dp(6));
        frequentScroll = new HorizontalScrollView(this);
        frequentScroll.setHorizontalScrollBarEnabled(false);
        frequentScroll.addView(frequentRow);
        root.addView(frequentScroll);

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
        cell.setBackground(themedBackground());

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
                query.setLength(0);
                refresh();
            }
        }, null));

        bar.addView(actionButton("Call", new View.OnClickListener() {
            public void onClick(View v) {
                if (!shown.isEmpty()) {
                    onTap(shown.get(shown.size() - 1));
                } else if (query.length() > 0) {
                    call(query.toString(), -1);
                }
            }
        }, null));

        bar.addView(actionButton("Del", new View.OnClickListener() {
            public void onClick(View v) {
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(66), dp(66));
        lp.setMargins(dp(4), 0, dp(4), 0);
        tile.setLayoutParams(lp);
        return tile;
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

    private List<T9Index.Contact> readContacts() {
        List<T9Index.Contact> out = new ArrayList<T9Index.Contact>();
        Map<Long, T9Index.Contact> byId = new HashMap<Long, T9Index.Contact>();
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

    private void refresh() {
        queryView.setText(query.toString());
        shown.clear();
        List<T9Index.Contact> hits = T9Index.search(all, query.toString(), MAX_RESULTS);
        // Results grow upward toward the keypad, so the best match sits closest to it.
        for (int i = hits.size() - 1; i >= 0; i--) {
            shown.add(hits.get(i));
        }
        adapter.notifyDataSetChanged();
        refreshFrequent();
        if (query.length() > 0 && shown.isEmpty()) {
            statusView.setText("No match for " + query);
        } else {
            statusView.setText(all.size() + " contacts indexed");
        }
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
        if (c.numbers.isEmpty()) {
            openContact(c.id);
            return;
        }
        final String[] items = new String[c.numbers.size()];
        for (int i = 0; i < items.length; i++) {
            String label = c.labels.get(i);
            items[i] = label.isEmpty()
                    ? c.numbers.get(i)
                    : c.numbers.get(i) + "   (" + label + ")";
        }
        int checked = c.numbers.indexOf(resolveNumber(c));
        final int[] selected = {checked < 0 ? 0 : checked};

        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setSingleChoiceItems(items, selected[0], new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        selected[0] = which;
                    }
                })
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
            View v = reuse;
            if (v == null) {
                v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            T9Index.Contact c = shown.get(i);
            String number = resolveNumber(c);
            if (c.numbers.size() > 1) {
                number = number + "   +" + (c.numbers.size() - 1) + " more";
            }
            ((TextView) v.findViewById(android.R.id.text1)).setText(c.name);
            ((TextView) v.findViewById(android.R.id.text2)).setText(number);
            return v;
        }
    }
}
