package com.devfolio.util;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Set;

public final class UrlValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "raw.githubusercontent.com", "api.github.com", "github.com",
        "i.imgur.com", "avatars.githubusercontent.com");

    private static final long MAX_FETCH_SIZE = 2 * 1024 * 1024; // 2 Mo

    private UrlValidator() {}

    public static URL validate(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            if (!"https".equals(uri.getScheme())) {
                throw new IllegalArgumentException("Seul HTTPS est autorisé");
            }
            String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host)) {
                throw new IllegalArgumentException("Domaine non autorisé : " + host);
            }
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Adresse IP interdite");
            }
            return uri.toURL();
        } catch (URISyntaxException | MalformedURLException | UnknownHostException e) {
            throw new IllegalArgumentException("URL invalide", e);
        }
    }

    public static long getMaxFetchSize() {
        return MAX_FETCH_SIZE;
    }
}
