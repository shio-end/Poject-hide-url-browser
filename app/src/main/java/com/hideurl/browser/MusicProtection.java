package com.hideurl.browser;

import android.content.res.Resources;
import android.webkit.WebView;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

final class MusicProtection {
    private final Resources resources;
    private final List<ScriptHandler> registrations = new ArrayList<>();
    private final boolean early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
    private boolean youtubeOn, spotifyOn;
    private String youtube;
    private String spotify;

    MusicProtection(Resources resources) { this.resources = resources; }

    void configure(WebView view, boolean blockAds, boolean allowYoutubeRules, boolean allowSpotifyRules) {
        boolean yt = blockAds && allowYoutubeRules, sp = blockAds && allowSpotifyRules;
        if (youtubeOn == yt && spotifyOn == sp) return;
        youtubeOn = yt;
        spotifyOn = sp;
        for (ScriptHandler registration : registrations) registration.remove();
        registrations.clear();
        if (yt || sp) {
            if (youtube == null) {
                youtube = read(R.raw.adguard_youtube);
                spotify = read(R.raw.adguard_spotify);
            }
            if (early) {
                if (yt) register(view, youtube, "https://music.youtube.com");
                if (sp) register(view, spotify, "https://open.spotify.com");
            }
        }
        if (isMusic(view.getUrl())) view.reload();
    }

    private void register(WebView view, String script, String origin) {
        registrations.add(WebViewCompat.addDocumentStartJavaScript(view, script, Collections.singleton(origin)));
    }

    private String read(int resource) {
        try (Scanner input = new Scanner(resources.openRawResource(resource), "UTF-8")) {
            return input.useDelimiter("\\A").next();
        }
    }

    private static boolean isMusic(String url) {
        return url != null && (url.startsWith("https://music.youtube.com/") || url.startsWith("https://open.spotify.com/"));
    }

    void fallback(WebView view, String url) {
        if (!early && isMusic(url) && (url.startsWith("https://music.youtube.com/") ? youtubeOn : spotifyOn)) {
            view.evaluateJavascript(url.startsWith("https://music.youtube.com/") ? youtube : spotify, null);
        }
    }
}
