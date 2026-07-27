package com.wirth.hebt9;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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
        col.setPadding(dp(20), dp(12), dp(20), dp(28));

        col.addView(header("Appearance"));
        col.addView(themePicker());

        col.addView(header("App icon"));
        col.addView(iconPicker());

        col.addView(header("Tapping a result"));
        col.addView(tapPicker());

        col.addView(header("Default numbers"));
        col.addView(note("When a contact has more than one number, long-press it to pick "
                + "which line to use and save it as the default."));
        defaultsSummary = note("");
        col.addView(defaultsSummary);
        col.addView(button("Clear saved defaults", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.clearDefaults(SettingsActivity.this);
                refreshDefaultsSummary();
                Toast.makeText(SettingsActivity.this, "Saved defaults cleared",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        refreshDefaultsSummary();

        col.addView(header("Frequent contacts"));
        col.addView(note("The tiles above the keypad are ranked by how often you call each "
                + "contact from this app. The tally is local and never leaves the device."));
        usageSummary = note("");
        col.addView(usageSummary);
        col.addView(button("Reset usage stats", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.clearUsage(SettingsActivity.this);
                refreshUsageSummary();
                Toast.makeText(SettingsActivity.this, "Usage stats reset",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        refreshUsageSummary();

        col.addView(header("Keypad layout"));
        col.addView(note("Only Hebrew ships with the app. Other scripts are plain .t9 text "
                + "files you import here -- no new build needed. Sample layouts live in the "
                + "project repository."));
        layoutGroupHolder = new LinearLayout(this);
        layoutGroupHolder.setOrientation(LinearLayout.VERTICAL);
        col.addView(layoutGroupHolder);
        buildLayoutPicker();
        col.addView(button("Import layout file...", new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                startActivityForResult(pick, REQ_IMPORT);
            }
        }));

        col.addView(header("About"));
        col.addView(aboutBlock());

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
        if (names.size() > 1) {
            layoutGroupHolder.addView(note("Long-press an imported layout to remove it."));
        }
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

    // -------------------------------------------------------------- widgets

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(22), 0, dp(6));
        t.setTextColor(accent());
        return t;
    }

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
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setClickable(true);
        t.setFocusable(true);
        t.setTextColor(accent());
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        t.setBackground(getDrawable(tv.resourceId));
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
