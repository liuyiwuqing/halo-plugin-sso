package site.muyin.sso.oauth.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OAuthTokenStoreTest {

    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void validatesAccessTokenAcrossStoreInstancesWithSameSigningKey() {
        var issuer = new OAuthTokenStore(CLOCK, new SecureRandom());
        var verifier = new OAuthTokenStore(CLOCK, new SecureRandom());

        var tokens = issuer.issue(claims(), "client-secret-hash-001");
        var verifiedClaims = verifier.findAccessToken(tokens.accessToken(),
            "client-secret-hash-001");

        assertThat(verifiedClaims).hasValueSatisfying(claims -> {
            assertThat(claims.clientId()).isEqualTo("site-b");
            assertThat(claims.subject()).isEqualTo("user-001");
            assertThat(claims.username()).isEqualTo("lywq");
            assertThat(claims.roles()).containsExactlyInAnyOrder("author", "subscriber");
            assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-06-26T00:15:00Z"));
        });
    }

    @Test
    void rejectsAccessTokenSignedWithDifferentKey() {
        var store = new OAuthTokenStore(CLOCK, new SecureRandom());

        var tokens = store.issue(claims(), "client-secret-hash-001");

        assertThat(store.findAccessToken(tokens.accessToken(), "client-secret-hash-002"))
            .isEmpty();
    }

    private static OAuthTokenClaims claims() {
        return new OAuthTokenClaims(
            "site-b",
            "user-001",
            "lywq",
            "lywq@example.com",
            "Lywq",
            "https://example.com/avatar.png",
            Set.of("author", "subscriber"),
            Set.of("openid", "profile", "email"),
            Instant.parse("2026-06-26T00:00:00Z"),
            null
        );
    }
}
