package com.hideurl.browser;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.SafeBrowsingResponse;
import android.widget.Toast;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Collections;

final class RequestFilter {
    private final SharedPreferences settings;

    private final Resources resources;
    private HostList adDomains;
    private HostList trackingDomains;

    RequestFilter(SharedPreferences settings, Resources resources) {
        this.settings = settings;
        this.resources = resources;
    }

    private synchronized HostList domains(boolean ads) {
        if (ads) {
            if (adDomains == null) adDomains = new HostList(resources.openRawResource(R.raw.ad_domains));
            return adDomains;
        }
        if (trackingDomains == null) trackingDomains = new HostList(resources.openRawResource(R.raw.tracking_domains));
        return trackingDomains;
    }

    void configure(WebView view) {
        musicRules(view).configure(view, adsEnabled());
        boolean blockWindows = settings.getBoolean("blockPopups", true);
        // With multiple windows enabled, WebChromeClient rejects new windows by default.
        view.getSettings().setSupportMultipleWindows(blockWindows);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(!blockWindows);
        view.getSettings().setSafeBrowsingEnabled(settings.getBoolean("safeBrowsing", true));
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, !settings.getBoolean("blockThirdPartyCookies", true));
    }

    private MusicProtection musicRules(WebView view) {
        MusicProtection rules = (MusicProtection) view.getTag(R.id.music_protection);
        if (rules == null) {
            rules = new MusicProtection(resources);
            view.setTag(R.id.music_protection, rules);
        }
        return rules;
    }

    boolean adsEnabled() {
        return settings.getBoolean("blockAds", true);
    }

    void stylePage(WebView view) {
        String update = adsEnabled()
                ? "if(!s){s=document.createElement('style');s.id=id;document.head.appendChild(s)}"
                    + "s.textContent='ins.adsbygoogle,iframe[id^=google_ads_iframe],"
                    + "ytmusic-promoted-sparkles-web-renderer,[data-ad-slot][data-ad-client]"
                    + "{display:none!important}'"
                : "if(s)s.remove()";
        view.evaluateJavascript("(()=>{if(!document.head)return;const id='hideurl-ad-style';"
                + "let s=document.getElementById(id);" + update + "})()", null);
    }

    class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return intercept(request);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
            musicRules(view).fallback(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            musicRules(view).fallback(view, url);
            stylePage(view);
        }

        @Override
        public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threat, SafeBrowsingResponse response) {
            response.backToSafety(false);
            if (view.isShown()) Toast.makeText(view.getContext(),
                    "บล็อกเว็บอันตรายตามรายการ Safe Browsing", Toast.LENGTH_SHORT).show();
        }
    }

    WebResourceResponse intercept(WebResourceRequest request) {
        Uri uri = request.getUrl();
        String host = uri.getHost();
        boolean ads = adsEnabled();
        boolean trackers = settings.getBoolean("blockTrackers", true);
        boolean blocked = DomainFilter.blocks(host, ads, trackers)
                || (ads && domains(true).matches(host))
                || (trackers && domains(false).matches(host));
        if (!blocked && ads && "music.youtube.com".equalsIgnoreCase(host)) {
            String path = uri.getPath();
            blocked = path != null && (path.startsWith("/pagead/") || path.equals("/api/stats/ads"));
        }
        if (!blocked && ads && host != null && (host.equals("spotify.com") || host.endsWith(".spotify.com"))) {
            String path = uri.getPath();
            blocked = path != null && path.startsWith("/ad-logic/");
        }
        if (!blocked) return null;
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream((request.isForMainFrame()
                    ? "Hide URL Browser blocked this domain. Change protection in Settings to open it." : "")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
