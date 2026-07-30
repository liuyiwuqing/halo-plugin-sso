package site.muyin.sso.endpoint.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import site.muyin.sso.service.OAuthAuthorizationService;
import site.muyin.sso.model.oauth.OAuthTokenResponse;

class OAuthRoutesTest {

    @Test
    void redirectsAnonymousAuthorizeRequestToCenterLoginAndPreservesAuthorizeUrl() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.authorize(any()))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "用户未登录身份中心")));

        var location = webClient(authorizationService)
            .get()
            .uri("/authorize?response_type=code&client_id=site-b"
                + "&redirect_uri=https%3A%2F%2Fb.example.com%2Fcallback"
                + "&state=state-001&code_challenge=challenge-001"
                + "&code_challenge_method=S256")
            .exchange()
            .expectStatus().isTemporaryRedirect()
            .expectHeader().value("Location", value -> assertThat(value).startsWith("/login?"))
            .returnResult(Void.class)
            .getResponseHeaders()
            .getLocation();

        var loginParams = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        var redirectUri = URLDecoder.decode(loginParams.getFirst("redirect_uri"),
            StandardCharsets.UTF_8);

        assertThat(redirectUri).isNotBlank();
        assertThat(URI.create(redirectUri).getPath()).isEqualTo("/authorize");
        assertThat(redirectUri)
            .contains("client_id=site-b")
            .contains("state=state-001")
            .contains("code_challenge=challenge-001");
    }

    @Test
    void redirectsUnverifiedEmailAuthorizeRequestToNoticePage() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.authorize(any()))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "用户邮箱未验证，不能跨站登录")));

        var location = webClient(authorizationService)
            .get()
            .uri("/authorize?response_type=code&client_id=site-b"
                + "&redirect_uri=https%3A%2F%2Fb.example.com%2Fcallback"
                + "&state=state-001&code_challenge=challenge-001"
                + "&code_challenge_method=S256")
            .exchange()
            .expectStatus().isTemporaryRedirect()
            .expectHeader().value("Location", value -> assertThat(value).startsWith("/apis/public.sso.muyin.site/v1alpha1/oauth/notice?"))
            .returnResult(Void.class)
            .getResponseHeaders()
            .getLocation();

        var noticeParams = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        var returnTo = URLDecoder.decode(noticeParams.getFirst("return_to"),
            StandardCharsets.UTF_8);

        assertThat(noticeParams.getFirst("code")).isEqualTo("email_not_verified");
        assertThat(returnTo).isNotBlank();
        assertThat(URI.create(returnTo).getPath()).isEqualTo("/authorize");
        assertThat(returnTo)
            .contains("client_id=site-b")
            .contains("state=state-001")
            .contains("code_challenge=challenge-001");
    }

    @Test
    void rendersFriendlyNoticePageForUnverifiedEmail() {
        webClient(mock(OAuthAuthorizationService.class))
            .get()
            .uri("/notice?code=email_not_verified&return_to=%2Fapis%2Fpublic.sso.muyin.site%2F"
                + "v1alpha1%2Foauth%2Fauthorize%3Fresponse_type%3Dcode")
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("邮箱未验证，暂时无法继续登录")
                .contains("当前账号邮箱未验证，不能跨站登录")
                .contains("/uc/profile")
                .contains("重新尝试登录"));
    }

    @Test
    void preventsTokenResponsesFromBeingCached() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.token(any()))
            .thenReturn(Mono.just(OAuthTokenResponse.builder()
                .accessToken("access-001")
                .tokenType("Bearer")
                .expiresIn(900)
                .build()));

        webClient(authorizationService)
            .post()
            .uri("/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue("grant_type=authorization_code&code=code-001"
                + "&redirect_uri=https%3A%2F%2Fb.example.com%2Fcallback"
                + "&client_id=site-b&client_secret=secret&code_verifier=verifier")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectHeader().valueEquals(HttpHeaders.PRAGMA, "no-cache");
    }

    @Test
    void rejectsBackslashBasedExternalNoticeReturnTarget() {
        webClient(mock(OAuthAuthorizationService.class))
            .get()
            .uri("/notice?return_to=%2F%5Cevil.example")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("href=\"/login\"")
                .doesNotContain("evil.example"));
    }

    private static WebTestClient webClient(OAuthAuthorizationService authorizationService) {
        return WebTestClient.bindToRouterFunction(
            new OAuthRoutes(authorizationService, new OAuthAuthorizeHandler(authorizationService))
                .publicRoutes()
        ).build();
    }
}
