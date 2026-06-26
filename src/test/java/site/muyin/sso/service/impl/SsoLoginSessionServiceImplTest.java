package site.muyin.sso.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.security.LoginHandlerEnhancer;

class SsoLoginSessionServiceImplTest {

    @Test
    void savesSecurityContextAndRunsHaloLoginEnhancers() {
        var userDetailsService = mock(ReactiveUserDetailsService.class);
        var securityContextRepository = mock(ServerSecurityContextRepository.class);
        var loginHandlerEnhancer = mock(LoginHandlerEnhancer.class);
        var exchange = mock(ServerWebExchange.class);
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
        when(loginHandlerEnhancer.onLoginSuccess(eq(exchange), any()))
            .thenReturn(Mono.empty());

        service.login(exchange, "lywq").block();

        verify(securityContextRepository).save(eq(exchange), any(SecurityContext.class));
        verify(loginHandlerEnhancer).onLoginSuccess(eq(exchange), any());
    }
}
