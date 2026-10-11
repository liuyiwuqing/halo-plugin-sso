package site.muyin.sso.endpoint.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.client.ClientLoginStartResult;
import site.muyin.sso.model.client.ClientLoginCallbackResult;
import site.muyin.sso.service.ClientLoginService;
import site.muyin.sso.service.SsoLoginSessionService;

class ClientLoginRoutesTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void passesRememberMeChoiceToLoginState(boolean rememberMe) {
        var login = mock(ClientLoginService.class);
        when(login.startLogin(anyString(), anyString(), anyString(), anyBoolean()))
            .thenReturn(Mono.just(ClientLoginStartResult.builder()
                .redirectUri("https://auth.example.com/oauth/authorize").build()));

        webClient(login, mock(SsoLoginSessionService.class)).get()
            .uri("https://client.example.com/login?remember-me=" + rememberMe)
            .exchange().expectStatus().isTemporaryRedirect();

        verify(login).startLogin(anyString(), anyString(), anyString(), eq(rememberMe));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void usesConsumedStatePreferenceInsteadOfCallbackQuery(boolean rememberMe) {
        var login = mock(ClientLoginService.class);
        var sessions = mock(SsoLoginSessionService.class);
        when(login.handleCallback(eq("code-001"), eq("state-001"), anyString()))
            .thenReturn(Mono.just(ClientLoginCallbackResult.builder()
                .localUsername("user-001").returnUrl("/posts/1")
                .rememberMe(rememberMe).build()));
        when(sessions.login(any(), anyString(), anyBoolean())).thenReturn(Mono.empty());

        webClient(login, sessions).get()
            .uri("https://client.example.com/callback?code=code-001&state=state-001"
                + "&remember-me=" + !rememberMe)
            .exchange().expectStatus().isTemporaryRedirect()
            .expectHeader().location("/posts/1");

        verify(sessions).login(any(), eq("user-001"), eq(rememberMe));
    }

    @Test
    void startsLoginFromSameOriginRedirectUri() {
        var clientLoginService = mock(ClientLoginService.class);
        var loginSessionService = mock(SsoLoginSessionService.class);
        when(clientLoginService.startLogin(anyString(), anyString(), anyString(), anyBoolean()))
            .thenReturn(Mono.just(ClientLoginStartResult.builder()
                .redirectUri("https://auth.example.com/oauth/authorize")
                .build()));

        webClient(clientLoginService, loginSessionService)
            .get()
            .uri("/login?redirect_uri=https%3A%2F%2Fclient.example.com%2Fposts%2F1%3Ftab%3Dcomments")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "client.example.com")
            .exchange()
            .expectStatus().isTemporaryRedirect()
            .expectHeader().location("https://auth.example.com/oauth/authorize");

        var returnUrl = ArgumentCaptor.forClass(String.class);
        verify(clientLoginService).startLogin(returnUrl.capture(), anyString(), anyString(), eq(false));
        assertThat(returnUrl.getValue()).isEqualTo("/posts/1?tab=comments");
    }

    @Test
    void startsLoginFromLoginPageRefererRedirectUri() {
        var clientLoginService = mock(ClientLoginService.class);
        var loginSessionService = mock(SsoLoginSessionService.class);
        when(clientLoginService.startLogin(anyString(), anyString(), anyString(), anyBoolean()))
            .thenReturn(Mono.just(ClientLoginStartResult.builder()
                .redirectUri("https://auth.example.com/oauth/authorize")
                .build()));

        webClient(clientLoginService, loginSessionService)
            .get()
            .uri("/login")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "client.example.com")
            .header("Referer",
                "https://client.example.com/login"
                    + "?redirect_uri=https%3A%2F%2Fclient.example.com%2Fposts%2F1")
            .exchange()
            .expectStatus().isTemporaryRedirect();

        var returnUrl = ArgumentCaptor.forClass(String.class);
        verify(clientLoginService).startLogin(returnUrl.capture(), anyString(), anyString(), eq(false));
        assertThat(returnUrl.getValue()).isEqualTo("/posts/1");
    }

    @Test
    void doesNotTrustRawForwardedForHeaderForRequesterQuota() {
        var clientLoginService = mock(ClientLoginService.class);
        var loginSessionService = mock(SsoLoginSessionService.class);
        when(clientLoginService.startLogin(anyString(), anyString(), anyString(), anyBoolean()))
            .thenReturn(Mono.just(ClientLoginStartResult.builder()
                .redirectUri("https://auth.example.com/oauth/authorize")
                .build()));

        webClient(clientLoginService, loginSessionService)
            .get()
            .uri("/login")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "client.example.com")
            .header("X-Forwarded-For", "198.51.100.23")
            .exchange()
            .expectStatus().isTemporaryRedirect();

        var requesterKey = ArgumentCaptor.forClass(String.class);
        verify(clientLoginService).startLogin(anyString(), anyString(), requesterKey.capture(), eq(false));
        assertThat(requesterKey.getValue()).isNotEqualTo("198.51.100.23");
    }

    private static WebTestClient webClient(ClientLoginService clientLoginService,
        SsoLoginSessionService loginSessionService) {
        return WebTestClient.bindToRouterFunction(
            new ClientLoginRoutes(clientLoginService, loginSessionService).publicRoutes()
        ).build();
    }
}
