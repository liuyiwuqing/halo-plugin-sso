package site.muyin.sso.service.impl;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.security.LoginHandlerEnhancer;
import site.muyin.sso.service.SsoLoginSessionService;

@Service
public class SsoLoginSessionServiceImpl implements SsoLoginSessionService {

    private final ReactiveUserDetailsService userDetailsService;
    private final ServerSecurityContextRepository securityContextRepository;
    private final LoginHandlerEnhancer loginHandlerEnhancer;

    public SsoLoginSessionServiceImpl(ReactiveUserDetailsService userDetailsService,
        ServerSecurityContextRepository securityContextRepository,
        LoginHandlerEnhancer loginHandlerEnhancer) {
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.loginHandlerEnhancer = loginHandlerEnhancer;
    }

    @Override
    public Mono<Void> login(ServerWebExchange exchange, String username) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username must not be blank"));
        }
        return userDetailsService.findByUsername(username)
            .flatMap(userDetails -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
                var securityContext = new SecurityContextImpl(authentication);
                return securityContextRepository.save(exchange, securityContext)
                    .then(loginHandlerEnhancer.onLoginSuccess(exchange, authentication));
            });
    }
}
