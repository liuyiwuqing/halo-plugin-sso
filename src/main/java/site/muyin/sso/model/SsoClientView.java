package site.muyin.sso.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import site.muyin.sso.scheme.SsoClient;

@Value
@Builder
public class SsoClientView {

    String clientId;

    String displayName;

    String siteUrl;

    List<String> redirectUris;

    Boolean enabled;

    Instant createdAt;

    Instant updatedAt;

    public static SsoClientView from(SsoClient client) {
        return SsoClientView.builder()
            .clientId(client.getClientId())
            .displayName(client.getDisplayName())
            .siteUrl(client.getSiteUrl())
            .redirectUris(client.getRedirectUris())
            .enabled(client.getEnabled())
            .createdAt(client.getCreatedAt())
            .updatedAt(client.getUpdatedAt())
            .build();
    }
}
