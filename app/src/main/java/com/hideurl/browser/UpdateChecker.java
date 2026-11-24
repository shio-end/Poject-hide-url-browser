package com.hideurl.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

final class UpdateChecker {
    private static final long CHECK_INTERVAL = 24 * 60 * 60 * 1000L;
    private final Activity activity;
    private final SharedPreferences settings;
    private final PackageInfo installed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final int AUTHORIZE = 4201;
    private static final int INSTALL = 4202;
    private final LinearLayout banner;
    private final TextView label;
    private final ProgressBar bar;
    private boolean downloading;
    private boolean closed;
    private boolean checking;
    private boolean manual;
    private boolean visible;
    private AlertDialog prompt;

    UpdateChecker(Activity activity, LinearLayout screen) {
        this.activity = activity;
        banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(0xff27241f);
        int pad = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        banner.setPadding(pad, pad / 2, pad, pad / 2);
        label = new TextView(activity);
        label.setTextColor(Color.WHITE);
        banner.addView(label);
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        banner.addView(bar);
        banner.setVisibility(View.GONE);
        screen.addView(banner);
        settings = activity.getSharedPreferences("updates", Activity.MODE_PRIVATE);
        try {
            installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    String installedName() {
        return installed.versionName;
    }

    void onStart() {
        visible = true;
        long current = installed.getLongVersionCode();
        if (settings.getLong("completed", -1) == current || settings.getLong("restartFor", -1) == current) {
            settings.edit().remove("completed").remove("restartFor").remove("staged").apply();
            UpdateApkProvider.apk(activity).delete();
            status("อัปเดตเป็น " + installedName() + " แล้ว", 100, null);
            banner.postDelayed(() -> { if (!downloading && staged() <= current) hide(); }, 4200);
        } else if (!downloading && staged() > current && UpdateApkProvider.apk(activity).isFile()) {
            ready();
        }
        long age = System.currentTimeMillis() - settings.getLong("lastCheck", 0);
        if (age < 0 || age >= CHECK_INTERVAL) check(false);
    }

    void onStop() { visible = false; }

    void check(boolean userRequested) {
        if (downloading || (prompt != null && prompt.isShowing())) return;
        if (userRequested) {
            manual = true;
            message("กำลังตรวจสอบรุ่นล่าสุด…");
        }
        if (checking) return;
        checking = true;
        worker.execute(() -> {
            PublishedVersion latest = null;
            try {
                latest = fetch();
            } catch (IOException | IllegalArgumentException ignored) {
                // Keep the previous successful check time so a retry can run.
            }
            PublishedVersion result = latest;
            activity.runOnUiThread(() -> finish(result));
        });
    }

    private static PublishedVersion fetch() throws IOException {
        URL url = new URL(PublishedVersion.REPOSITORY + "main/downloads/latest.properties");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Release feed unavailable");
            try (InputStream stream = connection.getInputStream()) {
                return PublishedVersion.read(stream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void finish(PublishedVersion result) {
        checking = false;
        boolean showStatus = manual;
        manual = false;
        if (!visible || activity.isDestroyed() || activity.isFinishing()) return;
        if (result == null) {
            if (showStatus) message("ตรวจสอบรุ่นล่าสุดไม่ได้ ลองอีกครั้งเมื่อเชื่อมต่ออินเทอร์เน็ต");
            return;
        }
        settings.edit().putLong("lastCheck", System.currentTimeMillis()).apply();
        if (!result.isNewerThan(installed.getLongVersionCode())) {
            if (showStatus) message("คุณใช้รุ่นล่าสุดแล้ว (" + installedName() + ")");
            return;
        }
        prompt = new AlertDialog.Builder(activity)
                .setTitle("มีอัปเดต " + result.name)
                .setMessage("ดาวน์โหลดและติดตั้งทับรุ่น " + installedName()
                        + " โดยเก็บเว็บที่บันทึกไว้และการตั้งค่า")
                .setNegativeButton("ไว้ภายหลัง", null)
                .setPositiveButton("อัปเดตในแอป", (dialog, which) -> download(result)).show();
    }

    private long staged() { return settings.getLong("staged", -1); }

    private void status(String text, int percent, Runnable tap) {
        label.setText(text);
        bar.setVisibility(percent == -2 ? View.GONE : View.VISIBLE);
        bar.setIndeterminate(percent == -1);
        if (percent >= 0) bar.setProgress(percent);
        banner.setOnClickListener(tap == null ? null : v -> tap.run());
        banner.setClickable(tap != null);
        banner.setFocusable(tap != null);
        banner.setVisibility(View.VISIBLE);
    }

    private void hide() { banner.setVisibility(View.GONE); }

    private void ready() {
        status("ดาวน์โหลดแล้ว · แตะเพื่อติดตั้งอัปเดต", 100, this::install);
    }

    private void download(PublishedVersion result) {
        if (downloading) return;
        downloading = true;
        status("กำลังดาวน์โหลด " + result.name, -1, null);
        worker.execute(() -> {
            try {
                UpdateDownload.fetch(activity, result, n -> activity.runOnUiThread(() -> {
                    if (!closed) status("กำลังดาวน์โหลด " + result.name + " · " + n + "%", n, null);
                }));
                activity.runOnUiThread(() -> {
                    if (closed) return;
                    downloading = false;
                    settings.edit().putLong("staged", result.code).apply();
                    ready();
                    if (visible) install();
                });
            } catch (IOException | RuntimeException error) {
                activity.runOnUiThread(() -> {
                    if (closed) return;
                    downloading = false;
                    status("ดาวน์โหลดหรือตรวจสอบไฟล์ไม่ได้ · แตะเพื่อลองใหม่", -2, () -> download(result));
                });
            }
        });
    }

    private void install() {
        try {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                status("อนุญาตให้ Hide URL Browser ติดตั้งอัปเดต", -2, this::install);
                activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())), AUTHORIZE);
                return;
            }
            long code = staged();
            UpdateDownload.verify(activity, UpdateApkProvider.apk(activity), code);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, UpdateApkProvider.uri(activity));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            settings.edit().putLong("restartFor", code).commit();
            status("รอยืนยันการติดตั้งจาก Android", -1, null);
            activity.startActivityForResult(intent, INSTALL);
        } catch (IOException invalid) {
            settings.edit().remove("staged").remove("restartFor").apply();
            status("ตรวจสอบไฟล์อัปเดตไม่ได้ · แตะเพื่อตรวจสอบอีกครั้ง", -2, () -> check(true));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            settings.edit().remove("restartFor").apply();
            ready();
            message("ไม่สามารถเปิดหน้าติดตั้งได้ โปรดตรวจสิทธิ์ติดตั้งแอปของเครื่อง");
        }
    }

    boolean handleResult(int request, int result) {
        if (request == AUTHORIZE) {
            if (activity.getPackageManager().canRequestPackageInstalls()) install();
            else ready();
            return true;
        }
        if (request != INSTALL) return false;
        if (result != Activity.RESULT_OK) {
            settings.edit().remove("restartFor").apply();
            ready();
        }
        return true;
    }

    private void message(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    void close() {
        visible = false;
        if (prompt != null) prompt.dismiss();
        closed = true;
        worker.shutdownNow();
    }
}
