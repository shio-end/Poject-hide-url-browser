package com.hideurl.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;

public final class HttpTab extends Activity {
    private static HttpTab active;
    private WebView web;
    private String origin;
    private TextView status;
    private LinearLayout layout;
    private Dialog warning;
    private String pendingUrl;
    private boolean routing;
    private BroadcastReceiver proxyChanges;
    private ResultReceiver history;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        String url = state == null ? getIntent().getDataString() : state.getString("page", getIntent().getDataString());
        if (HttpOrigin.of(url) == null || (active != null && active != this && !active.isDestroyed())) {
            finish();
            return;
        }
        active = this;
        history = getIntent().getParcelableExtra("history");
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tools = new LinearLayout(this);
        layout.addView(tools);
        Button close = new Button(this);
        close.setText("ปิด");
        close.setOnClickListener(v -> finish());
        tools.addView(close);
        status = new TextView(this);
        status.setText("แท็บ HTTP");
        tools.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        setContentView(layout);
        proxyChanges = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (isInitialStickyBroadcast() || web == null) return;
                String last = web.getUrl();
                destroyWebView();
                origin = null;
                status.setText("พร็อกซีของเครื่องเปลี่ยนแล้ว");
                if (warning == null && HttpOrigin.of(last) != null) confirm(last);
            }
        };
        IntentFilter changes = new IntentFilter(android.net.Proxy.PROXY_CHANGE_ACTION);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(proxyChanges, changes, RECEIVER_NOT_EXPORTED);
        else registerReceiver(proxyChanges, changes);
        confirm(url);
    }

    private void confirm(String url) {
        String target = HttpOrigin.of(url);
        if (target == null || warning != null) return;
        pendingUrl = url;
        Sheets sheet = new Sheets(this, "จะเปิดเว็บ HTTP ที่ไม่เข้ารหัสไหม", true);
        warning = sheet.dialog;
        sheet.dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        sheet.dialog.setOnDismissListener(ignored -> {
            warning = null;
            pendingUrl = null;
            if (origin == null && !isFinishing()) finish();
        });
        sheet.row(sheet.label(target, 18, true));
        sheet.row(sheet.label("ผู้ที่อยู่บนเครือข่ายอาจอ่านหรือแก้ไขข้อมูลที่รับส่ง รวมถึงรหัสผ่าน กรุณาตรวจชื่อเว็บและพอร์ต การยืนยันใช้ได้เฉพาะครั้งนี้", 16, false));
        sheet.row(sheet.action("ยกเลิก", sheet.dialog::dismiss));
        sheet.row(sheet.action("ลอง HTTPS ใน VEIL", () -> {
            returnLink(Uri.parse(url).buildUpon().scheme("https").build().toString());
            sheet.dialog.dismiss();
        }));
        View accept = sheet.action("ยอมรับความเสี่ยงและเปิดใน VEIL", () -> {
            origin = target;
            sheet.dialog.dismiss();
            openApproved(url);
        });
        accept.setFilterTouchesWhenObscured(true);
        sheet.row(accept);
        sheet.dialog.show();
    }

    private void openApproved(String url) {
        destroyWebView();
        status.setText("ไม่เข้ารหัส · " + origin);
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            status.setText("กรุณาอัปเดต Android System WebView เพื่อเปิดแท็บ HTTP");
            return;
        }
        final WebView tab = new WebView(this);
        web = tab;
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        RequestFilter filter = new RequestFilter(getSharedPreferences("browser", MODE_PRIVATE), getResources(), true);
        filter.configure(web);
        WebSettings options = web.getSettings();
        options.setAllowContentAccess(false);
        options.setAllowFileAccess(false);
        options.setGeolocationEnabled(false);
        options.setJavaScriptEnabled(true);
        options.setSupportMultipleWindows(true);
        options.setJavaScriptCanOpenWindowsAutomatically(false);
        options.setSafeBrowsingEnabled(true);
        options.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptThirdPartyCookies(web, false);
        ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setBlockNetworkLoads(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                callback.onReceiveValue(null);
                android.widget.Toast.makeText(HttpTab.this, "อัปโหลดไฟล์ได้ผ่านเว็บ HTTPS", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        final String approvedOrigin = origin;
        web.setWebViewClient(filter.new Client(approvedOrigin) {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!available(tab)) return true;
                Uri target = request.getUrl();
                if (approvedOrigin.equals(HttpOrigin.of(target.toString()))) return false;
                if (!request.isForMainFrame() && "https".equalsIgnoreCase(target.getScheme())) return false;
                if (request.isForMainFrame() && "GET".equals(request.getMethod()) &&
                        (HttpOrigin.of(target.toString()) != null || "https".equalsIgnoreCase(target.getScheme()))) {
                    if ("https".equalsIgnoreCase(target.getScheme())) openSecureLink(target, request.hasGesture());
                    else confirm(target.toString());
                }
                return true;
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                if (web == view && approvedOrigin.equals(HttpOrigin.of(url)) && history != null) {
                    Bundle update = new Bundle();
                    update.putString("page", url);
                    history.send(0, update);
                }
            }

            @Override public void onPageStarted(WebView view, String page, android.graphics.Bitmap icon) {
                if (!available(tab)) return;
                super.onPageStarted(view, page, icon);
                if (!approvedOrigin.equals(HttpOrigin.of(page))) view.stopLoading();
            }
        });
        // The route also covers redirect hops that WebViewClient does not report.
        new Thread(() -> {
            try {
                ProxySelector system = ProxySelector.getDefault();
                if (system != null && !system.select(URI.create(url)).equals(java.util.Collections.singletonList(Proxy.NO_PROXY))) {
                    runOnUiThread(() -> status.setText("เว็บนี้ต้องผ่านพร็อกซีของเครื่อง แท็บ HTTP ยังไม่รองรับพร็อกซีนี้"));
                    return;
                }
                ProxyConfig routes = WebRoutes.isolated(getSystemService(ConnectivityManager.class).getDefaultProxy(), approvedOrigin);
                runOnUiThread(() -> {
                    if (!available(tab)) return;
                    try {
                        routing = true;
                        ProxyController.getInstance().setProxyOverride(routes, getMainExecutor(), () -> {
                            if (!available(tab)) return;
                            cookies.removeAllCookies(ignored -> { if (available(tab)) tab.loadUrl(url); });
                        });
                    } catch (IllegalArgumentException | UnsupportedOperationException error) {
                        status.setText("ตั้งค่าการป้องกันแท็บ HTTP ไม่สำเร็จ");
                    }
                });
            } catch (IOException | RuntimeException failure) {
                runOnUiThread(() -> status.setText("เปิดแท็บ HTTP อย่างปลอดภัยไม่ได้ กรุณาปิดแล้วลองใหม่"));
            }
        }, "veil-http-route").start();
    }

    private boolean available(WebView tab) { return active == this && !isFinishing() && !isDestroyed() && web == tab; }

    private void openSecureLink(Uri target, boolean userGesture) {
        if (target.getHost() == null || target.getUserInfo() != null || warning != null) return;
        if (userGesture) {
            returnLink(target.toString());
            return;
        }
        Sheets sheet = new Sheets(this, "เว็บกำลังเปลี่ยนไปใช้ HTTPS", true);
        warning = sheet.dialog;
        sheet.dialog.setOnDismissListener(d -> warning = null);
        sheet.row(sheet.label(target.getEncodedAuthority(), 18, true));
        sheet.row(sheet.action("อยู่ในแท็บนี้", sheet.dialog::dismiss));
        sheet.row(sheet.action("เปิดในแท็บหลัก", () -> {
            returnLink(target.toString());
            sheet.dialog.dismiss();
        }));
        sheet.dialog.show();
    }

    private void returnLink(String url) {
        setResult(RESULT_OK, new Intent().setData(Uri.parse(url)));
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingUrl != null) state.putString("page", pendingUrl);
        else if (web != null && origin.equals(HttpOrigin.of(web.getUrl()))) state.putString("page", web.getUrl());
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void destroyWebView() {
        if (web != null) {
            web.stopLoading();
            ((LinearLayout) web.getParent()).removeView(web);
            web.destroy();
            web = null;
            CookieManager.getInstance().removeAllCookies(null);
            if (routing) {
                routing = false;
                try {
                    ProxyController.getInstance().setProxyOverride(WebRoutes.closed(), getMainExecutor(), () -> {});
                } catch (IOException | IllegalArgumentException | UnsupportedOperationException ignored) {
                    // No WebView remains. Every new tab must install a fresh route before loading.
                }
            }
        }
    }

    @Override protected void onDestroy() {
        if (proxyChanges != null) unregisterReceiver(proxyChanges);
        if (warning != null) {
            warning.setOnDismissListener(null);
            warning.dismiss();
        }
        destroyWebView();
        if (active == this) active = null;
        super.onDestroy();
    }
}
