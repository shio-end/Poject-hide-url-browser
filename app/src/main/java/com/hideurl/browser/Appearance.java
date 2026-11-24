package com.hideurl.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

final class Appearance {
    static final int SYSTEM = 0, LIGHT = 1, DARK = 2, WALLPAPER = 3;
    static final String[] MODES = {"ตามระบบ", "สว่าง", "มืด", "สีจากรูปพื้นหลัง"};
    final int mode, paper, surface, ink, muted, line, accent;
    final boolean dark;

    Appearance(Context context) {
        SharedPreferences p = context.getSharedPreferences("browser", Context.MODE_PRIVATE);
        int preferred = p.getInt("themeMode", SYSTEM);
        mode = preferred == WALLPAPER && !p.contains("wallpaper") ? SYSTEM : preferred;
        int seed = p.getInt("wallColor", Color.GRAY);
        boolean systemDark = (context.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = mode == DARK || (mode == SYSTEM && systemDark) ||
                (mode == WALLPAPER && Color.luminance(seed) < 0.35);
        paper = tint(dark ? 0xff151515 : 0xfff0f0f0, seed, mode == WALLPAPER ? .18f : 0);
        surface = tint(dark ? 0xff262626 : Color.WHITE, seed, mode == WALLPAPER ? .12f : 0);
        ink = dark ? 0xfffafafa : 0xff141414;
        muted = dark ? 0xffc1c1c1 : 0xff505050;
        line = tint(paper, ink, .19f);
        accent = mode == WALLPAPER ? tint(ink, seed, .36f) : ink;
    }

    static int tint(int base, int color, float part) {
        return Color.rgb(Math.round(Color.red(base)*(1-part)+Color.red(color)*part),
                Math.round(Color.green(base)*(1-part)+Color.green(color)*part),
                Math.round(Color.blue(base)*(1-part)+Color.blue(color)*part));
    }

    int dialogTheme() {
        return dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }
}
