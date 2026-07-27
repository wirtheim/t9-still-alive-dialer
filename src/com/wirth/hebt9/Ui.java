package com.wirth.hebt9;

import android.app.Activity;
import android.graphics.Insets;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;

/** Shared window-inset handling. */
public final class Ui {

    private Ui() {
    }

    /**
     * Keeps content clear of the system bars AND the ActionBar.
     *
     * targetSdk 35+ forces edge-to-edge: the content view is laid out from y=0, with the
     * status bar and the ActionBar drawn on top of it, and the navigation bar over its
     * bottom. Without this the first row hides behind the title and the last row hides
     * behind the nav buttons.
     */
    public static void fitSystemBars(final Activity activity, View root) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top + actionBarHeight(activity),
                        bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            }
        });
    }

    private static int actionBarHeight(Activity activity) {
        if (activity.getActionBar() == null) {
            return 0;
        }
        TypedValue tv = new TypedValue();
        if (!activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return 0;
        }
        return TypedValue.complexToDimensionPixelSize(
                tv.data, activity.getResources().getDisplayMetrics());
    }
}
