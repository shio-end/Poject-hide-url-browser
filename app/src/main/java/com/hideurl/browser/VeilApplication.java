package com.hideurl.browser;

import android.app.Application;
import android.content.Context;
import android.webkit.WebView;

public final class VeilApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if ((base.getPackageName() + ":http").equals(Application.getProcessName())) {
            WebView.setDataDirectorySuffix("http_tabs");
        }
    }
}
