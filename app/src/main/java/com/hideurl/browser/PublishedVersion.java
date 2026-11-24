package com.hideurl.browser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class PublishedVersion {
    static final String REPOSITORY = "https://raw.githubusercontent.com/shio-end/Poject-hide-url-browser/";
    final long code;
    final String name;

    private PublishedVersion(long code, String name) {
        this.code = code;
        this.name = name;
    }

    static PublishedVersion read(InputStream stream) throws IOException {
        Properties values = new Properties();
        values.load(stream);
        String name = values.getProperty("versionName", "");
        if (!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("Invalid release name");
        try {
            long code = Long.parseLong(values.getProperty("versionCode", ""));
            if (code < 1) throw new IOException("Invalid version code");
            return new PublishedVersion(code, name);
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
