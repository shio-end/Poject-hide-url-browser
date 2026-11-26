package com.hideurl.browser;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class HttpOrigin {
    private HttpOrigin() {}

    static boolean permits(String approvedOrigin, String scheme, String url, boolean mainFrame) {
        if ("https".equalsIgnoreCase(scheme)) return approvedOrigin == null || !mainFrame;
        return approvedOrigin != null && "http".equalsIgnoreCase(scheme) && approvedOrigin.equals(of(url));
    }

    // Use an unambiguous ASCII authority both in the consent sheet and in Chromium's route.
    static String of(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url).parseServerAuthority();
            String host = uri.getHost();
            int port = uri.getPort();
            if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                    || uri.getRawAuthority().endsWith(":") || host.contains("%") || port == 0 || port > 65535) return null;
            if (host.startsWith("[")) {
                InetAddress ip = InetAddress.getByName(host);
                if (ip.getAddress().length != 16) return null;
                host = "[" + ip.getHostAddress() + "]";
            } else if (host.matches("(?i)(?:.*\\.)?(?:[0-9]+|0x[0-9a-f]*)\\.?")) {
                // Reject alternate IPv4 spellings that Chromium would rewrite after consent.
                String[] octets = host.split("\\.", -1);
                if (octets.length != 4) return null;
                for (String octet : octets) {
                    if (!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet) > 255) return null;
                }
            }
            return "http://" + host.toLowerCase(Locale.ROOT) + ":" + (port == -1 ? 80 : port);
        } catch (URISyntaxException | UnknownHostException malformed) {
            return null;
        }
    }
}
