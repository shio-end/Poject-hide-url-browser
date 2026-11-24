package com.hideurl.browser;

import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceResponse;
import android.widget.Switch;
import android.widget.SeekBar;
import java.util.Locale;
import java.util.function.IntConsumer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;

public class MainActivity extends Activity {
    private LinearLayout controls;
    private static final String[] FONT_NAMES = {"ค่าเริ่มต้น", "Serif", "Monospace"};
    private static final String[] FONT_FAMILIES = {"sans-serif", "serif", "monospace"};
    private static final int WALLPAPER_REQUEST = 1;
    private static final int THEME_PHOTO_REQUEST = 2;

    private UpdateChecker updates;
    private MusicPanel music;
    private View musicHeart;
    private RequestFilter filter;
    private SharedPreferences settings;
    private FrameLayout root;
    private FrameLayout home;
    private FrameLayout browser;
    private ImageView wallpaper;
    private View shade;
    private LinearLayout browserBar;
    private EditText urlInput;
    private TextView saveButton;
    private WebView web;
    private View back;
    private View forward;
    private boolean choosingWallpaper;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TapSound.prepare(this);
        getSplashScreen().setOnExitAnimationListener(splash -> {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                splash.remove();
                return;
            }
            View cloud = splash.getIconView();
            if (cloud != null) {
                cloud.animate().scaleX(1.12f).scaleY(1.12f).translationY(-dp(8))
                        .setDuration(200).start();
            }
            splash.animate().alpha(0f).setDuration(220).withEndAction(splash::remove).start();
        });
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        updates = new UpdateChecker(this, screen);
        settings = getSharedPreferences("browser", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        screen.addView(root, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);
        getWindow().getInsetsController().setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());

        choosingWallpaper = state != null && state.getBoolean("choosingWallpaper");
        filter = new RequestFilter(settings, getResources());
        music = new MusicPanel(this, root, filter, this::updateMusicHeart);
        buildHome();
        buildBrowser();
        if (state != null) music.restore(state.getString("musicService"));
        String latest = settings.getString("latest", "");
        Bundle webState = state == null ? null : state.getBundle("web");
        if (webState != null && web.restoreState(webState) != null) {
            showPage(state.getBoolean("browsing"));
        } else if (!latest.isEmpty()) {
            open(latest);
        } else {
            showPage(false);
        }
    }

    private void buildHome() {
        home = new FrameLayout(this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        home.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(this);
        home.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        applyWallpaper();

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        applyHomeTheme();
        controls.setElevation(dp(4));
        controls.setPadding(dp(14), dp(14), dp(14), dp(14));
        FrameLayout.LayoutParams placement = new FrameLayout.LayoutParams(dp(264), -2, Gravity.CENTER);
        home.addView(controls, placement);
        controls.setTranslationY(dp(38));

        urlInput = new EditText(this);
        urlInput.setSingleLine();
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setTextSize(22);
        urlInput.setHint("Url..");
        urlInput.setHintTextColor(0xffe6e6e6);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setPadding(dp(18), 0, dp(18), 0);
        urlInput.setBackground(rounded(Color.BLACK, 23));
        controls.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(66)));
        urlInput.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            openInput();
            return true;
        });

        LinearLayout buttons = new LinearLayout(this);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.topMargin = dp(6);
        controls.addView(buttons, row);

        saveButton = new TextView(this);
        saveButton.setText("SAVE");
        saveButton.setTextColor(Color.WHITE);
        saveButton.setTextSize(18);
        saveButton.setGravity(Gravity.CENTER);
        styleButton(saveButton);
        TapSound.bind(saveButton, this::saveUrl);
        buttons.addView(saveButton, new LinearLayout.LayoutParams(dp(72), dp(48)));
        addIcon(buttons, "cloud", "เว็บไซต์ที่บันทึกไว้", 48, 8, () -> showSaved());
        addIcon(buttons, "settings", "ตั้งค่า", 48, 8, () -> showSettings());

        LinearLayout launch = new LinearLayout(this);
        LinearLayout.LayoutParams launchRow = new LinearLayout.LayoutParams(-1, -2);
        launchRow.topMargin = dp(4);
        controls.addView(launch, launchRow);
        addIcon(launch, "play", "เปิดเว็บไซต์", 56, 0, () -> openInput());
        addIcon(launch, "music", "เพลงพื้นหลัง", 48, 8, music::choose);
        applyFont();
        home.setFocusableInTouchMode(true);
        home.requestFocus();
    }

    private void buildBrowser() {
        browser = new FrameLayout(this);
        browser.setBackground(rounded(Color.WHITE, 14));
        browser.setClipToOutline(true);
        FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(-1, -1);
        panel.setMargins(dp(8), dp(8), dp(8), dp(8));
        browser.setVisibility(View.GONE);
        root.addView(browser, panel);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        applyZoom();
        filter.configure(web);
        web.setWebChromeClient(new android.webkit.WebChromeClient());
        ServiceWorkerController.getInstance().setServiceWorkerClient(new WorkerFilter(filter));
        web.setWebViewClient(filter.new Client() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isWebUrl(request.getUrl());
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                remember(url);
                updateNavigation();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateNavigation();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) updateNavigation();
            }
        });
        FrameLayout.LayoutParams viewport = new FrameLayout.LayoutParams(-1, -1);
        browser.addView(web, viewport);

        browserBar = new LinearLayout(this);
        browserBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams toolbar = new FrameLayout.LayoutParams(-2, dp(56),
                Gravity.BOTTOM | Gravity.LEFT);
        toolbar.leftMargin = dp(6);
        browser.addView(browserBar, toolbar);
        addIcon(browserBar, "cloud", "กลับหน้าหลัก", 48, 0, () -> showPage(false));

        LinearLayout arrows = new LinearLayout(this);
        arrows.setGravity(Gravity.CENTER_VERTICAL);
        arrows.setBackground(rounded(Color.BLACK, 24));
        LinearLayout.LayoutParams arrowsPlacement = new LinearLayout.LayoutParams(-2, dp(48));
        arrowsPlacement.leftMargin = dp(4);
        browserBar.addView(arrows, arrowsPlacement);
        back = addIcon(arrows, "back", "ย้อนกลับ", 48, 0, () -> {
            if (web.canGoBack()) {
                nudge(back, -1);
                web.goBack();
            }
        });
        View divider = new View(this);
        divider.setBackground(rounded(Color.WHITE, 1));
        arrows.addView(divider, new LinearLayout.LayoutParams(dp(2), dp(23)));
        forward = addIcon(arrows, "forward", "ไปข้างหน้า", 48, 0, () -> {
            if (web.canGoForward()) {
                nudge(forward, 1);
                web.goForward();
            }
        });
        LinearLayout musicDock = new LinearLayout(this);
        FrameLayout.LayoutParams dock = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        dock.setMargins(0, 0, dp(6), dp(4));
        browser.addView(musicDock, dock);
        musicHeart = addIcon(musicDock, "heart", "เปิดพรีวิวเพลง", 48, 0, music::preview);
        updateMusicHeart();
    }

    private void updateMusicHeart() {
        musicHeart.setVisibility(music.enabled() ? View.VISIBLE : View.GONE);
    }

    private void showPage(boolean browsing) {
        if (browsing && browser.getVisibility() != View.VISIBLE && ValueAnimator.areAnimatorsEnabled()) {
            browserBar.setAlpha(0f);
            browserBar.setTranslationY(dp(14));
            browserBar.animate().alpha(1f).translationY(0).setDuration(220).start();
        }
        if (browsing) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
            urlInput.clearFocus();
        }
        home.setVisibility(browsing ? View.GONE : View.VISIBLE);
        browser.setVisibility(browsing ? View.VISIBLE : View.GONE);
        updateNavigation();
    }

    private void nudge(View arrow, int direction) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        arrow.setTranslationX(direction * dp(4));
        arrow.animate().translationX(0).setDuration(180).start();
    }

    private static final class WorkerFilter extends ServiceWorkerClient {
        private final RequestFilter filter;

        WorkerFilter(RequestFilter filter) { this.filter = filter; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
            return filter.intercept(request);
        }
    }

    private void updateNavigation() {
        back.setEnabled(web.canGoBack());
        forward.setEnabled(web.canGoForward());
        back.setAlpha(back.isEnabled() ? 1f : 0.4f);
        forward.setAlpha(forward.isEnabled() ? 1f : 0.4f);
    }

    private void openInput() {
        String url = readInput();
        if (url != null) open(url);
    }

    private void open(String url) {
        if (!isWebUrl(Uri.parse(url))) return;
        remember(url);
        showPage(true);
        web.loadUrl(url);
    }

    private void remember(String url) {
        if (url != null && isWebUrl(Uri.parse(url))) {
            settings.edit().putString("latest", url).apply();
        }
    }

    private static boolean isWebUrl(Uri uri) {
        String scheme = uri.getScheme();
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                && uri.getHost() != null && !uri.getHost().isEmpty();
    }

    private String readInput() {
        String input = urlInput.getText().toString().trim();
        String url = input.contains("://") ? input : "https://" + input;
        try {
            java.net.URI parsed = new java.net.URI(url);
            if (isWebUrl(Uri.parse(url)) && parsed.getRawUserInfo() == null
                    && Uri.parse(url).getUserInfo() == null) return url;
        } catch (java.net.URISyntaxException ignored) {
            // The field shows the same error for malformed input.
        }
        urlInput.setError("กรอก URL เว็บไซต์ให้ถูกต้อง");
        return null;
    }

    private ArrayList<String> savedUrls() {
        ArrayList<String> links = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(settings.getString("saved", "[]"));
            for (int i = 0; i < stored.length(); i++) links.add(stored.getString(i));
        } catch (JSONException ignored) {
            return links;
        }
        return links;
    }

    private void saveUrl() {
        String url = readInput();
        if (url == null) return;
        ArrayList<String> links = savedUrls();
        if (!links.contains(url)) {
            links.add(0, url);
            settings.edit().putString("saved", new JSONArray(links).toString()).apply();
        }
        Toast.makeText(this, "บันทึก URL แล้ว", Toast.LENGTH_SHORT).show();
    }

    private void showSaved() {
        Sheets sheet = new Sheets(this, "เว็บที่บันทึกไว้");
        EditText search = new EditText(this);
        search.setSingleLine();
        search.setHint("ค้นหาชื่อเว็บหรือ URL");
        search.setTextSize(16);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setMinHeight(dp(48));
        search.setBackground(sheet.surface(sheet.theme.surface, 16));
        search.setTextColor(sheet.theme.ink);
        search.setHintTextColor(sheet.theme.muted);
        sheet.row(search);
        LinearLayout results = sheet.column();
        sheet.body.addView(results);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderSaved(sheet, search, results); }
            public void afterTextChanged(Editable s) {}
        });
        renderSaved(sheet, search, results);
        sheet.dialog.show();
    }

    private void renderSaved(Sheets sheet, EditText search, LinearLayout results) {
        results.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (String link : savedUrls()) {
            if (!link.toLowerCase(Locale.ROOT).contains(query)) continue;
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(sheet.surface(sheet.theme.surface, 16));
            LinearLayout entry = sheet.column();
            entry.setPadding(dp(12), dp(10), dp(8), dp(10));
            entry.addView(sheet.label(Uri.parse(link).getHost(), 17, true));
            TextView address = sheet.label(link, 13, false);
            address.setTextColor(sheet.theme.muted);
            address.setSingleLine();
            address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            entry.addView(address);
            sheet.clickable(entry, () -> {
                sheet.dialog.dismiss();
                urlInput.setText(link);
                open(link);
            });
            card.addView(entry, new LinearLayout.LayoutParams(0, -2, 1));
            TextView remove = sheet.action("ลบ", () ->
                new AlertDialog.Builder(this, sheet.theme.dialogTheme()).setTitle("ลบเว็บที่บันทึกไว้?")
                        .setMessage(link).setNegativeButton("ยกเลิก", null)
                        .setPositiveButton("ลบ", (d, which) -> {
                            ArrayList<String> links = savedUrls();
                            links.remove(link);
                            settings.edit().putString("saved", new JSONArray(links).toString()).apply();
                            renderSaved(sheet, search, results);
                        }).show());
            remove.setContentDescription("ลบ " + link);
            card.addView(remove);
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
            gap.topMargin = dp(8);
            results.addView(card, gap);
        }
        if (results.getChildCount() == 0) {
            TextView empty = sheet.label(query.isEmpty()
                    ? "ยังไม่มีเว็บที่บันทึกไว้ · เพิ่ม URL ด้วยปุ่ม SAVE" : "ไม่พบเว็บที่ตรงกับคำค้น", 15, false);
            empty.setPadding(dp(6), dp(18), dp(6), dp(12));
            results.addView(empty);
        }
    }

    private void showSettings() {
        Sheets sheet = new Sheets(this, "ตั้งค่า");
        settingsLink(sheet, "รูปลักษณ์และเสียง", Appearance.MODES[sheet.theme.mode], this::showAppearance);
        settingsLink(sheet, "การป้องกัน", filter.adsEnabled() ? "AdGuard · เปิด" : "AdGuard · ปิด", this::showProtection);
        settingsLink(sheet, "การอ่านเว็บ", web.getSettings().getTextZoom() + "%", this::showReading);
        settingsLink(sheet, "ตรวจสอบอัปเดต", "รุ่น " + updates.installedName(), () -> updates.check(true));
        sheet.dialog.show();
    }

    private void settingsLink(Sheets sheet, String title, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(14), dp(12));
        row.setMinimumHeight(dp(58));
        row.addView(sheet.label(title, 16, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView detail = sheet.label(value + "   ›", 14, false);
        detail.setTextColor(sheet.theme.muted);
        row.addView(detail);
        sheet.clickable(row, () -> {
            sheet.dialog.dismiss();
            action.run();
        });
        sheet.row(row);
    }

    private Sheets settingsPage(String title) {
        Sheets sheet = new Sheets(this, title);
        sheet.header.addView(sheet.action("‹", () -> {
            sheet.dialog.dismiss();
            showSettings();
        }), 0);
        sheet.header.getChildAt(0).setContentDescription("กลับการตั้งค่า");
        sheet.dialog.setOnCancelListener(dialog -> showSettings());
        return sheet;
    }

    private void showProtection() {
        Sheets sheet = settingsPage("การป้องกัน");
        addSwitch(sheet, "บล็อกโฆษณา · AdGuard", "blockAds");
        addSwitch(sheet, "บล็อกโดเมนติดตาม", "blockTrackers");
        addSwitch(sheet, "บล็อกป๊อปอัปและหน้าต่างใหม่", "blockPopups");
        addSwitch(sheet, "บล็อกคุกกี้จากบุคคลที่สาม", "blockThirdPartyCookies");
        addSwitch(sheet, "บล็อกเว็บอันตราย (Safe Browsing)", "safeBrowsing");
        sheet.dialog.show();
    }

    private void showReading() {
        Sheets sheet = settingsPage("การอ่านเว็บ");
        choice(sheet, "ขนาดข้อความบนเว็บ", new String[]{"85%", "100%", "115%", "130%", "150%"},
                settings.getInt("zoomStep", 1), i -> {
                    settings.edit().putInt("zoomStep", i).apply();
                    applyZoom();
                });
        sheet.dialog.show();
    }

    private void showAppearance() {
        Sheets sheet = settingsPage("รูปลักษณ์และเสียง");
        sheet.section("ธีม");
        for (int start = 0; start < Appearance.MODES.length; start += 2) {
            LinearLayout pair = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                final int mode = start + col;
                boolean selected = mode == sheet.theme.mode;
                TextView tile = sheet.action((selected ? "✓  " : "") + Appearance.MODES[mode], () -> {
                    if (mode == Appearance.WALLPAPER && !settings.contains("wallpaper")) {
                        sheet.dialog.dismiss();
                        pickWallpaper(THEME_PHOTO_REQUEST);
                        return;
                    }
                    settings.edit().putInt("themeMode", mode).apply();
                    applyHomeTheme();
                    sheet.dialog.dismiss();
                    showAppearance();
                });
                tile.setSelected(selected);
                if (selected) {
                    tile.setBackground(sheet.surface(sheet.theme.ink, 14));
                    tile.setTextColor(sheet.theme.paper);
                }
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(52), 1);
                if (col > 0) cell.leftMargin = dp(6);
                pair.addView(tile, cell);
            }
            sheet.row(pair);
        }
        sheet.section("หน้าหลัก");
        choice(sheet, "ฟอนต์", FONT_NAMES, settings.getInt("font", 0), i -> {
            settings.edit().putInt("font", i).apply();
            applyFont();
        });
        String[] backgrounds = {"ไล่เฉดนุ่ม", "ไล่เฉดตัดกัน", "เงาเข้ม", "เลือกรูปจากเครื่อง"};
        int preset = settings.getInt("palette", 0);
        choice(sheet, "พื้นหลัง", backgrounds, settings.contains("wallpaper") ? 3 : preset, i -> {
            if (i == 3) {
                sheet.dialog.dismiss();
                pickWallpaper(WALLPAPER_REQUEST);
            } else {
                releaseWallpaper(settings.getString("wallpaper", ""));
                settings.edit().remove("wallpaper").putInt("palette", i).apply();
                applyWallpaper();
                sheet.dialog.dismiss();
                showAppearance();
            }
        });
        slider(sheet, "ความเบลอของรูป", "blur", 9, 24, " dp");
        slider(sheet, "ลดความสว่างพื้นหลัง", "dim", 0, 65, "%");
        sheet.section("เสียง");
        addSwitch(sheet, "เสียงตอบสนองเมื่อกด", "tapSound");
        sheet.dialog.show();
    }

    private void addSwitch(Sheets sheet, String title, String key) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextColor(sheet.theme.ink);
        toggle.setPadding(dp(14), 0, dp(14), 0);
        toggle.setMinHeight(dp(50));
        toggle.setBackground(sheet.surface(sheet.theme.surface, 14));
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{sheet.theme.accent, sheet.theme.muted}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{sheet.theme.line, sheet.theme.paper}));
        toggle.setSoundEffectsEnabled(false);
        toggle.setChecked(settings.getBoolean(key, true));
        toggle.setOnCheckedChangeListener((view, value) -> {
            settings.edit().putBoolean(key, value).apply();
            TapSound.play(this);
            if (key.equals("tapSound")) return;
            filter.configure(web);
            filter.stylePage(web);
            music.applyProtection();
        });
        sheet.row(toggle);
    }

    private void choice(Sheets sheet, String title, String[] options, int selected, IntConsumer apply) {
        TextView row = sheet.label(title + "  ·  " + options[selected], 16, false);
        row.setPadding(dp(14), dp(12), dp(8), dp(12));
        row.setMinHeight(dp(48));
        sheet.clickable(row, () -> new AlertDialog.Builder(this, sheet.theme.dialogTheme()).setTitle(title)
                .setSingleChoiceItems(options, java.util.Arrays.asList(options).indexOf(row.getTag()),
                        (dialog, i) -> {
                            dialog.dismiss();
                            TapSound.play(this);
                            apply.accept(i);
                            row.setTag(options[i]);
                            row.setText(title + "  ·  " + options[i]);
                        }).setNegativeButton("ยกเลิก", null).show());
        row.setTag(options[selected]);
        sheet.row(row);
    }

    private void slider(Sheets sheet, String title, String key, int initial, int max, String unit) {
        LinearLayout group = sheet.column();
        group.setBackground(sheet.surface(sheet.theme.surface, 14));
        group.setPadding(dp(12), dp(10), dp(12), dp(4));
        int current = settings.getInt(key, initial);
        TextView caption = sheet.label(title + "  ·  " + current + unit, 15, false);
        group.addView(caption);
        SeekBar slider = new SeekBar(this);
        slider.setProgressTintList(ColorStateList.valueOf(sheet.theme.accent));
        slider.setThumbTintList(ColorStateList.valueOf(sheet.theme.accent));
        slider.setContentDescription(title);
        slider.setMax(max);
        slider.setProgress(current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (!user) return;
                caption.setText(title + "  ·  " + value + unit);
                settings.edit().putInt(key, value).apply();
                applyWallpaperEffects();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) { TapSound.play(MainActivity.this); }
        });
        group.addView(slider, new LinearLayout.LayoutParams(-1, dp(40)));
        sheet.row(group);
    }

    private void pickWallpaper(int request) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        choosingWallpaper = true;
        startActivityForResult(picker, request);
    }

    private void applyZoom() {
        int[] sizes = {85, 100, 115, 130, 150};
        web.getSettings().setTextZoom(sizes[settings.getInt("zoomStep", 1)]);
    }

    private void applyWallpaperEffects() {
        int blur = dp(settings.getInt("blur", 9));
        wallpaper.setRenderEffect(blur == 0 ? null : RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        shade.setBackgroundColor(Color.argb(settings.getInt("dim", 0) * 255 / 100, 0, 0, 0));
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (updates.handleResult(request, result)) return;
        if (request != WALLPAPER_REQUEST && request != THEME_PHOTO_REQUEST) return;
        choosingWallpaper = false;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri image = data.getData();
        String previous = settings.getString("wallpaper", "");
        try {
            getContentResolver().takePersistableUriPermission(image, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            android.graphics.drawable.Drawable drawable = decodeWallpaper(image);
            int color = android.app.WallpaperColors.fromDrawable(drawable).getPrimaryColor().toArgb();
            wallpaper.setImageDrawable(drawable);
            SharedPreferences.Editor edit = settings.edit().putString("wallpaper", image.toString()).putInt("wallColor", color);
            if (request == THEME_PHOTO_REQUEST) edit.putInt("themeMode", Appearance.WALLPAPER);
            edit.apply();
            applyHomeTheme();
            if (!previous.equals(image.toString())) releaseWallpaper(previous);
        } catch (java.io.IOException | SecurityException | IllegalArgumentException error) {
            if (!previous.equals(image.toString())) releaseWallpaper(image.toString());
            Toast.makeText(this, "เปิดรูปพื้นหลังไม่ได้", Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseWallpaper(String file) {
        if (file.isEmpty()) return;
        try {
            getContentResolver().releasePersistableUriPermission(Uri.parse(file), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The document provider may already have revoked access.
        }
    }

    private android.graphics.drawable.Drawable decodeWallpaper(Uri uri) throws java.io.IOException {
        return ImageDecoder.decodeDrawable(ImageDecoder.createSource(getContentResolver(), uri),
                (decoder, info, source) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int size = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    decoder.setTargetSampleSize(Math.max(1, (size + 1599) / 1600));
                });
    }

    private void applyWallpaper() {
        applyHomeTheme();
        applyWallpaperEffects();
        String file = settings.getString("wallpaper", "");
        wallpaper.setImageDrawable(null);
        if (file.isEmpty()) return;
        try {
            android.graphics.drawable.Drawable drawable = decodeWallpaper(Uri.parse(file));
            wallpaper.setImageDrawable(drawable);
            if (!settings.contains("wallColor")) {
                settings.edit().putInt("wallColor", android.app.WallpaperColors.fromDrawable(drawable).getPrimaryColor().toArgb()).apply();
                applyHomeTheme();
            }
        } catch (java.io.IOException | SecurityException | IllegalArgumentException ignored) {
            settings.edit().remove("wallpaper").remove("wallColor").apply();
            applyHomeTheme();
        }
    }

    private void applyHomeTheme() {
        Appearance theme = new Appearance(this);
        int preset = settings.getInt("palette", 0);
        float contrast = new float[]{.13f, .28f, .42f}[preset];
        int shadow = Appearance.tint(theme.paper, theme.accent, contrast);
        home.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{theme.surface, theme.paper, shadow}));
        android.graphics.ColorMatrix mono = new android.graphics.ColorMatrix();
        mono.setSaturation(0f);
        wallpaper.setColorFilter(theme.mode == Appearance.WALLPAPER ? null
                : new android.graphics.ColorMatrixColorFilter(mono));
        if (controls != null) {
            GradientDrawable glass = rounded((theme.surface & 0xffffff) | 0xb8000000, 30);
            glass.setStroke(dp(1), theme.line);
            controls.setBackground(glass);
        }
    }

    private void applyFont() {
        String family = FONT_FAMILIES[settings.getInt("font", 0)];
        urlInput.setTypeface(Typeface.create(family, Typeface.NORMAL));
        saveButton.setTypeface(Typeface.create(family, Typeface.BOLD));
    }

    @Override
    protected void onStart() {
        super.onStart();
        updates.onStart();
    }

    @Override
    protected void onStop() {
        updates.onStop();
        super.onStop();
        // A launcher return may resume this Activity without calling onCreate again.
        if (!isChangingConfigurations() && !choosingWallpaper && settings.contains("latest")) {
            showPage(true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        Bundle webState = new Bundle();
        web.saveState(webState);
        state.putString("musicService", music.selection());
        state.putBoolean("choosingWallpaper", choosingWallpaper);
        state.putBundle("web", webState);
        state.putBoolean("browsing", browser.getVisibility() == View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (music.handleBack()) return;
        if (browser.getVisibility() == View.VISIBLE && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        music.close();
        updates.close();
        browser.removeView(web);
        web.destroy();
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void styleButton(View button) {
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55ffffff),
                rounded(Color.BLACK, 24), null));
        button.setStateListAnimator(AnimatorInflater.loadStateListAnimator(this, R.animator.button_press));
    }

    private View addIcon(LinearLayout row, String shape, String description, int width,
                         int margin, Runnable action) {
        Icon icon = new Icon(shape);
        icon.setContentDescription(description);
        icon.setFocusable(true);
        styleButton(icon);
        TapSound.bind(icon, action);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(dp(width), dp(48));
        layout.leftMargin = dp(margin);
        row.addView(icon, layout);
        return icon;
    }

    private final class Icon extends View {
        private final String shape;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(String shape) {
            super(MainActivity.this);
            this.shape = shape;
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return "android.widget.Button";
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = dp(30);
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 32f, size / 32f);
            paint.setColor(Color.WHITE);
            switch (shape) {
                case "heart":
                    android.graphics.drawable.Drawable heart = getDrawable(R.drawable.ic_music_heart);
                    heart.setBounds(0, 0, 32, 32);
                    heart.draw(canvas);
                    break;
                case "cloud":
                    canvas.drawRoundRect(1, 13, 31, 25, 6, 6, paint);
                    canvas.drawCircle(10, 15, 6, paint);
                    canvas.drawCircle(17, 14, 8, paint);
                    canvas.drawCircle(24, 17, 6, paint);
                    break;
                case "settings":
                    polygon(canvas, 16, 2, 28, 9, 28, 23, 16, 30, 4, 23, 4, 9);
                    paint.setColor(Color.BLACK);
                    polygon(canvas, 16, 12, 20, 14, 20, 18, 16, 20, 12, 18, 12, 14);
                    break;
                case "music":
                    canvas.drawCircle(7, 17, 4, paint);
                    canvas.drawRoundRect(12, 2, 17, 30, 2.5f, 2.5f, paint);
                    canvas.drawRoundRect(20, 7, 25, 25, 2.5f, 2.5f, paint);
                    break;
                case "play":
                    polygon(canvas, 8, 2, 30, 16, 8, 30);
                    break;
                case "forward":
                    canvas.translate(32, 0);
                    canvas.scale(-1, 1);
                    // Both arrows use the same outline.
                case "back":
                    paint.setStrokeWidth(7);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    canvas.drawLine(10, 10, 25, 25, paint);
                    polygon(canvas, 3, 3, 20, 3, 3, 20);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }

        private void polygon(Canvas canvas, float... points) {
            Path path = new Path();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
