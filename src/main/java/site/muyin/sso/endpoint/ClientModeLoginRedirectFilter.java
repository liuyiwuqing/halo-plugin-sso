package site.muyin.sso.endpoint;

import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.security.AdditionalWebFilter;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.setting.SsoGeneralSetting;

@Component
public class ClientModeLoginRedirectFilter implements AdditionalWebFilter {

    private static final String LOCAL_LOGIN_QUERY = "sso_local";
    private static final String LOGOUT_QUERY = "logout";

    private final ReactiveSettingFetcher settingFetcher;

    public ClientModeLoginRedirectFilter(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!shouldHandle(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .flatMap(setting -> {
                if (!"client".equals(setting.getMode())) {
                    return chain.filter(exchange);
                }
                if (Boolean.FALSE.equals(setting.getAutoSsoLoginEnabled())) {
                    return chain.filter(exchange);
                }
                var response = exchange.getResponse();
                response.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
                response.getHeaders().setLocation(clientLoginUri(exchange));
                return response.setComplete();
            });
    }

    private static boolean shouldHandle(ServerHttpRequest request) {
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return false;
        }
        if (!"/login".equals(request.getPath().pathWithinApplication().value())) {
            return false;
        }
        var queryParams = request.getQueryParams();
        return !queryParams.containsKey(LOCAL_LOGIN_QUERY)
            && !queryParams.containsKey(LOGOUT_QUERY)
            && !queryParams.containsKey("method");
    }

    private static URI clientLoginUri(ServerWebExchange exchange) {
        return UriComponentsBuilder.fromPath(OAuthEndpointPaths.CLIENT_LOGIN)
            .queryParam("return_url", ReturnUrlPolicy.safeRedirectUriReturnUrl(
                exchange.getRequest()))
            .encode()
            .build()
            .toUri();
    }
}
