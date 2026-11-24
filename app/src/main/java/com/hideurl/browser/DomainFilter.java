package com.hideurl.browser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DomainFilter {
    private static final Set<String> ADS = domains(
            "doubleclick.net googlesyndication.com googleadservices.com googletagservices.com " +
            "adnxs.com adsrvr.org rubiconproject.com pubmatic.com openx.net casalemedia.com " +
            "criteo.com criteo.net taboola.com outbrain.com adform.net advertising.com " +
            "amazon-adsystem.com media.net smartadserver.com adsafeprotected.com " +
            "moatads.com lijit.com sovrn.com sharethrough.com indexww.com " +
            "popads.net popcash.net propellerads.com exoclick.com exosrv.com teads.tv " +
            "trafficjunky.net mgid.com adroll.com serving-sys.com 2mdn.net " +
            "ads-twitter.com adcolony.com applovin.com unityads.unity3d.com");
    private static final Set<String> TRACKERS = domains(
            "google-analytics.com analytics.google.com app-measurement.com " +
            "scorecardresearch.com quantserve.com hotjar.com hotjar.io clarity.ms " +
            "api.mixpanel.com api-js.mixpanel.com cdn.mxpnl.com api.amplitude.com " +
            "api2.amplitude.com api.segment.io cdn.segment.com fullstory.com mouseflow.com " +
            "heapanalytics.com chartbeat.com chartbeat.net demdex.net omtrdc.net " +
            "bluekai.com krxd.net rlcdn.com tapad.com analytics.tiktok.com " +
            "px.ads.linkedin.com bat.bing.com pixel.wp.com mc.yandex.ru");

    private static Set<String> domains(String values) {
        return new HashSet<>(Arrays.asList(values.split(" ")));
    }

    static boolean blocks(String hostname, boolean ads, boolean trackers) {
        if (hostname == null || (!ads && !trackers)) return false;
        String host = hostname.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        while (!host.isEmpty()) {
            if ((ads && ADS.contains(host)) || (trackers && TRACKERS.contains(host))) return true;
            int dot = host.indexOf('.');
            if (dot < 0) break;
            host = host.substring(dot + 1);
        }
        return false;
    }
}
