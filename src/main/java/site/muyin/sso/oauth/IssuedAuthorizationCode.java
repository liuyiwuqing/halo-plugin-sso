package site.muyin.sso.oauth;

import java.time.Instant;

public record IssuedAuthorizationCode(String code, Instant expiresAt) {
}
