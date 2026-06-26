package site.muyin.sso.endpoint;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.BeforeSecurityWebFilter;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.OAuthBearerToken;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.service.OAuthAuthorizationService;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OAuthUserInfoSecurityWebFilter implements BeforeSecurityWebFilter {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String USERINFO_PATH = OAuthEndpointPaths.USERINFO;

    private final OAuthAuthorizationService oauthAuthorizationService;

    public OAuthUserInfoSecurityWebFilter(OAuthAuthorizationService oauthAuthorizationService) {
        this.oauthAuthorizationService = oauthAuthorizationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!shouldHandle(exchange)) {
            return chain.filter(exchange);
        }
        var authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return oauthAuthorizationService.userInfo(OAuthBearerToken.fromAuthorizationHeader(authorization))
            .flatMap(response -> writeJson(exchange, response))
            .onErrorResume(ResponseStatusException.class, error -> {
                if (HttpStatus.UNAUTHORIZED.equals(error.getStatusCode())) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
                return Mono.error(error);
            });
    }

    private static boolean shouldHandle(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        return HttpMethod.GET.equals(request.getMethod())
            && USERINFO_PATH.equals(request.getPath().pathWithinApplication().value());
    }

    private static Mono<Void> writeJson(ServerWebExchange exchange,
        OAuthUserInfoResponse responseBody) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            var body = JSON_MAPPER.writeValueAsBytes(responseBody);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
