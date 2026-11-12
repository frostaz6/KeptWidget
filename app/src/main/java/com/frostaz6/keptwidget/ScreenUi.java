package com.frostaz6.keptwidget;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ScreenUi {
    static final int TEXT = 0xffeceee9;
    static final int MUTED = 0xffaab5aa;
    static final int GREEN = 0xff4c7d46;

    private ScreenUi() {}

    static int dp(Context c, int value) {
        return Math.round(c.getResources().getDisplayMetrics().density * value);
    }

    static LinearLayout page(Activity activity, String title) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff181b1b);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 30));
        scroll.addView(root);
        activity.setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(dp(activity, 22),
                dp(activity, 22) + insets.getSystemWindowInsetTop(),
                dp(activity, 22),
                dp(activity, 30) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView heading = label(activity, title, 28, true);
        root.addView(heading, margins(activity, 0, 0, 0, 18));
        return root;
    }

    static TextView label(Context c, String text, int size, boolean bold) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(TEXT);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    static TextView hint(Context c, String text) {
        TextView view = label(c, text, 13, false);
        view.setTextColor(MUTED);
        return view;
    }

    static EditText field(Context c, String hint, int inputType) {
        EditText edit = new EditText(c);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setInputType(inputType);
        edit.setSelectAllOnFocus(false);
        return edit;
    }

    static Button button(Context c, String title, boolean primary) {
        Button button = new Button(c);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? GREEN : 0xff303735);
        bg.setCornerRadius(dp(c, 13));
        button.setBackground(bg);
        button.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return button;
    }

    static LinearLayout.LayoutParams margins(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c,l), dp(c,t), dp(c,r), dp(c,b));
        return lp;
    }
}
