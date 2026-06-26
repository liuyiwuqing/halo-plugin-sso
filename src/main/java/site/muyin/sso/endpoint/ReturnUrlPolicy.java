package site.muyin.sso.endpoint;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.UriComponentsBuilder;

public final class ReturnUrlPolicy {

    private ReturnUrlPolicy() {
    }

    public static String safeRedirectUriReturnUrl(ServerHttpRequest request) {
        return safeReturnUrl(request, request.getQueryParams().getFirst("redirect_uri"));
    }

    public static String safeLoginStartReturnUrl(ServerHttpRequest request) {
        var returnUrl = safeRedirectUriReturnUrl(request);
        if (request.getQueryParams().containsKey("redirect_uri") || !"/".equals(returnUrl)) {
            return returnUrl;
        }
        return safeLoginRefererReturnUrl(request);
    }

    private static String safeLoginRefererReturnUrl(ServerHttpRequest request) {
        var referer = request.getHeaders().getFirst("Referer");
        if (!hasText(referer)) {
            return "/";
        }
        try {
            var refererUri = URI.create(referer.trim());
            if (!sameOrigin(request, refererUri) || !"/login".equals(refererUri.getPath())) {
                return "/";
            }
            var redirectUri = UriComponentsBuilder.fromUri(refererUri)
                .build()
                .getQueryParams()
                .getFirst("redirect_uri");
            return safeReturnUrl(request, redirectUri);
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    private static String safeReturnUrl(ServerHttpRequest request, String redirectUri) {
        if (!hasText(redirectUri)) {
            return "/";
        }
        var trimmed = redirectUri.trim();
        var returnUrl = safeReturnUrlCandidate(request, trimmed);
        if (returnUrl != null) {
            return returnUrl;
        }
        var decoded = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
        if (!decoded.equals(trimmed)) {
            returnUrl = safeReturnUrlCandidate(request, decoded);
            if (returnUrl != null) {
                return returnUrl;
            }
        }
        return "/";
    }

    private static String safeReturnUrlCandidate(ServerHttpRequest request, String value) {
        if (isRelativeReturnUrl(value)) {
            return value;
        }
        try {
            var target = URI.create(value);
            return sameOrigin(request, target) ? pathAndQuery(target) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isRelativeReturnUrl(String value) {
        return value.startsWith("/") && !value.startsWith("//");
    }

    private static boolean sameOrigin(ServerHttpRequest request, URI target) {
        if (!hasText(target.getScheme()) || !hasText(target.getHost())) {
            return false;
        }
        var requestOrigin = requestOrigin(request);
        return target.getScheme().equalsIgnoreCase(requestOrigin.scheme())
            && target.getHost().equalsIgnoreCase(requestOrigin.host())
            && effectivePort(target) == requestOrigin.port();
    }

    private static RequestOrigin requestOrigin(ServerHttpRequest request) {
        var forwarded = request.getHeaders().getFirst("Forwarded");
        var forwardedProto = forwardedValue(forwarded, "proto");
        var forwardedHost = forwardedValue(forwarded, "host");
        if (hasText(forwardedProto) && hasText(forwardedHost)) {
            return origin(forwardedProto, forwardedHost);
        }

        var xForwardedProto = firstForwardedHeader(request, "X-Forwarded-Proto");
        var xForwardedHost = firstForwardedHeader(request, "X-Forwarded-Host");
        if (hasText(xForwardedProto) && hasText(xForwardedHost)) {
            return origin(xForwardedProto, xForwardedHost);
        }

        var uri = request.getURI();
        var host = uri.getHost();
        if (!hasText(host) && request.getHeaders().getHost() != null) {
            host = request.getHeaders().getHost().getHostString();
        }
        return new RequestOrigin(uri.getScheme(), host, effectivePort(uri));
    }

    private static RequestOrigin origin(String scheme, String hostHeader) {
        var host = hostHeader;
        var port = -1;
        var portSeparator = hostHeader.lastIndexOf(':');
        if (portSeparator > 0 && portSeparator < hostHeader.length() - 1) {
            host = hostHeader.substring(0, portSeparator);
            try {
                port = Integer.parseInt(hostHeader.substring(portSeparator + 1));
            } catch (NumberFormatException ignored) {
                port = -1;
            }
        }
        if (port < 0) {
            port = defaultPort(scheme);
        }
        return new RequestOrigin(scheme, host, port);
    }

    private static int effectivePort(URI uri) {
        var port = uri.getPort();
        return port >= 0 ? port : defaultPort(uri.getScheme());
    }

    private static int defaultPort(String scheme) {
        if (scheme == null) {
            return -1;
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    private static String pathAndQuery(URI uri) {
        var path = hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        var query = uri.getRawQuery();
        var fragment = uri.getRawFragment();
        if (hasText(query)) {
            path += "?" + query;
        }
        if (hasText(fragment)) {
            path += "#" + fragment;
        }
        return path;
    }

    private static String firstForwardedHeader(ServerHttpRequest request, String name) {
        var value = request.getHeaders().getFirst(name);
        if (!hasText(value)) {
            return null;
        }
        var commaIndex = value.indexOf(',');
        return commaIndex < 0 ? value.trim() : value.substring(0, commaIndex).trim();
    }

    private static String forwardedValue(String forwarded, String name) {
        if (!hasText(forwarded)) {
            return null;
        }
        var first = forwarded.split(",", 2)[0];
        for (var part : first.split(";")) {
            var trimmed = part.trim();
            var separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            var key = trimmed.substring(0, separator).trim();
            if (name.equalsIgnoreCase(key)) {
                return unquote(trimmed.substring(separator + 1).trim());
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RequestOrigin(String scheme, String host, int port) {
    }
}
