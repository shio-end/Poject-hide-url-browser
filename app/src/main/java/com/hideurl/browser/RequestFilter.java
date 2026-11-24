package com.hideurl.browser;

import android.content.SharedPreferences;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Collections;

final class RequestFilter {
    private final SharedPreferences settings;

    RequestFilter(SharedPreferences settings) {
        this.settings = settings;
    }

    WebResourceResponse intercept(WebResourceRequest request) {
        if (!DomainFilter.blocks(request.getUrl().getHost(),
                settings.getBoolean("blockAds", true), settings.getBoolean("blockTrackers", true))) return null;
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream((request.isForMainFrame()
                    ? "Hide URL Browser blocked this domain. Change protection in Settings to open it." : "")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
