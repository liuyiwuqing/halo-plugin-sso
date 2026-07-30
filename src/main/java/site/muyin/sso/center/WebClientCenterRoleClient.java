package site.muyin.sso.center;

import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import site.muyin.sso.model.SsoPublicRole;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class WebClientCenterRoleClient implements CenterRoleClient {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final Duration requestTimeout;

    public WebClientCenterRoleClient() {
        this(WebClient.builder().build(), DEFAULT_REQUEST_TIMEOUT);
    }

    WebClientCenterRoleClient(WebClient webClient) {
        this(webClient, DEFAULT_REQUEST_TIMEOUT);
    }

    WebClientCenterRoleClient(WebClient webClient, Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.webClient = webClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Flux<SsoPublicRole> listRoles(String centerUrl) {
        return webClient.get()
            .uri(endpoint(centerUrl, OAuthEndpointPaths.ROLES_LIST))
            .retrieve()
            .bodyToFlux(SsoPublicRole.class)
            .collectList()
            .timeout(requestTimeout)
            .flatMapMany(Flux::fromIterable);
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
