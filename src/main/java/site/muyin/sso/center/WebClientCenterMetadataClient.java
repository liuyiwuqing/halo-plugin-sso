package site.muyin.sso.center;

import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.SsoAuthProviderMetadata;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class WebClientCenterMetadataClient implements CenterMetadataClient {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final Duration requestTimeout;

    public WebClientCenterMetadataClient() {
        this(WebClient.builder().build(), DEFAULT_REQUEST_TIMEOUT);
    }

    WebClientCenterMetadataClient(WebClient webClient) {
        this(webClient, DEFAULT_REQUEST_TIMEOUT);
    }

    WebClientCenterMetadataClient(WebClient webClient, Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.webClient = webClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Mono<SsoAuthProviderMetadata> getMetadata(String centerUrl) {
        return webClient.get()
            .uri(endpoint(centerUrl, OAuthEndpointPaths.METADATA))
            .retrieve()
            .bodyToMono(SsoAuthProviderMetadata.class)
            .timeout(requestTimeout);
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
