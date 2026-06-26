package site.muyin.sso.endpoint.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.client.ClientLoginStartResult;
import site.muyin.sso.service.ClientLoginService;
import site.muyin.sso.service.SsoLoginSessionService;

class ClientLoginRoutesTest {

    @Test
    void startsLoginFromSameOriginRedirectUri() {
        var clientLoginService = mock(ClientLoginService.class);
        var loginSessionService = mock(SsoLoginSessionService.class);
        when(clientLoginService.startLogin(anyString(), anyString()))
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
        verify(clientLoginService).startLogin(returnUrl.capture(), anyString());
        assertThat(returnUrl.getValue()).isEqualTo("/posts/1?tab=comments");
    }

    @Test
    void startsLoginFromLoginPageRefererRedirectUri() {
        var clientLoginService = mock(ClientLoginService.class);
        var loginSessionService = mock(SsoLoginSessionService.class);
        when(clientLoginService.startLogin(anyString(), anyString()))
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
        verify(clientLoginService).startLogin(returnUrl.capture(), anyString());
        assertThat(returnUrl.getValue()).isEqualTo("/posts/1");
    }

    private static WebTestClient webClient(ClientLoginService clientLoginService,
        SsoLoginSessionService loginSessionService) {
        return WebTestClient.bindToRouterFunction(
            new ClientLoginRoutes(clientLoginService, loginSessionService).publicRoutes()
        ).build();
    }
}
