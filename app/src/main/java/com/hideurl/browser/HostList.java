package com.hideurl.browser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class HostList {
    private final Set<String> domains = new HashSet<>();

    HostList(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) domains.add(line);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable packaged domain list", e);
        }
    }

    boolean matches(String host) {
        if (host == null) return false;
        String suffix = host.toLowerCase(Locale.ROOT);
        if (suffix.endsWith(".")) suffix = suffix.substring(0, suffix.length() - 1);
        while (!domains.contains(suffix)) {
            int dot = suffix.indexOf('.');
            if (dot < 0) return false;
            suffix = suffix.substring(dot + 1);
        }
        return true;
    }
}
