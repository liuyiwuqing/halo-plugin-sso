package site.muyin.sso.oauth;

import java.time.Instant;
import java.util.Set;

public record AuthorizationCodeGrant(
    String clientId,
    String redirectUri,
    String subject,
    String username,
    String email,
    String displayName,
    String avatar,
    Set<String> roles,
    Set<String> scopes,
    Instant issuedAt
) {
}
