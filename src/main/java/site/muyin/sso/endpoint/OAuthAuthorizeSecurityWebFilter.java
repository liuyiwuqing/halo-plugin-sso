package site.muyin.sso.endpoint;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.AuthenticationSecurityWebFilter;
import site.muyin.sso.endpoint.routes.OAuthAuthorizeHandler;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class OAuthAuthorizeSecurityWebFilter implements AuthenticationSecurityWebFilter {

    private static final String AUTHORIZE_PATH = OAuthEndpointPaths.AUTHORIZE;

    private final OAuthAuthorizeHandler authorizeHandler;

    public OAuthAuthorizeSecurityWebFilter(OAuthAuthorizeHandler authorizeHandler) {
        this.authorizeHandler = authorizeHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!shouldHandle(exchange)) {
            return chain.filter(exchange);
        }
        return authorizeHandler.authorize(exchange)
            .flatMap(location -> {
                var response = exchange.getResponse();
                response.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
                response.getHeaders().setLocation(location);
                return response.setComplete();
            });
    }

    private static boolean shouldHandle(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        return HttpMethod.GET.equals(request.getMethod())
            && AUTHORIZE_PATH.equals(request.getPath().pathWithinApplication().value());
    }
}
