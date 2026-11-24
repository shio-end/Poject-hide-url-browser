package com.hideurl.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
    private boolean checking;
    private boolean manual;
    private boolean visible;
    private AlertDialog prompt;

    UpdateChecker(Activity activity) {
        this.activity = activity;
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
        long age = System.currentTimeMillis() - settings.getLong("lastCheck", 0);
        if (age < 0 || age >= CHECK_INTERVAL) check(false);
    }

    void onStop() { visible = false; }

    void check(boolean userRequested) {
        if (prompt != null && prompt.isShowing()) return;
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
                .setMessage("รุ่นที่ใช้อยู่ " + installedName()
                        + "\nดาวน์โหลด APK แล้วเปิดไฟล์และเลือก “อัปเดต” เพื่อเก็บเว็บที่บันทึกไว้และการตั้งค่า")
                .setNegativeButton("ไว้ภายหลัง", null)
                .setPositiveButton("ดาวน์โหลด APK", (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.apkUrl())));
                    } catch (ActivityNotFoundException error) {
                        message("เปิดลิงก์ไม่ได้ โปรดตรวจว่าเครื่องมีเบราว์เซอร์สำหรับดาวน์โหลด");
                    }
                }).show();
    }

    private void message(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    void close() {
        visible = false;
        if (prompt != null) prompt.dismiss();
        worker.shutdownNow();
    }
}
