package site.muyin.sso.clientlogin;

import java.time.Instant;

public record ClientLoginSession(
    String state,
    String codeVerifier,
    String codeChallenge,
    String returnUrl,
    Instant expiresAt
) {
}
