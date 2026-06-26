package site.muyin.sso.oauth.token;

import java.time.Instant;
import java.util.Set;

public record OAuthTokenClaims(
    String clientId,
    String subject,
    String username,
    String email,
    String displayName,
    String avatar,
    Set<String> roles,
    Set<String> scopes,
    Instant issuedAt,
    Instant expiresAt
) {
}
