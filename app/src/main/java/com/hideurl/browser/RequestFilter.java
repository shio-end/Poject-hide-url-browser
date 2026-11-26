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
        view.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        musicRules(view).configure(view, adsEnabled(), !paused("music.youtube.com"), !paused("open.spotify.com"));
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
        String update = adsEnabled() && !paused(host(view.getUrl()))
                ? "if(!s){s=document.createElement('style');s.id=id;document.head.appendChild(s)}"
                    + "s.textContent='ins.adsbygoogle,iframe[id^=google_ads_iframe],"
                    + "ytmusic-promoted-sparkles-web-renderer,[data-ad-slot][data-ad-client]"
                    + "{display:none!important}'"
                : "if(s)s.remove()";
        view.evaluateJavascript("(()=>{if(!document.head)return;const id='hideurl-ad-style';"
                + "let s=document.getElementById(id);" + update + "})()", null);
    }

    private final java.util.LinkedHashMap<String,java.util.LinkedHashSet<String>> recent = new java.util.LinkedHashMap<>();

    static String host(String url) {
        String name = url == null ? null : Uri.parse(url).getHost();
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }

    boolean paused(String host) { return settings.getBoolean("pause:" + host, false); }

    void panel(android.app.Activity activity, String host, Runnable refresh) {
        if (host.isEmpty()) return;
        Sheets sheet = new Sheets(activity, host);
        sheet.row(sheet.action(paused(host) ? "เปิดตัวกรองโฆษณาและตัวติดตาม" : "พักตัวกรองโฆษณาและตัวติดตาม", () -> {
            settings.edit().putBoolean("pause:" + host, !paused(host)).apply();
            sheet.dialog.dismiss();
            refresh.run();
        }));
        sheet.section("โดเมนที่บล็อกล่าสุด");
        String detail;
        synchronized (recent) {
            detail = recent.containsKey(host) ? android.text.TextUtils.join("\n", recent.get(host)) : "ยังไม่มีรายการ";
        }
        sheet.row(sheet.label(detail, 14, false));
        sheet.dialog.show();
    }

    class Client extends WebViewClient {
        private volatile String pageHost = "";
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return intercept(request, request.isForMainFrame() ? host(request.getUrl().toString()) : pageHost);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
            pageHost = host(url);
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
        String source = request.getRequestHeaders().get("Referer");
        if (source == null) source = request.getRequestHeaders().get("Origin");
        return intercept(request, host(source));
    }

    private WebResourceResponse intercept(WebResourceRequest request, String site) {
        if (paused(site)) return null;
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
        if (!site.isEmpty() && host != null) synchronized (recent) {
            if (!recent.containsKey(site) && recent.size() >= 64) recent.remove(recent.keySet().iterator().next());
            java.util.LinkedHashSet<String> domains = recent.computeIfAbsent(site, key -> new java.util.LinkedHashSet<>());
            domains.remove(host);
            domains.add(host);
            if (domains.size() > 20) domains.remove(domains.iterator().next());
        }
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream((request.isForMainFrame()
                    ? "VEIL blocked this domain. Change protection in Settings to open it." : "")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
