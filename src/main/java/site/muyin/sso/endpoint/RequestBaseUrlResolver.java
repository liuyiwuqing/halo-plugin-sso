package site.muyin.sso.endpoint;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

public final class RequestBaseUrlResolver {

    private RequestBaseUrlResolver() {
    }

    public static String resolve(ServerRequest request) {
        var forwarded = request.headers().firstHeader("Forwarded");
        var forwardedProto = forwardedValue(forwarded, "proto");
        var forwardedHost = forwardedValue(forwarded, "host");
        if (hasText(forwardedProto) && hasText(forwardedHost)) {
            return forwardedProto + "://" + forwardedHost;
        }

        var xForwardedProto = firstForwardedHeader(request, "X-Forwarded-Proto");
        var xForwardedHost = firstForwardedHeader(request, "X-Forwarded-Host");
        if (hasText(xForwardedProto) && hasText(xForwardedHost)) {
            return xForwardedProto + "://" + xForwardedHost;
        }

        URI uri = request.uri();
        if (!hasText(uri.getScheme()) || !hasText(uri.getRawAuthority())) {
            return "";
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    public static String require(ServerRequest request) {
        var baseUrl = resolve(request);
        if (!hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法识别当前站点访问地址");
        }
        return baseUrl;
    }

    private static String firstForwardedHeader(ServerRequest request, String name) {
        var value = request.headers().firstHeader(name);
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
            if (!name.equalsIgnoreCase(key)) {
                continue;
            }
            return unquote(trimmed.substring(separator + 1).trim());
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
}
