package site.muyin.sso.oauth.token;

import java.time.Instant;

public record IssuedOAuthTokens(
    String accessToken,
    String idToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt
) {
}
