package site.muyin.sso.clientlogin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class WebClientCenterOAuthClient implements CenterOAuthClient {

    private final WebClient webClient;

    public WebClientCenterOAuthClient() {
        this(WebClient.builder().build());
    }

    WebClientCenterOAuthClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<OAuthTokenResponse> exchangeCode(String centerUrl, OAuthTokenRequest request) {
        return webClient.post()
            .uri(endpoint(centerUrl, OAuthEndpointPaths.TOKEN))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", request.getGrantType())
                .with("code", request.getCode())
                .with("redirect_uri", request.getRedirectUri())
                .with("client_id", request.getClientId())
                .with("client_secret", request.getClientSecret())
                .with("code_verifier", request.getCodeVerifier()))
            .retrieve()
            .bodyToMono(OAuthTokenResponse.class);
    }

    @Override
    public Mono<OAuthUserInfoResponse> userInfo(String centerUrl, String accessToken) {
        return webClient.get()
            .uri(endpoint(centerUrl, OAuthEndpointPaths.USERINFO))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .bodyToMono(OAuthUserInfoResponse.class);
    }

    private static String endpoint(String baseUrl, String path) {
        return trimTrailingSlash(baseUrl) + path;
    }

    private static String trimTrailingSlash(String value) {
        var trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
