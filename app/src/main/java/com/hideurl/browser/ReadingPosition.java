package com.hideurl.browser;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;

final class ReadingPosition {
    private final WebView web;
    private final SharedPreferences prefs;
    private String page;
    private int target;
    private long deadline;
    private boolean touched;
    private final Runnable retry = this::restore;
    private final Runnable save = this::flush;

    ReadingPosition(WebView web, SharedPreferences prefs) {
        this.web = web;
        this.prefs = prefs;
        web.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                web.removeCallbacks(retry);
                touched = true;
            }
            return false;
        });
        web.setOnScrollChangeListener((v,x,y,oldX,oldY) -> {
            if (!touched || page == null || !page.equals(web.getUrl())) return;
            web.removeCallbacks(save);
            web.postDelayed(save, 150);
            // Keep the old page's last observed position through navigation.
            lastY = y;
            dirty = true;
        });
    }

    private int lastY;
    private boolean dirty;

    void flush() {
        web.removeCallbacks(save);
        if (dirty && page != null) prefs.edit().putInt(page, lastY).apply();
        dirty = false;
    }

    void start(String url) {
        flush();
        web.removeCallbacks(retry);
        target = 0;
        page = null;
        navigate(url);
    }

    void navigate(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        if (url.equals(page)) return;
        flush();
        web.removeCallbacks(retry);
        page = url;
        touched = false;
        target = prefs.getInt(url, 0);
        deadline = SystemClock.uptimeMillis() + 12000;
        if (target > 0) web.postDelayed(retry, 120);
    }

    void restore() {
        web.removeCallbacks(retry);
        if (target <= 0 || page == null || touched || !page.equals(web.getUrl())) return;
        web.scrollTo(web.getScrollX(), target);
        if (web.getScrollY() < target && SystemClock.uptimeMillis() < deadline) web.postDelayed(retry, 250);
    }

    void close() {
        flush();
        web.removeCallbacks(retry);
    }
}
