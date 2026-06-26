package site.muyin.sso.query;

import org.springframework.web.reactive.function.server.ServerRequest;

public record SsoAuditLogQuery(
    String outcome,
    String clientId,
    String keyword,
    int page,
    int size
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    public static SsoAuditLogQuery from(ServerRequest request) {
        var params = request.queryParams();
        return new SsoAuditLogQuery(
            normalize(params.getFirst("outcome")),
            normalize(params.getFirst("clientId")),
            normalize(params.getFirst("keyword")),
            normalizePage(params.getFirst("page")),
            normalizeSize(params.getFirst("size"))
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int normalizePage(String value) {
        var page = parsePositiveInt(value, DEFAULT_PAGE);
        return Math.max(DEFAULT_PAGE, page);
    }

    private static int normalizeSize(String value) {
        var size = parsePositiveInt(value, DEFAULT_SIZE);
        return Math.min(MAX_SIZE, Math.max(1, size));
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
