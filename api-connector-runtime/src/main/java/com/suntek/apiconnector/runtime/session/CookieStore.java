/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.session;

import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Session-owned cookie authority. Outbound Cookie headers MUST come from here.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class CookieStore {

    private final List<HttpCookie> cookies = new ArrayList<>();

    /**
     * Accepts Set-Cookie values from a response against the request URI.
     *
     * @param requestUri request URI that received the cookies
     * @param setCookie  Set-Cookie header values
     */
    public synchronized void acceptSetCookie(URI requestUri, List<String> setCookie) {
        Objects.requireNonNull(requestUri, "requestUri");
        if (setCookie == null || setCookie.isEmpty()) {
            return;
        }
        for (String header : setCookie) {
            if (header == null || header.isBlank()) {
                continue;
            }
            List<HttpCookie> parsed;
            try {
                parsed = HttpCookie.parse(header);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            for (HttpCookie cookie : parsed) {
                if (cookie.getDomain() == null || cookie.getDomain().isBlank()) {
                    cookie.setDomain(requestUri.getHost());
                }
                if (cookie.getPath() == null || cookie.getPath().isBlank()) {
                    cookie.setPath(defaultPath(requestUri));
                }
                replace(cookie);
            }
        }
    }

    /**
     * @param requestUri outbound URI
     * @return Cookie header value, or empty when none match
     */
    public synchronized String cookiesFor(URI requestUri) {
        Objects.requireNonNull(requestUri, "requestUri");
        purgeExpired();
        return cookies.stream()
                .filter(cookie -> matches(cookie, requestUri))
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
    }

    /**
     * @return a copy of this store
     */
    public synchronized CookieStore copy() {
        CookieStore copy = new CookieStore();
        for (HttpCookie cookie : cookies) {
            copy.cookies.add((HttpCookie) cookie.clone());
        }
        return copy;
    }

    private void replace(HttpCookie incoming) {
        cookies.removeIf(existing ->
                existing.getName().equalsIgnoreCase(incoming.getName())
                        && Objects.equals(normalizeDomain(existing.getDomain()), normalizeDomain(incoming.getDomain()))
                        && Objects.equals(existing.getPath(), incoming.getPath())
        );
        cookies.add(incoming);
    }

    private void purgeExpired() {
        Iterator<HttpCookie> it = cookies.iterator();
        while (it.hasNext()) {
            if (it.next().hasExpired()) {
                it.remove();
            }
        }
    }

    private static boolean matches(HttpCookie cookie, URI uri) {
        if (cookie.getSecure() && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String domain = normalizeDomain(cookie.getDomain());
        if (domain != null && !HttpCookie.domainMatches(domain, host) && !host.equals(domain)) {
            return false;
        }
        String path = cookie.getPath() == null || cookie.getPath().isBlank() ? "/" : cookie.getPath();
        String uriPath = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
        return uriPath.equals(path) || uriPath.startsWith(path.endsWith("/") ? path : path + "/");
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return null;
        }
        String trimmed = domain.startsWith(".") ? domain.substring(1) : domain;
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String defaultPath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return "/";
        }
        int slash = path.lastIndexOf('/');
        if (slash <= 0) {
            return "/";
        }
        return path.substring(0, slash);
    }
}
