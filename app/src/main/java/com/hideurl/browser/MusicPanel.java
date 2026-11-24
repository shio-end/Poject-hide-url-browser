package com.hideurl.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class MusicPanel {
    private enum Service {
        YOUTUBE("YouTube Music", "music.youtube.com", "com.google.android.apps.youtube.music"),
        SPOTIFY("Spotify", "open.spotify.com", "com.spotify.music");

        final String title;
        final String host;
        final String app;

        Service(String title, String host, String app) {
            this.title = title;
            this.host = host;
            this.app = app;
        }

        Uri homepage() { return Uri.parse("https://" + host + "/"); }
    }

    private final Activity activity;
    private final FrameLayout root;
    private final RequestFilter filter;
    private LinearLayout panel;
    private WebView player;
    private Service current;
    private AlertDialog mediaPrompt;
    private PermissionRequest pendingMedia;

    MusicPanel(Activity activity, FrameLayout root, RequestFilter filter) {
        this.activity = activity;
        this.root = root;
        this.filter = filter;
    }

    void choose() {
        Sheets sheet = new Sheets(activity, "เพลงพื้นหลัง");
        for (Service service : Service.values()) {
            sheet.section(service.title);
            sheet.row(sheet.action("เปิดบริการ · เข้าสู่ระบบด้วย Google", () -> {
                sheet.dialog.dismiss();
                openOfficial(service);
            }));
            boolean alreadyOpen = current == service;
            sheet.row(sheet.action(alreadyOpen ? "กลับไปที่แท็บเพลง" : "เปิดแท็บเพลงพร้อมตัวกรองโฆษณา", () -> {
                sheet.dialog.dismiss();
                showTab(service);
            }));
        }
        sheet.row(sheet.label("เมื่อเปิดบริการ ให้กดเล่นแล้วสลับกลับมาอ่านเว็บ การเล่นเมื่อล็อกจอขึ้นกับบริการและบัญชี ตัวกรองของ Hide URL ทำงานเฉพาะในแท็บของแอป", 14, false));
        if (player != null) {
            sheet.row(sheet.action("ปิดแท็บเพลง · " + current.title, () -> {
                sheet.dialog.dismiss();
                close();
            }));
        }
        sheet.dialog.show();
    }

    private void openOfficial(Service service) {
        Intent link = new Intent(Intent.ACTION_VIEW, service.homepage());
        try {
            try {
                activity.startActivity(new Intent(link).setPackage(service.app));
            } catch (ActivityNotFoundException missingApp) {
                activity.startActivity(link);
            }
            close();
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(activity, "ไม่พบแอปเพลงหรือเบราว์เซอร์สำหรับเปิดบริการ", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTab(Service service) {
        if (current == service) {
            panel.setVisibility(View.VISIBLE);
            panel.bringToFront();
            return;
        }
        close();
        current = service;
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.BLACK);
        LinearLayout tools = new LinearLayout(activity);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(activity);
        name.setText(service.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setPadding(dp(12), 0, 0, 0);
        tools.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        addControl(tools, "เข้าสู่ระบบ", () -> openOfficial(service));
        addControl(tools, "ย่อ", () -> panel.setVisibility(View.GONE));
        panel.addView(tools, new LinearLayout.LayoutParams(-1, dp(48)));

        player = new WebView(activity);
        player.getSettings().setJavaScriptEnabled(true);
        player.getSettings().setDomStorageEnabled(true);
        player.getSettings().setAllowFileAccess(false);
        player.getSettings().setAllowContentAccess(false);
        player.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                Uri origin = request.getOrigin();
                if (panel == null || !panel.isShown() || pendingMedia != null
                        || !"https".equalsIgnoreCase(origin.getScheme())
                        || !service.host.equalsIgnoreCase(origin.getHost())
                        || !java.util.Arrays.asList(request.getResources())
                                .contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                    request.deny();
                    return;
                }
                pendingMedia = request;
                mediaPrompt = new AlertDialog.Builder(activity)
                        .setTitle("เล่นเนื้อหาที่มีการคุ้มครอง")
                        .setMessage("อนุญาตให้ " + service.title + " ใช้รหัสสื่อของอุปกรณ์สำหรับเล่นเพลงนี้ไหม?")
                        .setPositiveButton("อนุญาต", (dialog, which) -> finishMediaRequest(true))
                        .setNegativeButton("ยกเลิก", (dialog, which) -> finishMediaRequest(false))
                        .setOnCancelListener(dialog -> finishMediaRequest(false)).show();
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (request == pendingMedia) {
                    pendingMedia = null;
                    mediaPrompt.dismiss();
                    mediaPrompt = null;
                }
            }
        });
        player.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return filter.intercept(request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equalsIgnoreCase(uri.getScheme())) return true;
                if (!request.isForMainFrame() || service.host.equalsIgnoreCase(uri.getHost())) return false;
                if (request.hasGesture() || "accounts.google.com".equalsIgnoreCase(uri.getHost())
                        || "accounts.spotify.com".equalsIgnoreCase(uri.getHost())) {
                    Toast.makeText(activity, "แตะ “เข้าสู่ระบบ” เพื่อเปิดบริการและใช้บัญชีของคุณ", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        panel.addView(player, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        player.loadUrl(service.homepage().toString());
    }

    private void addControl(LinearLayout row, String label, Runnable action) {
        android.widget.Button button = new android.widget.Button(activity);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        row.addView(button);
    }

    boolean handleBack() {
        if (panel == null || panel.getVisibility() != View.VISIBLE) return false;
        if (player.canGoBack()) player.goBack();
        else panel.setVisibility(View.GONE);
        return true;
    }

    private void finishMediaRequest(boolean allow) {
        PermissionRequest request = pendingMedia;
        pendingMedia = null;
        mediaPrompt = null;
        if (request == null) return;
        if (allow) request.grant(new String[] {PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
        else request.deny();
    }

    void close() {
        if (panel == null) return;
        if (mediaPrompt != null) {
            mediaPrompt.dismiss();
            finishMediaRequest(false);
        }
        player.stopLoading();
        root.removeView(panel);
        panel.removeView(player);
        player.destroy();
        player = null;
        panel = null;
        current = null;
    }

    private int dp(int n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }
}
