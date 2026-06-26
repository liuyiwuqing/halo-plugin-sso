package site.muyin.sso.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationCodeManagerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-24T08:00:00Z"),
        ZoneOffset.UTC);

    @Test
    void issuesAndConsumesAuthorizationCodeOnceWhenPkceMatches() {
        var manager = AuthorizationCodeManager.inMemory(CLOCK);
        var verifier = "test-verifier-with-enough-entropy";
        var challenge = Pkce.challengeS256(verifier);

        var code = manager.issue(AuthorizationCodeIssueRequest.builder()
            .clientId("site-b")
            .redirectUri("https://b.example.com/plugins/sso/callback")
            .subject("user-center-001")
            .username("lywq")
            .email("user@example.com")
            .displayName("Lywq")
            .avatar("https://example.com/avatar.png")
            .roles(Set.of("author", "subscriber"))
            .codeChallenge(challenge)
            .scopes(Set.of("openid", "profile", "email"))
            .build());

        var grant = manager.consume(AuthorizationCodeConsumeRequest.builder()
            .code(code.code())
            .clientId("site-b")
            .redirectUri("https://b.example.com/plugins/sso/callback")
            .codeVerifier(verifier)
            .build());

        assertThat(grant.subject()).isEqualTo("user-center-001");
        assertThat(grant.username()).isEqualTo("lywq");
        assertThat(grant.email()).isEqualTo("user@example.com");
        assertThat(grant.displayName()).isEqualTo("Lywq");
        assertThat(grant.avatar()).isEqualTo("https://example.com/avatar.png");
        assertThat(grant.roles()).containsExactlyInAnyOrder("author", "subscriber");
        assertThat(grant.scopes()).containsExactlyInAnyOrder("openid", "profile", "email");

        assertThatThrownBy(() -> manager.consume(AuthorizationCodeConsumeRequest.builder()
            .code(code.code())
            .clientId("site-b")
            .redirectUri("https://b.example.com/plugins/sso/callback")
            .codeVerifier(verifier)
            .build()))
            .isInstanceOf(AuthorizationCodeException.class)
            .hasMessageContaining("used");
    }
}
