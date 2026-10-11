package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.security.LoginHandlerEnhancer;

class SsoLoginSessionServiceImplTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void usesSavedPreferenceEvenWhenCallbackHasNewSessionAndConflictingQuery(boolean rememberMe) {
        var userDetailsService = mock(ReactiveUserDetailsService.class);
        var repository = new WebSessionServerSecurityContextRepository();
        var enhancer = mock(LoginHandlerEnhancer.class);
        var callbackUri = URI.create("https://client.example.com/callback"
            + "?code=a%2Bb&state=state-001&remember-me=" + !rememberMe);
        var exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.GET, callbackUri));
        var user = new User("lywq", "", List.of(new SimpleGrantedAuthority("ROLE_guest")));
        when(userDetailsService.findByUsername("lywq")).thenReturn(Mono.just(user));
        when(enhancer.onLoginSuccess(any(), any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(0, ServerWebExchange.class);
            assertThat(callback.getRequest().getQueryParams().get("remember-me"))
                .containsExactly(Boolean.toString(rememberMe));
            assertThat(callback.getRequest().getURI().getRawQuery()).contains("code=a%2Bb");
            assertThat(callback.getResponse()).isSameAs(exchange.getResponse());
            assertThat(callback.getAttributes()).isSameAs(exchange.getAttributes());
            return Mono.empty();
        });

        new SsoLoginSessionServiceImpl(userDetailsService, repository, enhancer)
            .login(exchange, "lywq", rememberMe).block();

        assertThat(repository.load(exchange).block().getAuthentication().getName())
            .isEqualTo("lywq");
        verify(enhancer).onLoginSuccess(any(), any());
    }

    @Test
    void savesSecurityContextAndRunsHaloLoginEnhancers() {
        var userDetailsService = mock(ReactiveUserDetailsService.class);
        var securityContextRepository = mock(ServerSecurityContextRepository.class);
        var loginHandlerEnhancer = mock(LoginHandlerEnhancer.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/callback"));
        var service = new SsoLoginSessionServiceImpl(
            userDetailsService,
            securityContextRepository,
            loginHandlerEnhancer
        );
        var user = new User(
            "lywq",
            "",
            List.of(new SimpleGrantedAuthority("ROLE_guest"))
        );

        when(userDetailsService.findByUsername("lywq")).thenReturn(Mono.just(user));
        when(securityContextRepository.save(eq(exchange), any(SecurityContext.class)))
            .thenReturn(Mono.empty());
        when(loginHandlerEnhancer.onLoginSuccess(any(), any()))
            .thenReturn(Mono.empty());

        service.login(exchange, "lywq").block();

        verify(securityContextRepository).save(eq(exchange), any(SecurityContext.class));
        verify(loginHandlerEnhancer).onLoginSuccess(any(), any());
    }
}
