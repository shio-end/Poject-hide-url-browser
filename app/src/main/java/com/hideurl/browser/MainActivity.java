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
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    private static final int GOLD = 0xffe6bb65;
    private static final String[] FONT_NAMES = {"ค่าเริ่มต้น", "Serif", "Monospace"};
    private static final String[] FONT_FAMILIES = {"sans-serif", "serif", "monospace"};
    private static final int WALLPAPER_REQUEST = 1;

    private SharedPreferences settings;
    private FrameLayout root;
    private FrameLayout home;
    private FrameLayout browser;
    private ImageView wallpaper;
    private EditText urlInput;
    private TextView saveButton;
    private WebView web;
    private View back;
    private View forward;
    private boolean choosingWallpaper;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
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
        settings = getSharedPreferences("browser", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        getWindow().getInsetsController().setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());

        choosingWallpaper = state != null && state.getBoolean("choosingWallpaper");
        buildHome();
        buildBrowser();
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
        home.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xffffe9ad, GOLD, 0xffb7814a}));
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setRenderEffect(RenderEffect.createBlurEffect(dp(9), dp(9), Shader.TileMode.CLAMP));
        home.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        applyWallpaper();

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x88ffffff, 0x24ffffff, 0x18ffffff, 0x56ffffff});
        glass.setCornerRadius(dp(32));
        glass.setStroke(dp(1), 0xaaffffff);
        controls.setBackground(glass);
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
        saveButton.setOnClickListener(view -> saveUrl());
        buttons.addView(saveButton, new LinearLayout.LayoutParams(dp(72), dp(48)));
        addIcon(buttons, "cloud", "เว็บไซต์ที่บันทึกไว้", 48, 8, () -> showSaved());
        addIcon(buttons, "settings", "ตั้งค่า", 48, 8, () -> showSettings());

        LinearLayout launch = new LinearLayout(this);
        LinearLayout.LayoutParams launchRow = new LinearLayout.LayoutParams(-1, -2);
        launchRow.topMargin = dp(4);
        controls.addView(launch, launchRow);
        addIcon(launch, "play", "เปิดเว็บไซต์", 56, 0, () -> openInput());
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
        root.addView(browser, panel);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
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
                updateNavigation();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) updateNavigation();
            }
        });
        FrameLayout.LayoutParams viewport = new FrameLayout.LayoutParams(-1, -1);
        browser.addView(web, viewport);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams toolbar = new FrameLayout.LayoutParams(-2, dp(56),
                Gravity.BOTTOM | Gravity.LEFT);
        toolbar.leftMargin = dp(6);
        browser.addView(bar, toolbar);
        addIcon(bar, "cloud", "กลับหน้าหลัก", 48, 0, () -> showPage(false));

        LinearLayout arrows = new LinearLayout(this);
        arrows.setGravity(Gravity.CENTER_VERTICAL);
        arrows.setBackground(rounded(Color.BLACK, 24));
        LinearLayout.LayoutParams arrowsPlacement = new LinearLayout.LayoutParams(-2, dp(48));
        arrowsPlacement.leftMargin = dp(4);
        bar.addView(arrows, arrowsPlacement);
        back = addIcon(arrows, "back", "ย้อนกลับ", 48, 0, () -> {
            if (web.canGoBack()) web.goBack();
        });
        View divider = new View(this);
        divider.setBackground(rounded(Color.WHITE, 1));
        arrows.addView(divider, new LinearLayout.LayoutParams(dp(2), dp(23)));
        forward = addIcon(arrows, "forward", "ไปข้างหน้า", 48, 0, () -> {
            if (web.canGoForward()) web.goForward();
        });
    }

    private void showPage(boolean browsing) {
        if (browsing) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
            urlInput.clearFocus();
        }
        home.setVisibility(browsing ? View.GONE : View.VISIBLE);
        browser.setVisibility(browsing ? View.VISIBLE : View.GONE);
        updateNavigation();
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
        ArrayList<String> links = savedUrls();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("เว็บไซต์ที่บันทึกไว้")
                .setNegativeButton("ปิด", null);
        if (links.isEmpty()) {
            dialog.setMessage("ยังไม่มี URL ที่บันทึกไว้");
        } else {
            dialog.setItems(links.toArray(new String[0]), (d, index) -> {
                String url = links.get(index);
                urlInput.setText(url);
                open(url);
            });
        }
        dialog.show();
    }

    private void showSettings() {
        String[] options = {"เปลี่ยนฟอนต์", "เลือกรูปพื้นหลัง", "ใช้พื้นหลังเดิม"};
        new AlertDialog.Builder(this).setTitle("ตั้งค่าหน้าหลัก")
                .setItems(options, (dialog, index) -> {
                    if (index == 0) {
                        new AlertDialog.Builder(this).setTitle("ฟอนต์")
                                .setSingleChoiceItems(FONT_NAMES, settings.getInt("font", 0), (fonts, choice) -> {
                                    settings.edit().putInt("font", choice).apply();
                                    applyFont();
                                    fonts.dismiss();
                                }).setNegativeButton("ปิด", null).show();
                    } else if (index == 1) {
                        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        picker.addCategory(Intent.CATEGORY_OPENABLE);
                        picker.setType("image/*");
                        choosingWallpaper = true;
                        startActivityForResult(picker, WALLPAPER_REQUEST);
                    } else {
                        releaseWallpaper(settings.getString("wallpaper", ""));
                        settings.edit().remove("wallpaper").apply();
                        applyWallpaper();
                    }
                }).setNegativeButton("ปิด", null).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != WALLPAPER_REQUEST) return;
        choosingWallpaper = false;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri image = data.getData();
        String previous = settings.getString("wallpaper", "");
        try {
            getContentResolver().takePersistableUriPermission(image, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            wallpaper.setImageDrawable(decodeWallpaper(image));
            settings.edit().putString("wallpaper", image.toString()).apply();
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
                    int size = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    decoder.setTargetSampleSize(Math.max(1, (size + 1599) / 1600));
                });
    }

    private void applyWallpaper() {
        String file = settings.getString("wallpaper", "");
        wallpaper.setImageDrawable(null);
        if (file.isEmpty()) return;
        try {
            wallpaper.setImageDrawable(decodeWallpaper(Uri.parse(file)));
        } catch (java.io.IOException | SecurityException | IllegalArgumentException ignored) {
            settings.edit().remove("wallpaper").apply();
        }
    }

    private void applyFont() {
        String family = FONT_FAMILIES[settings.getInt("font", 0)];
        urlInput.setTypeface(Typeface.create(family, Typeface.NORMAL));
        saveButton.setTypeface(Typeface.create(family, Typeface.BOLD));
    }

    @Override
    protected void onStop() {
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
        state.putBoolean("choosingWallpaper", choosingWallpaper);
        state.putBundle("web", webState);
        state.putBoolean("browsing", browser.getVisibility() == View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (browser.getVisibility() == View.VISIBLE && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
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
        icon.setOnClickListener(view -> action.run());
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
