package com.hideurl.browser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.IntConsumer;
import javax.net.ssl.HttpsURLConnection;

final class UpdateDownload {
    static void fetch(Context context, PublishedVersion version, IntConsumer progress) throws IOException {
        try {
            fetch(context, version, progress, version.apkUrl());
        } catch (IOException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            fetch(context, version, progress, ReleaseFeed.CONTENTS + "Hide-URL-Browser.apk?ref=v" + version.name);
        }
    }

    private static void fetch(Context context, PublishedVersion version, IntConsumer progress, String url) throws IOException {
        File temp = File.createTempFile("update-", ".apk", context.getCacheDir());
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            if (connection.getResponseCode() != 200) throw new IOException("Download failed");
            long size = connection.getContentLengthLong();
            long total = 0;
            int last = -1;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Interrupted");
                    out.write(buffer, 0, count);
                    total += count;
                    if (size > 0) {
                        int percent = (int) Math.min(99, total * 100 / size);
                        if (percent != last) progress.accept(percent);
                        last = percent;
                    }
                }
            }
            if (total == 0 || (size >= 0 && total != size)) throw new IOException("Incomplete download");
            verify(context, temp, version.code);
            if (Thread.currentThread().isInterrupted() || !temp.renameTo(UpdateApkProvider.apk(context))) {
                throw new IOException("Could not stage update");
            }
        } finally {
            connection.disconnect();
            temp.delete();
        }
    }

    static void verify(Context context, File file, long versionCode) throws IOException {
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo candidate = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        try {
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            if (candidate == null || !installed.packageName.equals(candidate.packageName)
                    || candidate.getLongVersionCode() != versionCode || versionCode <= installed.getLongVersionCode()
                    || candidate.signingInfo == null || installed.signingInfo == null) throw new IOException("Wrong APK");
            Signature[] expected = installed.signingInfo.getApkContentsSigners();
            Signature[] actual = candidate.signingInfo.getApkContentsSigners();
            if (actual.length == 0 || !new HashSet<>(Arrays.asList(expected)).equals(new HashSet<>(Arrays.asList(actual)))) {
                throw new IOException("Wrong signer");
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException(error);
        }
    }
}
