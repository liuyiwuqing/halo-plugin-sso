package site.muyin.sso.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import site.muyin.sso.endpoint.routes.OAuthAuthorizeHandler;
import site.muyin.sso.model.oauth.OAuthAuthorizeResult;
import site.muyin.sso.service.OAuthAuthorizationService;

class OAuthAuthorizeSecurityWebFilterTest {

    @Test
    void handlesAuthorizeRequestBeforeRbacAuthorization() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.authorize(any()))
            .thenReturn(Mono.just(OAuthAuthorizeResult.builder()
                .redirectUri("http://127.0.0.1:8090/apis/public.sso.muyin.site/"
                    + "v1alpha1/client/callback?code=code-001&state=state-001")
                .build()));
        var filter = new OAuthAuthorizeSecurityWebFilter(
            new OAuthAuthorizeHandler(authorizationService));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(authorizeUri())
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        assertThat(exchange.getResponse().getHeaders().getLocation().toString())
            .contains("code=code-001")
            .contains("state=state-001");
    }

    @Test
    void redirectsAnonymousAuthorizeRequestToLogin() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.authorize(any()))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "用户未登录身份中心")));
        var filter = new OAuthAuthorizeSecurityWebFilter(
            new OAuthAuthorizeHandler(authorizationService));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(authorizeUri())
            .build());

        filter.filter(exchange, filteredExchange -> Mono.empty()).block();

        var location = exchange.getResponse().getHeaders().getLocation();
        var params = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        var redirectUri = URLDecoder.decode(params.getFirst("redirect_uri"),
            StandardCharsets.UTF_8);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        assertThat(location.getPath()).isEqualTo("/login");
        assertThat(redirectUri)
            .startsWith("/apis/public.sso.muyin.site/v1alpha1/oauth/authorize")
            .contains("client_id=site-b")
            .contains("state=state-001");
    }

    @Test
    void redirectsUnverifiedEmailAuthorizeRequestToNoticePage() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.authorize(any()))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "用户邮箱未验证，不能跨站登录")));
        var filter = new OAuthAuthorizeSecurityWebFilter(
            new OAuthAuthorizeHandler(authorizationService));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(authorizeUri())
            .build());

        filter.filter(exchange, filteredExchange -> Mono.empty()).block();

        var location = exchange.getResponse().getHeaders().getLocation();
        var params = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        var returnTo = URLDecoder.decode(params.getFirst("return_to"),
            StandardCharsets.UTF_8);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        assertThat(location.getPath()).isEqualTo("/apis/public.sso.muyin.site/v1alpha1/oauth/notice");
        assertThat(params.getFirst("code")).isEqualTo("email_not_verified");
        assertThat(returnTo)
            .startsWith("/apis/public.sso.muyin.site/v1alpha1/oauth/authorize")
            .contains("client_id=site-b")
            .contains("state=state-001");
    }

    @Test
    void ignoresOtherRequests() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        var filter = new OAuthAuthorizeSecurityWebFilter(
            new OAuthAuthorizeHandler(authorizationService));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/apis/public.sso.muyin.site/v1alpha1/roles/list")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static String authorizeUri() {
        return "/apis/public.sso.muyin.site/v1alpha1/oauth/authorize"
            + "?response_type=code"
            + "&client_id=site-b"
            + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A8090%2Fapis%2Fpublic.sso.muyin.site%2F"
            + "v1alpha1%2Fclient%2Fcallback"
            + "&scope=openid%20profile%20email"
            + "&state=state-001"
            + "&code_challenge=challenge-001"
            + "&code_challenge_method=S256";
    }
}
