package site.muyin.sso.endpoint;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.security.AdditionalWebFilter;
import site.muyin.sso.core.SsoConstants;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class SsoRememberMeRedirectFilter implements AdditionalWebFilter {

    private static final String PROVIDER_LOGIN_PATH = "/login/social/"
        + SsoConstants.AUTH_PROVIDER_NAME;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!HttpMethod.POST.equals(request.getMethod())
            || !PROVIDER_LOGIN_PATH.equals(request.getPath().pathWithinApplication().value())) {
            return chain.filter(exchange);
        }

        return rememberMe(exchange).flatMap(rememberMe -> {
            var response = exchange.getResponse();
            // Halo 的社交登录入口不转发原请求参数，在响应提交前保留本次登录选择。
            response.beforeCommit(() -> Mono.fromRunnable(
                () -> preserveRememberMe(response, rememberMe)));
            return chain.filter(exchange);
        });
    }

    private static Mono<Boolean> rememberMe(ServerWebExchange exchange) {
        var queryValue = exchange.getRequest().getQueryParams()
            .getFirst(SsoConstants.REMEMBER_ME_PARAMETER);
        if (queryValue != null) {
            return Mono.just(Boolean.parseBoolean(queryValue));
        }
        return exchange.getFormData().map(form -> Boolean.parseBoolean(
            form.getFirst(SsoConstants.REMEMBER_ME_PARAMETER)));
    }

    private static void preserveRememberMe(ServerHttpResponse response, boolean rememberMe) {
        var status = response.getStatusCode();
        var location = response.getHeaders().getLocation();
        if (status == null || !status.is3xxRedirection() || location == null
            || location.isAbsolute() || location.getRawAuthority() != null
            || !OAuthEndpointPaths.CLIENT_LOGIN.equals(location.getPath())) {
            return;
        }
        response.getHeaders().setLocation(UriComponentsBuilder.fromUri(location)
            .replaceQueryParam(SsoConstants.REMEMBER_ME_PARAMETER, rememberMe)
            .build(true)
            .toUri());
    }
}
