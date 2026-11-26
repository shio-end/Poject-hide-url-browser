package com.hideurl.browser;

import org.junit.Test;
import static org.junit.Assert.*;

public class HttpOriginTest {
    @Test public void matchesOnlyTheConsentedHttpHostAndEffectivePort() {
        String origin = HttpOrigin.of("HTTP://Example.org/path?item=2#text");
        assertEquals("http://example.org:80", origin);
        assertEquals(origin, HttpOrigin.of("http://example.org:80/another"));
        assertNotEquals(origin, HttpOrigin.of("http://example.org:1818/"));
        assertNotEquals(origin, HttpOrigin.of("http://example.org.attacker.invalid/"));
        assertEquals("http://192.0.2.3:1818", HttpOrigin.of("http://192.0.2.3:1818/login"));
        assertEquals(HttpOrigin.of("http://[::1]:81/a"), HttpOrigin.of("http://[0:0:0:0:0:0:0:1]:81/b"));
        assertNotNull(HttpOrigin.of("http://[::1]:81"));
    }

    @Test public void requiresConsentForRequestsAndNeverSharesItWithSecureTabs() {
        String url = "http://example.org:1818/page";
        String grant = HttpOrigin.of(url);
        assertFalse(HttpOrigin.permits(null, "http", url, true));
        assertFalse(HttpOrigin.permits(null, "http", url, false));
        assertTrue(HttpOrigin.permits(null, "https", "https://elsewhere.test/", true));
        assertTrue(HttpOrigin.permits(grant, "http", url, true));
        assertTrue(HttpOrigin.permits(grant, "http", url, false));
        assertFalse(HttpOrigin.permits(grant, "http", "http://example.org:1819/image", false));
        assertFalse(HttpOrigin.permits(grant, "http", "http://other.test:1818/form", true));
        assertFalse(HttpOrigin.permits(grant, "file", "file:///sdcard/photo", false));
        assertFalse(HttpOrigin.permits(grant, "https", "https://elsewhere.test/", true));
        assertTrue(HttpOrigin.permits(grant, "https", "https://cdn.example.org/style.css", false));
    }

    @Test public void routesOnlyEncryptedSchemesDirectlyInPrimaryProcess() {
        androidx.webkit.ProxyConfig rules = WebRoutes.secureConfig(null, "127.0.0.1:10080");
        assertEquals(java.util.Arrays.asList("<-loopback>", "https://*", "wss://*"), rules.getBypassRules());
        assertEquals(1, rules.getProxyRules().size());
        assertEquals("127.0.0.1:10080", rules.getProxyRules().get(0).getUrl());
    }

    @Test public void rejectsAmbiguousAuthoritiesAndCredentials() {
        String[] urls = {null, "https://example.org/", "file:///tmp/", "http:///",
                "http://example.org:/", "http://example.org:0", "http://example.org:65536/",
                "http://user:pass@example.org", "http://example.org%40other.test",
                "http://example.org\\@other.test/", "http://*.example.org/",
                "http://[fe80::1%25wlan0]/", "http://2130706433/", "http://127.1/",
                "http://127.000.000.001/", "http://0x7f000001/"};
        for (String url : urls) assertNull(url, HttpOrigin.of(url));
    }
}
