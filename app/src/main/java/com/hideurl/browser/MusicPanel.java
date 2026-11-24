package com.hideurl.browser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import java.util.ArrayList;
import android.app.AlertDialog;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageButton;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

final class MusicPanel {
    static final int ACCOUNT_TAB = 491;
    boolean accountTabOpen;

    private enum Service {
        YOUTUBE("YouTube Music", "music.youtube.com"),
        SPOTIFY("Spotify", "open.spotify.com");

        final String title;
        final String host;

        Service(String title, String host) {
            this.title = title;
            this.host = host;
        }

        Uri homepage() { return Uri.parse("https://" + host + "/"); }
    }

    private final Activity activity;
    private final FrameLayout root;
    private final RequestFilter filter;
    private final Runnable selectionChanged;
    private final Runnable connectYoutube;
    private String skipScript;
    private FrameLayout panel;
    private FrameLayout card;
    private Service selected;
    private WebView player;
    private Service current;
    private AlertDialog mediaPrompt;
    private PermissionRequest pendingMedia;

    MusicPanel(Activity activity, FrameLayout root, RequestFilter filter, Runnable selectionChanged, Runnable connectYoutube) {
        this.connectYoutube = connectYoutube;
        this.activity = activity;
        this.root = root;
        this.filter = filter;
        this.selectionChanged = selectionChanged;
    }

    void choose() {
        Sheets sheet = new Sheets(activity, "เพลงพื้นหลัง");
        for (Service service : Service.values()) {
            sheet.row(sheet.action(service.title, () -> {
                sheet.dialog.dismiss();
                selected = service;
                selectionChanged.run();
                preview();
            }));
        }
        sheet.row(sheet.action("YouTube Music · ฟังด้วยบัญชี Google", () -> {
            if (openAccountTab(Service.YOUTUBE.homepage())) sheet.dialog.dismiss();
        }));
        sheet.row(sheet.label("แท็บเพลงนี้ใช้บัญชีของเบราว์เซอร์ แตะ × เพื่อกลับ VEIL", 14, false));
        sheet.row(sheet.action("เชื่อมบัญชี YouTube · เพลย์ลิสต์ของฉัน", () -> {
            sheet.dialog.dismiss();
            connectYoutube.run();
        }));
        sheet.row(sheet.label("เลือกเว็บเพลงเพื่อเปิดพรีวิว แตะหัวใจเพื่อย่อและกลับมาฟังต่อ", 14, false));
        if (selected != null) {
            sheet.row(sheet.action("การป้องกัน · " + selected.title, () -> {
                sheet.dialog.dismiss();
                filter.panel(activity, selected.host, () -> { applyProtection(); if (player != null) player.reload(); });
            }));
            sheet.row(sheet.action("ปิดแท็บเพลง · " + selected.title, () -> {
                sheet.dialog.dismiss();
                close();
                selected = null;
                selectionChanged.run();
            }));
        }
        sheet.dialog.show();
    }

    void openPlaylist(String id) {
        openAccountTab(Service.YOUTUBE.homepage().buildUpon().appendPath("playlist")
                .appendQueryParameter("list", id).build());
    }

    private boolean openAccountTab(Uri musicUrl) {
        ArrayList<String> providers = new ArrayList<>();
        Intent service = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
        for (ResolveInfo match : activity.getPackageManager().queryIntentServices(service, 0)) {
            if (match.serviceInfo != null) providers.add(match.serviceInfo.packageName);
        }
        String provider = CustomTabsClient.getPackageName(activity, providers);
        if (provider != null) {
            CustomTabsIntent tab = new CustomTabsIntent.Builder().setShowTitle(true).build();
            tab.intent.setPackage(provider);
            try {
                tab.intent.setData(musicUrl);
                activity.startActivityForResult(tab.intent, ACCOUNT_TAB);
                accountTabOpen = true;
                return true;
            } catch (ActivityNotFoundException ignored) {
                // The selected browser may have been disabled since the lookup.
            }
        }
        Toast.makeText(activity, "กรุณาติดตั้งหรือเปิดใช้เบราว์เซอร์ที่รองรับแท็บเพลง เช่น Chrome", Toast.LENGTH_LONG).show();
        return false;
    }

    boolean enabled() { return selected != null; }

    String selection() { return enabled() ? selected.name() : null; }

    void restore(String name) {
        for (Service service : Service.values()) {
            if (service.name().equals(name)) selected = service;
        }
        selectionChanged.run();
    }

    private void backdrop(boolean active) {
        RenderEffect effect = active
                ? RenderEffect.createBlurEffect(dp(9), dp(9), Shader.TileMode.CLAMP) : null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == panel) continue;
            child.setRenderEffect(effect);
            child.setImportantForAccessibility(active ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    }

    private void minimize() {
        panel.setVisibility(View.GONE);
        backdrop(false);
    }

    private ImageButton previewHeart;

    void applyAppearance(Appearance theme) {
        if (previewHeart == null) return;
        previewHeart.setColorFilter(theme.accent);
        previewHeart.setBackground(theme.buttonFace(activity.getResources().getDisplayMetrics().density));
        ((GradientDrawable) card.getBackground()).setColor(theme.surface);
    }

    private void revealCard() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        card.setAlpha(0f);
        card.setTranslationY(dp(10));
        card.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    void preview() {
        Service service = selected;
        if (service == null) return;
        if (current == service) {
            panel.setVisibility(View.VISIBLE);
            panel.bringToFront();
            revealCard();
            backdrop(true);
            return;
        }
        close();
        current = service;
        panel = new FrameLayout(activity);
        panel.setBackgroundColor(0x73000000);
        panel.setOnClickListener(v -> minimize());
        card = new FrameLayout(activity);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(0xff393939);
        surface.setCornerRadius(dp(16));
        card.setBackground(surface);
        card.setClipToOutline(true);
        card.setClickable(true);
        FrameLayout.LayoutParams inset = new FrameLayout.LayoutParams(-1, -1);
        inset.setMargins(dp(16), dp(12), dp(16), dp(12));
        panel.addView(card, inset);

        player = new WebView(activity);
        player.getSettings().setJavaScriptEnabled(true);
        player.getSettings().setDomStorageEnabled(true);
        player.getSettings().setAllowFileAccess(false);
        player.getSettings().setAllowContentAccess(false);
        filter.configure(player);
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
                mediaPrompt = new AlertDialog.Builder(activity, new Appearance(activity).dialogTheme())
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
        player.setWebViewClient(filter.new Client() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view == player) updateMusicAds();
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equalsIgnoreCase(uri.getScheme())) return true;
                if (!request.isForMainFrame() || service.host.equalsIgnoreCase(uri.getHost())) return false;
                if (service == Service.YOUTUBE && ("consent.youtube.com".equalsIgnoreCase(uri.getHost())
                        || "consent.google.com".equalsIgnoreCase(uri.getHost()))) return false;
                if (service == Service.SPOTIFY && "accounts.spotify.com".equalsIgnoreCase(uri.getHost())) return false;
                if ("accounts.google.com".equalsIgnoreCase(uri.getHost())) {
                    if (service == Service.YOUTUBE && panel != null && panel.isShown()) {
                        new AlertDialog.Builder(activity, new Appearance(activity).dialogTheme())
                                .setMessage("เปิด YouTube Music ในแท็บเพลงเพื่อลงชื่อเข้าใช้ด้วยบัญชีของเบราว์เซอร์?")
                                .setPositiveButton("เปิดแท็บเพลง", (dialog, which) -> openAccountTab(service.homepage()))
                                .setNegativeButton("ยกเลิก", null).show();
                        return true;
                    }
                    Toast.makeText(activity, "Google จำกัดการเข้าสู่ระบบในแท็บฝังตัวนี้", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        card.addView(player, new FrameLayout.LayoutParams(-1, -1));
        ImageButton heart = new ImageButton(activity);
        previewHeart = heart;
        heart.setImageResource(R.drawable.ic_music_heart);
        applyAppearance(new Appearance(activity));
        heart.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(activity, R.animator.button_press));
        heart.setPadding(dp(8), dp(8), dp(8), dp(8));
        heart.setContentDescription("ย่อพรีวิวเพลง");
        TapSound.bind(heart, this::minimize);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.RIGHT);
        corner.setMargins(0, 0, dp(8), dp(8));
        card.addView(heart, corner);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        backdrop(true);
        revealCard();
        player.loadUrl(service.homepage().toString());
    }

    private void updateMusicAds() {
        if (current != Service.YOUTUBE) return;
        if (skipScript == null) {
            try (java.util.Scanner input = new java.util.Scanner(
                    activity.getResources().openRawResource(R.raw.music_ads), "UTF-8")) {
                skipScript = input.useDelimiter("\\A").next();
            }
        }
        player.evaluateJavascript("(function(enabled){" + skipScript + "})(" + (filter.adsEnabled() && !filter.paused("music.youtube.com")) + ")", null);
    }

    void applyProtection() {
        if (player != null) {
            filter.configure(player);
            filter.stylePage(player);
            updateMusicAds();
        }
    }

    boolean handleBack() {
        if (panel == null || panel.getVisibility() != View.VISIBLE) return false;
        if (player.canGoBack()) player.goBack();
        else minimize();
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
        backdrop(false);
        root.removeView(panel);
        card.removeView(player);
        previewHeart = null;
        card = null;
        player.destroy();
        player = null;
        panel = null;
        current = null;
    }

    private int dp(int n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }
}
