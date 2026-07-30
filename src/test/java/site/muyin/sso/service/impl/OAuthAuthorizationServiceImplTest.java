package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.client.ClientSecretVerificationBusyException;
import site.muyin.sso.model.oauth.CenterUserClaim;
import site.muyin.sso.model.oauth.OAuthAuthorizeRequest;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.oauth.AuthorizationCodeManager;
import site.muyin.sso.oauth.Pkce;
import site.muyin.sso.oauth.token.OAuthTokenStore;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.service.CenterUserClaimService;
import site.muyin.sso.service.SsoClientService;
import site.muyin.sso.setting.SsoGeneralSetting;

class OAuthAuthorizationServiceImplTest {

    @Test
    void authorizesExchangesTokenAndReturnsUserInfo() {
        var clientService = mock(SsoClientService.class);
        var userClaimService = mock(CenterUserClaimService.class);
        var authorizationCodeManager = AuthorizationCodeManager.inMemory(
            Clock.fixed(Instant.parse("2026-06-24T08:00:00Z"), ZoneOffset.UTC));
        var settingFetcher = centerModeSettingFetcher();
        var service = new OAuthAuthorizationServiceImpl(
            clientService,
            userClaimService,
            authorizationCodeManager,
            new OAuthTokenStore(),
            settingFetcher
        );
        var redirectUri = "https://b.example.com/plugins/sso/callback";
        var codeVerifier = "test-verifier-with-enough-entropy";

        when(clientService.requireAuthorizedClientWithRX("site-b", redirectUri))
            .thenReturn(Mono.just(new SsoClient().setClientId("site-b")));
        when(userClaimService.currentUser())
            .thenReturn(Mono.just(CenterUserClaim.builder()
                .subject("user-001")
                .username("lywq")
                .email("lywq@example.com")
                .displayName("Lywq")
                .avatar("https://example.com/avatar.png")
                .roles(Set.of("author", "subscriber"))
                .build()));
        when(clientService.verifySecretWithRX("site-b", "client-secret"))
            .thenReturn(Mono.just(true));
        when(clientService.getByClientIdWithRX("site-b"))
            .thenReturn(Mono.just(new SsoClient()
                .setClientId("site-b")
                .setClientSecretHash("client-secret-hash-001")
                .setEnabled(true)));

        var authorizeResult = service.authorize(OAuthAuthorizeRequest.builder()
            .responseType("code")
            .clientId("site-b")
            .redirectUri(redirectUri)
            .scope("openid profile email")
            .state("state-001")
            .codeChallenge(Pkce.challengeS256(codeVerifier))
            .codeChallengeMethod("S256")
            .build()).block();

        var params = UriComponentsBuilder.fromUri(URI.create(authorizeResult.getRedirectUri()))
            .build()
            .getQueryParams();
        var code = params.getFirst("code");
        assertThat(code).isNotBlank();
        assertThat(params.getFirst("state")).isEqualTo("state-001");

        var tokenResponse = service.token(OAuthTokenRequest.builder()
            .grantType("authorization_code")
            .code(code)
            .redirectUri(redirectUri)
            .clientId("site-b")
            .clientSecret("client-secret")
            .codeVerifier(codeVerifier)
            .build()).block();

        assertThat(tokenResponse.getAccessToken()).isNotBlank();
        assertThat(tokenResponse.getTokenType()).isEqualTo("Bearer");

        var userInfo = service.userInfo(tokenResponse.getAccessToken()).block();
        assertThat(userInfo.getSub()).isEqualTo("user-001");
        assertThat(userInfo.getPreferredUsername()).isEqualTo("lywq");
        assertThat(userInfo.getEmail()).isEqualTo("lywq@example.com");
        assertThat(userInfo.getName()).isEqualTo("Lywq");
        assertThat(userInfo.getPicture()).isEqualTo("https://example.com/avatar.png");
        assertThat(userInfo.getRoles()).containsExactlyInAnyOrder("author", "subscriber");
    }

    @Test
    void rejectsOAuthOperationsWhenPluginRunsInClientMode() {
        var clientService = mock(SsoClientService.class);
        var userClaimService = mock(CenterUserClaimService.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        var service = new OAuthAuthorizationServiceImpl(
            clientService,
            userClaimService,
            AuthorizationCodeManager.inMemory(Clock.systemUTC()),
            new OAuthTokenStore(),
            settingFetcher
        );

        assertThatThrownBy(() -> service.authorize(OAuthAuthorizeRequest.builder()
                .responseType("code")
                .clientId("site-b")
                .redirectUri("https://b.example.com/callback")
                .state("state-001")
                .codeChallenge(Pkce.challengeS256("test-verifier-with-enough-entropy"))
                .codeChallengeMethod("S256")
                .build()).block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.token(OAuthTokenRequest.builder()
                .grantType("authorization_code")
                .code("code-001")
                .redirectUri("https://b.example.com/callback")
                .clientId("site-b")
                .clientSecret("secret-b")
                .codeVerifier("test-verifier-with-enough-entropy")
                .build()).block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.userInfo("access-001").block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(clientService, userClaimService);
    }

    @Test
    void returnsTooManyRequestsWhenSecretVerificationIsBusy() {
        var clientService = mock(SsoClientService.class);
        when(clientService.verifySecretWithRX("site-b", "client-secret"))
            .thenReturn(Mono.error(new ClientSecretVerificationBusyException()));
        var service = new OAuthAuthorizationServiceImpl(
            clientService,
            mock(CenterUserClaimService.class),
            AuthorizationCodeManager.inMemory(Clock.systemUTC()),
            new OAuthTokenStore(),
            centerModeSettingFetcher()
        );

        assertThatThrownBy(() -> service.token(OAuthTokenRequest.builder()
                .grantType("authorization_code")
                .code("code-001")
                .redirectUri("https://b.example.com/callback")
                .clientId("site-b")
                .clientSecret("client-secret")
                .codeVerifier("test-verifier-with-enough-entropy")
                .build()).block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private static ReactiveSettingFetcher centerModeSettingFetcher() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("center");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        return settingFetcher;
    }
}
