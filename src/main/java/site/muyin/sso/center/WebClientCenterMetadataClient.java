package site.muyin.sso.center;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.SsoAuthProviderMetadata;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class WebClientCenterMetadataClient implements CenterMetadataClient {

    private final WebClient webClient;

    public WebClientCenterMetadataClient() {
        this(WebClient.builder().build());
    }

    WebClientCenterMetadataClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<SsoAuthProviderMetadata> getMetadata(String centerUrl) {
        return webClient.get()
            .uri(endpoint(centerUrl, OAuthEndpointPaths.METADATA))
            .retrieve()
            .bodyToMono(SsoAuthProviderMetadata.class);
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
