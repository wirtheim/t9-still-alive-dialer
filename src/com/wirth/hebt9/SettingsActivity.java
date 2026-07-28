package com.wirth.hebt9;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** The management panel: appearance, launcher icon, tap behaviour, saved defaults. */
public class SettingsActivity extends Activity {

    private static final int REQ_IMPORT = 10;

    private String appliedTheme;
    private TextView defaultsSummary;
    private TextView usageSummary;
    private TextView searchesSummary;
    private LinearLayout layoutGroupHolder;

    @Override
    protected void onCreate(Bundle state) {
        appliedTheme = Prefs.theme(this);
        setTheme(Prefs.themeRes(this));
        super.onCreate(state);
        if (getActionBar() != null) {
            getActionBar().setTitle("Settings");
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setContentView(buildUi());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private View buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(28));

        LinearLayout appearance = card("Appearance",
                "Choose light, dark, or follow the system theme.");
        appearance.addView(themePicker());
        col.addView(appearance);

        LinearLayout icon = card("App icon",
                "Pick the launcher icon and name the app shows. After a change the launcher "
                + "may take a moment to refresh.");
        icon.addView(iconPicker());
        col.addView(icon);

        LinearLayout tap = card("Tapping a result",
                "What a single tap on a contact does: call its default number, or open the "
                + "contact card. A long-press always opens the number picker.");
        tap.addView(tapPicker());
        col.addView(tap);

        LinearLayout wa = card("WhatsApp",
                "Marks contact numbers that are on WhatsApp and lets you open a chat from the "
                + "green badge. Detection needs the number saved as a contact and WhatsApp's "
                + "contact sync enabled; nothing leaves the device. The badge also appears for "
                + "a number you type that is not in your contacts.");
        wa.addView(whatsAppPicker());
        col.addView(wa);

        LinearLayout haptics = card("Key vibration",
                "A short buzz on each key press. Drag the slider to set the strength; all the "
                + "way to the left turns it off.");
        haptics.addView(hapticPicker());
        col.addView(haptics);

        LinearLayout defaults = card("Default numbers",
                "When a contact has more than one number, long-press it to pick which line to "
                + "use and save it as the default.");
        defaultsSummary = note("");
        defaults.addView(defaultsSummary);
        defaults.addView(button("Clear saved defaults", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.clearDefaults(SettingsActivity.this);
                refreshDefaultsSummary();
                Toast.makeText(SettingsActivity.this, "Saved defaults cleared",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        refreshDefaultsSummary();
        col.addView(defaults);

        LinearLayout freq = card("Frequent contacts",
                "The tiles above the keypad are ranked by how often you call each contact from "
                + "this app. The tally is local and never leaves the device.");
        usageSummary = note("");
        freq.addView(usageSummary);
        freq.addView(button("Reset usage stats", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.clearUsage(SettingsActivity.this);
                refreshUsageSummary();
                Toast.makeText(SettingsActivity.this, "Usage stats reset",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        refreshUsageSummary();
        col.addView(freq);

        LinearLayout recent = card("Recent searches",
                "Numbers you type appear as tiles under the frequent contacts, newest first. "
                + "Tap one to search it again. They are stored only on this device and reset "
                + "each time you leave the dialer.");
        searchesSummary = note("");
        recent.addView(searchesSummary);
        recent.addView(button("Clear recent searches", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.clearSearches(SettingsActivity.this);
                refreshSearchesSummary();
                Toast.makeText(SettingsActivity.this, "Recent searches cleared",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        refreshSearchesSummary();
        col.addView(recent);

        LinearLayout layout = card("Keypad layout",
                "Only Hebrew ships with the app. Other scripts are plain .t9 text files you "
                + "import here -- no new build needed. Sample layouts live in the project "
                + "repository. Long-press an imported layout to remove it.");
        layoutGroupHolder = new LinearLayout(this);
        layoutGroupHolder.setOrientation(LinearLayout.VERTICAL);
        layout.addView(layoutGroupHolder);
        buildLayoutPicker();
        layout.addView(button("Import layout file...", new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                startActivityForResult(pick, REQ_IMPORT);
            }
        }));
        col.addView(layout);

        LinearLayout about = card("About", null);
        about.addView(aboutBlock());
        col.addView(about);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        Ui.fitSystemBars(this, scroll);
        return scroll;
    }

    // ------------------------------------------------------------- sections

    private View themePicker() {
        final String[] values = {Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK};
        String[] labels = {"Follow system", "Light", "Dark"};
        RadioGroup group = new RadioGroup(this);
        String current = Prefs.theme(this);
        for (int i = 0; i < values.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(labels[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb.setPadding(dp(8), dp(10), 0, dp(10));
            rb.setTag(values[i]);
            group.addView(rb);
            if (values[i].equals(current)) {
                group.check(rb.getId());
            }
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                View v = g.findViewById(id);
                if (v == null) {
                    return;
                }
                String value = (String) v.getTag();
                if (!value.equals(Prefs.theme(SettingsActivity.this))) {
                    Prefs.setTheme(SettingsActivity.this, value);
                    recreate();
                }
            }
        });
        return group;
    }

    private View iconPicker() {
        RadioGroup group = new RadioGroup(this);
        final int current = Prefs.icon(this);
        for (int i = 0; i < Prefs.ICON_ALIASES.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView preview = new ImageView(this);
            preview.setImageResource(Prefs.ICON_DRAWABLES[i]);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
            ip.setMargins(0, dp(6), dp(14), dp(6));
            row.addView(preview, ip);

            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(Prefs.ICON_NAMES[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb.setTag(Integer.valueOf(i));
            row.addView(rb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            group.addView(row);
            if (i == current) {
                // The button lives inside a row, so RadioGroup will not auto-manage it.
                rb.setChecked(true);
            }
            wireIconButton(group, rb);
        }
        return group;
    }

    /**
     * RadioGroup only auto-unchecks direct children, and these buttons sit inside rows
     * so they can show a preview. Exclusivity is therefore done by hand.
     */
    private void wireIconButton(final RadioGroup group, final RadioButton button) {
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int r = 0; r < group.getChildCount(); r++) {
                    View row = group.getChildAt(r);
                    if (row instanceof ViewGroup) {
                        for (int k = 0; k < ((ViewGroup) row).getChildCount(); k++) {
                            View child = ((ViewGroup) row).getChildAt(k);
                            if (child instanceof RadioButton) {
                                ((RadioButton) child).setChecked(child == button);
                            }
                        }
                    }
                }
                int index = ((Integer) button.getTag()).intValue();
                Prefs.applyIcon(SettingsActivity.this, index);
                Toast.makeText(SettingsActivity.this,
                        "Icon changed -- the launcher may take a moment to refresh",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private View tapPicker() {
        final String[] values = {Prefs.TAP_CALL, Prefs.TAP_OPEN};
        String[] labels = {"Call the default number", "Open the contact"};
        RadioGroup group = new RadioGroup(this);
        String current = Prefs.tapAction(this);
        for (int i = 0; i < values.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(labels[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb.setPadding(dp(8), dp(10), 0, dp(10));
            rb.setTag(values[i]);
            group.addView(rb);
            if (values[i].equals(current)) {
                group.check(rb.getId());
            }
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                View v = g.findViewById(id);
                if (v != null) {
                    Prefs.setTapAction(SettingsActivity.this, (String) v.getTag());
                }
            }
        });
        return group;
    }

    /**
     * A master switch plus three placement toggles. The placements read their raw stored
     * value (not the master-ANDed getter) so the panel shows what each would be if the
     * master were on, and they grey out while the master is off.
     */
    private View whatsAppPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        final CheckBox results = checkRow("Search results", Prefs.waResultsRaw(this));
        final CheckBox tiles = checkRow("Frequent contact tiles", Prefs.waTilesRaw(this));
        final CheckBox panel = checkRow("Number chooser", Prefs.waPanelRaw(this));

        CheckBox master = checkRow("Show WhatsApp badges", Prefs.waEnabled(this));
        master.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setWaEnabled(SettingsActivity.this, on);
                results.setEnabled(on);
                tiles.setEnabled(on);
                panel.setEnabled(on);
            }
        });
        results.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setWaInResults(SettingsActivity.this, on);
            }
        });
        tiles.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setWaInTiles(SettingsActivity.this, on);
            }
        });
        panel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setWaInPanel(SettingsActivity.this, on);
            }
        });

        boolean on = Prefs.waEnabled(this);
        results.setEnabled(on);
        tiles.setEnabled(on);
        panel.setEnabled(on);

        box.addView(master);
        // The three placements sit indented under the master switch.
        LinearLayout indent = new LinearLayout(this);
        indent.setOrientation(LinearLayout.VERTICAL);
        indent.setPadding(dp(20), 0, 0, 0);
        indent.addView(results);
        indent.addView(tiles);
        indent.addView(panel);
        box.addView(indent);

        // Which WhatsApp app a badge tap uses. "Ask" offers whatever is installed (so with
        // both apps present the tap shows a two-item chooser -- the default).
        box.addView(subLabel("When opening a chat"));
        final String[] appValues = {Prefs.WA_APP_ASK, Prefs.WA_APP_STD, Prefs.WA_APP_BIZ};
        String[] appLabels = {"Ask when both are installed", "Always WhatsApp",
                "Always WhatsApp Business"};
        RadioGroup appGroup = new RadioGroup(this);
        String currentApp = Prefs.waApp(this);
        for (int i = 0; i < appValues.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(appLabels[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb.setPadding(dp(8), dp(8), 0, dp(8));
            rb.setTag(appValues[i]);
            appGroup.addView(rb);
            if (appValues[i].equals(currentApp)) {
                appGroup.check(rb.getId());
            }
        }
        appGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                View v = g.findViewById(id);
                if (v != null) {
                    Prefs.setWaApp(SettingsActivity.this, (String) v.getTag());
                }
            }
        });
        box.addView(appGroup);
        return box;
    }

    private View hapticPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        final TextView value = note("");
        final SeekBar bar = new SeekBar(this);
        bar.setMax(Prefs.HAPTIC_MAX);
        bar.setProgress(Prefs.haptic(this));
        value.setText(hapticLabel(bar.getProgress()));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                value.setText(hapticLabel(p));
            }
            public void onStartTrackingTouch(SeekBar sb) {
            }
            public void onStopTrackingTouch(SeekBar sb) {
                Prefs.setHaptic(SettingsActivity.this, sb.getProgress());
                previewHaptic(sb.getProgress());
            }
        });
        box.addView(value);
        box.addView(bar);
        return box;
    }

    private String hapticLabel(int amplitude) {
        if (amplitude <= Prefs.HAPTIC_OFF) {
            return "Off";
        }
        return "Strength " + Math.round(amplitude * 100f / Prefs.HAPTIC_MAX) + "%";
    }

    private void previewHaptic(int amplitude) {
        if (amplitude <= Prefs.HAPTIC_OFF) {
            return;
        }
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(18, Math.min(255, amplitude)));
            }
        } catch (Exception e) {
            // preview only; ignore
        }
    }

    private CheckBox checkRow(String label, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cb.setPadding(dp(8), dp(10), 0, dp(10));
        cb.setChecked(checked);
        return cb;
    }

    // -------------------------------------------------------------- layouts

    private void buildLayoutPicker() {
        layoutGroupHolder.removeAllViews();
        final List<String> names = Layouts.names(this);
        String current = Prefs.layoutName(this);
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(Layouts.BUILT_IN.equals(name) ? name + "  (built in)" : name);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb.setPadding(dp(8), dp(10), 0, dp(10));
            rb.setTag(name);
            if (!Layouts.BUILT_IN.equals(name)) {
                rb.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        confirmDelete(name);
                        return true;
                    }
                });
            }
            group.addView(rb);
            if (name.equals(current)) {
                group.check(rb.getId());
            }
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                View v = g.findViewById(id);
                if (v != null) {
                    Prefs.setLayoutName(SettingsActivity.this, (String) v.getTag());
                }
            }
        });
        layoutGroupHolder.addView(group);
    }

    private void confirmDelete(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("Remove this layout?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Layouts.delete(SettingsActivity.this, name);
                        if (name.equals(Prefs.layoutName(SettingsActivity.this))) {
                            Prefs.setLayoutName(SettingsActivity.this, Layouts.BUILT_IN);
                        }
                        buildLayoutPicker();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_IMPORT || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("cannot open");
            }
            String stored = Layouts.importFrom(this, in, "Imported");
            if (stored == null) {
                Toast.makeText(this, "No key definitions found in that file",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.setLayoutName(this, stored);
            buildLayoutPicker();
            Toast.makeText(this, "Imported and selected: " + stored, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- about

    private View aboutBlock() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
        lp.setMargins(0, dp(4), dp(16), dp(4));
        box.addView(logo, lp);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        text.addView(title);

        TextView version = new TextView(this);
        version.setText("v" + versionName() + "   ·   W-are-theim");
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        version.setAlpha(0.75f);
        text.addView(version);

        box.addView(text);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(box);
        col.addView(note("Hebrew T9 search for contacts, with Samsung's dialer left "
                + "untouched -- call recording and the call log keep working."));
        col.addView(note("Free to use. Licensed CC BY-NC-ND 4.0: no modified versions, "
                + "no commercial use, attribution required."));
        return col;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void refreshUsageSummary() {
        int n = Prefs.usageCount(this);
        usageSummary.setText(n == 0
                ? "Nothing called from the app yet."
                : n + " contact(s) tracked.");
    }

    private void refreshDefaultsSummary() {
        int n = Prefs.defaultsCount(this);
        defaultsSummary.setText(n == 0
                ? "No saved defaults yet."
                : n + " contact(s) have a saved default number.");
    }

    private void refreshSearchesSummary() {
        int n = Prefs.searchesCount(this);
        searchesSummary.setText(n == 0
                ? "No recent searches yet."
                : n + " recent search(es) stored.");
    }

    // ---------------------------------------------------------------- cards

    /**
     * A rounded surface card carrying a section. Its title row ends in a small "?" that pops
     * the explanation, keeping the panel itself uncluttered. Pass null help for no button.
     */
    private LinearLayout card(String title, String help) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(cardColor());
        bg.setStroke(dp(1), strokeColor());
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(lp);
        card.addView(titleRow(title, help));
        return card;
    }

    private View titleRow(String title, String help) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(4));

        TextView t = new TextView(this);
        t.setText(title.toUpperCase());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setLetterSpacing(0.08f);
        t.setTextColor(accent());
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (help != null) {
            row.addView(helpButton(title, help));
        }
        return row;
    }

    private View helpButton(final String title, final String help) {
        TextView q = new TextView(this);
        q.setText("?");
        q.setGravity(Gravity.CENTER);
        q.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        q.setTextColor(accent());
        q.setWidth(dp(28));
        q.setHeight(dp(28));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(1), accent());
        q.setBackground(ring);
        q.setClickable(true);
        q.setFocusable(true);
        q.setContentDescription("Help: " + title);
        q.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(title)
                        .setMessage(help)
                        .setPositiveButton("Got it", null)
                        .show();
            }
        });
        return q;
    }

    /** Slightly elevated surface for a card, subtle on both themes. */
    private int cardColor() {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.colorBackgroundFloating, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        int base = resolveColor(android.R.attr.textColorSecondary, Color.GRAY);
        return Color.argb(16, Color.red(base), Color.green(base), Color.blue(base));
    }

    /** A subtle outline colour that reads on both themes. */
    private int strokeColor() {
        int base = resolveColor(android.R.attr.textColorSecondary, Color.GRAY);
        return Color.argb(70, Color.red(base), Color.green(base), Color.blue(base));
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return getResources().getColor(tv.resourceId, getTheme());
            }
            return tv.data;
        }
        return fallback;
    }

    private TextView subLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setAllCaps(false);
        t.setAlpha(0.6f);
        t.setPadding(dp(2), dp(12), 0, dp(2));
        return t;
    }

    // -------------------------------------------------------------- widgets

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(2), 0, dp(8));
        t.setAlpha(0.7f);
        return t;
    }

    private View button(String label, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setClickable(true);
        t.setFocusable(true);
        t.setTextColor(accent());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), accent());
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, dp(2));
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        return t;
    }

    /** Follows the One UI accent so the panel matches whichever theme is active. */
    private int accent() {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            return tv.data;
        }
        return Color.parseColor("#5B4BE8");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!appliedTheme.equals(Prefs.theme(this))) {
            recreate();
        }
    }
}
