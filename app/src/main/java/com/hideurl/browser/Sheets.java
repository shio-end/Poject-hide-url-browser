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
    final Appearance theme;

    Sheets(Activity activity, String title) {
        this(activity, title, false);
    }

    Sheets(Activity activity, String title, boolean centered) {
        this.activity = activity;
        theme = new Appearance(activity);
        dialog = new Dialog(activity, theme.dialogTheme());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = column();
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                (theme.surface & 0xffffff) | 0xee000000,
                (theme.paper & 0xffffff) | 0xcb000000,
                (Appearance.tint(theme.surface, theme.accent, .12f) & 0xffffff) | 0xe3000000});
        glass.setCornerRadius(dp(28));
        glass.setStroke(dp(1), theme.dark ? 0x66ffffff : 0xc0ffffff);
        card.setBackground(glass);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        View grip = new View(activity);
        LinearLayout.LayoutParams handle = new LinearLayout.LayoutParams(dp(36), dp(4));
        handle.gravity = Gravity.CENTER_HORIZONTAL;
        handle.bottomMargin = dp(12);
        grip.setBackground(surface(theme.line, 2));
        if (!centered) card.addView(grip, handle);
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
        window.setBackgroundDrawable(surface(Color.TRANSPARENT, 28));
        window.setBackgroundBlurRadius(dp(32));
        window.setDimAmount(.36f);
        window.setGravity(centered ? Gravity.CENTER : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(24), dp(centered ? 440 : 520)), -2);
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
        t.setTextColor(theme.ink);
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
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40888888),
                surface((theme.surface & 0xffffff) | 0xbd000000, 16), null));
        view.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(activity, R.animator.button_press));
        view.setFocusable(true);
        TapSound.bind(view, click);
    }

    void section(String name) {
        TextView title = label(name, 14, true);
        title.setTextColor(theme.muted);
        title.setPadding(dp(4), dp(16), 0, dp(6));
        body.addView(title);
    }

    void row(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(4);
        body.addView(view, params);
    }
}
