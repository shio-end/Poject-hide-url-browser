package com.hideurl.browser;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import org.json.*;

final class BookmarkBackup {
    static String export(SharedPreferences prefs, Iterable<String> urls) throws JSONException {
        JSONArray entries = new JSONArray();
        for (String url : urls) entries.put(new JSONObject().put("url", url)
                .put("title", prefs.getString("title:" + url, "")));
        return new JSONObject().put("format", "VEIL-bookmarks").put("version", 1)
                .put("bookmarks", entries).toString(2);
    }

    static void write(ContentResolver resolver, Uri dest, String json) throws IOException {
        try (OutputStream out = resolver.openOutputStream(dest, "wt")) {
            if (out == null) throw new IOException();
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    static JSONArray read(ContentResolver resolver, Uri src) throws IOException, JSONException {
        try (InputStream in = resolver.openInputStream(src); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException();
            byte[] block = new byte[8192];
            int n;
            while ((n = in.read(block)) != -1) {
                if (bytes.size() + n > 16 * 1024 * 1024) throw new IOException("File too large");
                bytes.write(block, 0, n);
            }
            JSONObject doc = new JSONObject(bytes.toString("UTF-8"));
            if (!"VEIL-bookmarks".equals(doc.getString("format")) || doc.getInt("version") != 1)
                throw new JSONException("Unknown file format");
            return doc.getJSONArray("bookmarks");
        }
    }

    static int merge(SharedPreferences prefs, Iterable<String> existing, JSONArray entries) throws JSONException {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String url : existing) urls.add(url);
        int before = urls.size();
        SharedPreferences.Editor edit = prefs.edit();
        for (int i=0; i<entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String url = entry.getString("url");
            Uri uri = Uri.parse(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost()==null || uri.getHost().isEmpty()) throw new JSONException("Invalid URL");
            String title = entry.optString("title", "");
            if (!title.isEmpty() && !prefs.contains("title:" + url)) edit.putString("title:" + url, title);
            urls.add(url);
        }
        edit.putString("saved", new JSONArray(urls).toString()).apply();
        return urls.size() - before;
    }
}
