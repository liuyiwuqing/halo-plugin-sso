package site.muyin.sso.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.security.BeforeSecurityWebFilter;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.service.OAuthAuthorizationService;

class OAuthUserInfoSecurityWebFilterTest {

    @Test
    void runsBeforeHostSecurityAuthentication() {
        assertThat(new OAuthUserInfoSecurityWebFilter(mock(OAuthAuthorizationService.class)))
            .isInstanceOf(BeforeSecurityWebFilter.class);
    }

    @Test
    void handlesUserInfoRequestBeforeHostAuthentication() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.userInfo("access-001"))
            .thenReturn(Mono.just(OAuthUserInfoResponse.builder()
                .sub("user-001")
                .preferredUsername("lywq")
                .email("lywq@example.com")
                .name("Lywq")
                .roles(Set.of("author", "subscriber"))
                .build()));
        var filter = new OAuthUserInfoSecurityWebFilter(authorizationService);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(OAuthEndpointPaths.USERINFO)
            .header(HttpHeaders.AUTHORIZATION, "Bearer access-001")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
            .isEqualTo("application/json");
        assertThat(exchange.getResponse().getBodyAsString().block())
            .contains("\"sub\":\"user-001\"")
            .contains("\"preferred_username\":\"lywq\"")
            .contains("\"roles\":[");
    }

    @Test
    void returnsUnauthorizedWhenAccessTokenIsRejected() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        when(authorizationService.userInfo("bad-token"))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "access_token 无效或已过期")));
        var filter = new OAuthUserInfoSecurityWebFilter(authorizationService);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(OAuthEndpointPaths.USERINFO)
            .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ignoresOtherRequests() {
        var authorizationService = mock(OAuthAuthorizationService.class);
        var filter = new OAuthUserInfoSecurityWebFilter(authorizationService);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(OAuthEndpointPaths.ROLES_LIST)
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
