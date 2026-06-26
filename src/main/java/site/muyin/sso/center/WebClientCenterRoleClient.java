package site.muyin.sso.center;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import site.muyin.sso.model.SsoPublicRole;
import site.muyin.sso.oauth.OAuthEndpointPaths;

@Component
public class WebClientCenterRoleClient implements CenterRoleClient {

    private final WebClient webClient;

    public WebClientCenterRoleClient() {
        this(WebClient.builder().build());
    }

    WebClientCenterRoleClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Flux<SsoPublicRole> listRoles(String centerUrl) {
        return webClient.get()
            .uri(endpoint(centerUrl, OAuthEndpointPaths.ROLES_LIST))
            .retrieve()
            .bodyToFlux(SsoPublicRole.class);
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
