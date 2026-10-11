package site.muyin.sso.service.impl;

import static site.muyin.sso.core.SsoConstants.REMEMBER_ME_PARAMETER;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
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
    public Mono<Void> login(ServerWebExchange exchange, String username, boolean rememberMe) {
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
                // Halo reads remember-me from the request. Use the consumed SSO state so a new
                // callback session or a caller-supplied query cannot change the original choice.
                var loginUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                    .replaceQueryParam(REMEMBER_ME_PARAMETER, rememberMe)
                    .build(true)
                    .toUri();
                var loginExchange = exchange.mutate()
                    .request(request -> request.uri(loginUri))
                    .build();
                return securityContextRepository.save(exchange, securityContext)
                    .then(Mono.defer(() ->
                        loginHandlerEnhancer.onLoginSuccess(loginExchange, authentication)));
            });
    }
}
