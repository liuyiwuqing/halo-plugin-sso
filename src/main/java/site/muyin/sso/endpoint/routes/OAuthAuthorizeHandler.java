package site.muyin.sso.endpoint.routes;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthAuthorizeRequest;
import site.muyin.sso.service.OAuthAuthorizationService;

@Component
public class OAuthAuthorizeHandler {

    private final OAuthAuthorizationService oauthAuthorizationService;

    public OAuthAuthorizeHandler(OAuthAuthorizationService oauthAuthorizationService) {
        this.oauthAuthorizationService = oauthAuthorizationService;
    }

    public Mono<URI> authorize(ServerRequest request) {
        return authorize(request.exchange());
    }

    public Mono<URI> authorize(ServerWebExchange exchange) {
        var authorizeRequest = authorizeRequest(exchange.getRequest().getQueryParams());
        return oauthAuthorizationService.authorize(authorizeRequest)
            .map(result -> URI.create(result.getRedirectUri()))
            .onErrorResume(ResponseStatusException.class, error -> {
                if (HttpStatus.UNAUTHORIZED.equals(error.getStatusCode())) {
                    return Mono.just(loginUri(exchange.getRequest().getURI()));
                }
                if (HttpStatus.FORBIDDEN.equals(error.getStatusCode())
                    && isCrossSiteLoginEmailError(error.getReason())) {
                    return Mono.just(noticeUri(exchange.getRequest().getURI(), error.getReason()));
                }
                return Mono.error(error);
            });
    }

    private static OAuthAuthorizeRequest authorizeRequest(MultiValueMap<String, String> queryParams) {
        return OAuthAuthorizeRequest.builder()
            .responseType(queryParams.getFirst("response_type"))
            .clientId(queryParams.getFirst("client_id"))
            .redirectUri(queryParams.getFirst("redirect_uri"))
            .scope(queryParams.getFirst("scope"))
            .state(queryParams.getFirst("state"))
            .codeChallenge(queryParams.getFirst("code_challenge"))
            .codeChallengeMethod(queryParams.getFirst("code_challenge_method"))
            .build();
    }

    private static URI loginUri(URI requestUri) {
        return UriComponentsBuilder.fromPath("/login")
            .queryParam("redirect_uri", currentRequestPathAndQuery(requestUri))
            .encode()
            .build()
            .toUri();
    }

    private static URI noticeUri(URI requestUri, String reason) {
        return UriComponentsBuilder.fromPath(site.muyin.sso.oauth.OAuthEndpointPaths.NOTICE)
            .queryParam("code", noticeCode(reason))
            .queryParam("return_to", currentRequestPathAndQuery(requestUri))
            .encode()
            .build()
            .toUri();
    }

    private static boolean isCrossSiteLoginEmailError(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        return reason.contains("用户邮箱未验证") || reason.contains("用户邮箱为空");
    }

    private static String noticeCode(String reason) {
        if (reason == null || reason.isBlank()) {
            return "access_denied";
        }
        if (reason.contains("用户邮箱未验证")) {
            return "email_not_verified";
        }
        if (reason.contains("用户邮箱为空")) {
            return "email_missing";
        }
        return "access_denied";
    }

    private static String currentRequestPathAndQuery(URI uri) {
        var value = uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            value += "?" + uri.getRawQuery();
        }
        if (uri.getRawFragment() != null && !uri.getRawFragment().isBlank()) {
            value += "#" + uri.getRawFragment();
        }
        return value;
    }
}
