package com.hideurl.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public final class UpdateRestart extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE);
        try {
            long code = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
            if (prefs.getLong("restartFor", -1) != code) return;
            prefs.edit().remove("restartFor").putLong("completed", code).commit();
            UpdateApkProvider.apk(context).delete();
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            // Devices that restrict background launches allow reopening from the launcher.
        }
    }
}
