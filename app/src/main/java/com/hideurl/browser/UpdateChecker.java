package com.hideurl.browser;

import android.app.Activity;
import android.app.Dialog;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateChecker {
    private static final long CHECK_INTERVAL = 15 * 60 * 1000L;
    private final Activity activity;
    private final SharedPreferences settings;
    private final PackageInfo installed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final int AUTHORIZE = 4201;
    private static final int INSTALL = 4202;
    private final LinearLayout banner;
    private final TextView label;
    private final ProgressBar bar;
    private final Runnable hideCompleted = this::hide;
    private boolean downloading;
    private boolean closed;
    private boolean checking;
    private long nextAutoCheck;
    private boolean pendingReport;
    private PublishedVersion pendingRelease;
    private boolean manual;
    private boolean visible;
    private Dialog prompt;

    UpdateChecker(Activity activity, LinearLayout screen) {
        this.activity = activity;
        banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(0xff232323);
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
            banner.postDelayed(hideCompleted, 4200);
        } else if (!downloading && staged() > current && UpdateApkProvider.apk(activity).isFile()) {
            ready();
        }
        if (pendingReport) {
            pendingReport = false;
            finish(pendingRelease);
            pendingRelease = null;
        } else if (android.os.SystemClock.elapsedRealtime() >= nextAutoCheck) {
            check(false);
        }
    }

    void onStop() { visible = false; }

    void check(boolean userRequested) {
        if (downloading || (prompt != null && prompt.isShowing())) return;
        if (userRequested) {
            manual = true;
            status("กำลังตรวจสอบรุ่นล่าสุด…", -1, null);
        }
        if (checking) return;
        checking = true;
        worker.execute(() -> {
            PublishedVersion latest = null;
            try {
                latest = ReleaseFeed.latest();
            } catch (IOException | IllegalArgumentException ignored) {
                // A failed lookup is reported separately from a current installation.
            }
            PublishedVersion result = latest;
            activity.runOnUiThread(() -> finish(result));
        });
    }

    private void finish(PublishedVersion result) {
        checking = false;
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (!visible) {
            pendingRelease = result;
            pendingReport = true;
            return;
        }
        boolean showStatus = manual;
        manual = false;
        if (result == null || result.code < installed.getLongVersionCode()) {
            nextAutoCheck = android.os.SystemClock.elapsedRealtime() + 30000L;
            if (showStatus) status("ยืนยันรุ่นล่าสุดไม่ได้ · แตะเพื่อลองอีกครั้ง", -2, () -> check(true));
            return;
        }
        nextAutoCheck = android.os.SystemClock.elapsedRealtime() + CHECK_INTERVAL;
        if (!result.isNewerThan(installed.getLongVersionCode())) {
            if (showStatus) status("รุ่นในเครื่อง " + installedName() + " · รุ่นล่าสุด " + result.name + " · ปิด", -2, this::hide);
            return;
        }
        if (showStatus) hide();
        Sheets sheet = new Sheets(activity, "มีอัปเดต " + result.name, true);
        sheet.row(sheet.label("ติดตั้งทับรุ่น " + installedName() + " โดยเก็บเว็บที่บันทึกไว้และการตั้งค่า", 16, false));
        sheet.row(sheet.action("อัปเดตในแอป", () -> { sheet.dialog.dismiss(); download(result); }));
        sheet.row(sheet.action("ไว้ภายหลัง", sheet.dialog::dismiss));
        prompt = sheet.dialog;
        prompt.show();
    }

    private long staged() { return settings.getLong("staged", -1); }

    private void status(String text, int percent, Runnable tap) {
        banner.removeCallbacks(hideCompleted);
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
                status("อนุญาตให้ VEIL ติดตั้งอัปเดต", -2, this::install);
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
