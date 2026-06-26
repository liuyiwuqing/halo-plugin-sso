package site.muyin.sso.client;

import java.util.List;

public record RegisteredSsoClient(
    String clientId,
    String clientSecretHash,
    String displayName,
    String siteUrl,
    List<String> redirectUris,
    boolean enabled
) {
}
