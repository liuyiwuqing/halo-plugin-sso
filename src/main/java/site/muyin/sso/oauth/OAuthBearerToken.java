package site.muyin.sso.oauth;

public final class OAuthBearerToken {

    private static final String BEARER_PREFIX = "Bearer ";

    public static String fromAuthorizationHeader(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        var value = authorization.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        var token = value.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    private OAuthBearerToken() {
    }
}
