package site.muyin.sso.client;

import java.net.URI;
import java.util.List;

public final class RedirectUriPolicy {

    private RedirectUriPolicy() {
    }

    public static List<String> normalize(List<String> redirectUris) {
        return normalize(redirectUris, true);
    }

    public static List<String> normalize(List<String> redirectUris, boolean allowHttpForLocalhost) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new IllegalArgumentException("redirectUris must not be empty");
        }
        return redirectUris.stream()
            .map(redirectUri -> normalizeOne(redirectUri, allowHttpForLocalhost))
            .distinct()
            .toList();
    }

    public static boolean isAllowed(List<String> allowedRedirectUris, String redirectUri) {
        return isAllowed(allowedRedirectUris, redirectUri, true);
    }

    public static boolean isAllowed(List<String> allowedRedirectUris, String redirectUri,
        boolean allowHttpForLocalhost) {
        if (allowedRedirectUris == null || redirectUri == null || redirectUri.isBlank()) {
            return false;
        }
        return allowedRedirectUris.contains(normalizeOne(redirectUri, allowHttpForLocalhost));
    }

    private static String normalizeOne(String redirectUri, boolean allowHttpForLocalhost) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be blank");
        }
        if (redirectUri.contains("*")) {
            throw new IllegalArgumentException("redirectUri must not contain wildcard");
        }
        var parsed = URI.create(redirectUri);
        var scheme = parsed.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
            && !isAllowedLocalhostHttp(parsed, allowHttpForLocalhost)) {
            throw new IllegalArgumentException("redirectUri must use HTTPS outside localhost");
        }
        return parsed.toString();
    }

    private static boolean isAllowedLocalhostHttp(URI uri, boolean allowHttpForLocalhost) {
        if (!allowHttpForLocalhost || !"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        var host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }
}
