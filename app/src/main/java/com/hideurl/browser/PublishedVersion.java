package com.hideurl.browser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class PublishedVersion {
    static final String REPOSITORY = "https://raw.githubusercontent.com/shio-end/Poject-hide-url-browser/";
    final long code;
    final String name;

    final String notes;

    private PublishedVersion(long code, String name, String notes) {
        this.code = code;
        this.name = name;
        this.notes = notes;
    }

    static PublishedVersion read(InputStream stream) throws IOException {
        Properties values = new Properties();
        values.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        String name = values.getProperty("versionName", "");
        if (!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("Invalid release name");
        try {
            long code = Long.parseLong(values.getProperty("versionCode", ""));
            if (code < 1) throw new IOException("Invalid version code");
            return new PublishedVersion(code, name, values.getProperty("releaseNotes", "").trim());
        } catch (NumberFormatException error) {
            throw new IOException("Invalid version code", error);
        }
    }

    boolean isNewerThan(long installedCode) {
        return code > installedCode;
    }

    String apkUrl() {
        return REPOSITORY + "v" + name + "/downloads/Hide-URL-Browser.apk";
    }
}
