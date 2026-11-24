package com.hideurl.browser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

final class ReleaseFeed {
    static final String CONTENTS = "https://api.github.com/repos/shio-end/Poject-hide-url-browser/contents/downloads/";
    private static final String API = CONTENTS + "latest.properties?ref=main";
    private static final String RAW = PublishedVersion.REPOSITORY + "main/downloads/latest.properties";

    static PublishedVersion latest() throws IOException {
        long check = System.currentTimeMillis();
        PublishedVersion newest = null;
        for (String address : new String[]{API + "&check=" + check, RAW + "?check=" + check}) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Check cancelled");
            try {
                PublishedVersion candidate = read(address);
                if (newest == null || candidate.code > newest.code) newest = candidate;
            } catch (IOException | IllegalArgumentException failure) {
                // Either endpoint can be rate-limited, temporarily stale or unreachable.
            }
        }
        if (newest == null) throw new IOException("No valid release manifest");
        return newest;
    }

    private static PublishedVersion read(String address) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Feed unavailable");
            try (InputStream stream = connection.getInputStream()) {
                return PublishedVersion.read(stream);
            }
        } finally {
            connection.disconnect();
        }
    }
}
