package com.hideurl.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Sheets {
    final Activity activity;
    final Dialog dialog;
    final LinearLayout body;
    final LinearLayout header;
    static final int INK = 0xff26231e;
    static final int MUTED = 0xff635e54;

    Sheets(Activity activity, String title) {
        this.activity = activity;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = column();
        card.setBackground(surface(0xfffaf7ef, 24));
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 23, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(action("ปิด", () -> dialog.dismiss()));
        card.addView(header);
        ScrollView scroll = new ScrollView(activity);
        body = column();
        scroll.addView(body);
        card.addView(scroll);
        dialog.setContentView(card);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels, dp(520)), -2);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }

    LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    GradientDrawable surface(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    TextView label(String text, int size, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(INK);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    TextView action(String name, Runnable click) {
        TextView button = label(name, 15, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        clickable(button, click);
        return button;
    }

    void clickable(View view, Runnable click) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x307c693c),
                surface(0xfffffdf8, 14), null));
        view.setFocusable(true);
        view.setOnClickListener(v -> click.run());
    }

    void section(String name) {
        TextView title = label(name, 14, true);
        title.setTextColor(MUTED);
        title.setPadding(dp(4), dp(16), 0, dp(6));
        body.addView(title);
    }

    void row(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(4);
        body.addView(view, params);
    }
}
