package site.muyin.sso.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SsoClientRegistry {

    private final ClientSecretHasher secretHasher;
    private final Map<String, RegisteredSsoClient> clients = new ConcurrentHashMap<>();

    public SsoClientRegistry(ClientSecretHasher secretHasher) {
        this.secretHasher = secretHasher;
    }

    public RegisteredSsoClient register(RegisterSsoClientRequest request) {
        requireText(request.getClientId(), "clientId");
        requireText(request.getClientSecret(), "clientSecret");
        requireText(request.getDisplayName(), "displayName");
        requireText(request.getSiteUrl(), "siteUrl");
        var redirectUris = RedirectUriPolicy.normalize(request.getRedirectUris());
        var client = new RegisteredSsoClient(
            request.getClientId(),
            secretHasher.hash(request.getClientSecret()),
            request.getDisplayName(),
            request.getSiteUrl(),
            redirectUris,
            !Boolean.FALSE.equals(request.getEnabled())
        );
        clients.put(client.clientId(), client);
        return client;
    }

    public boolean verifySecret(String clientId, String clientSecret) {
        var client = clients.get(clientId);
        if (client == null || !client.enabled()) {
            return false;
        }
        return secretHasher.matches(clientSecret, client.clientSecretHash());
    }

    public RegisteredSsoClient requireAuthorizedClient(String clientId, String redirectUri) {
        requireText(clientId, "clientId");
        requireText(redirectUri, "redirectUri");
        var client = clients.get(clientId);
        if (client == null) {
            throw new SsoClientException("SSO client not found");
        }
        if (!client.enabled()) {
            throw new SsoClientException("SSO client is disabled");
        }
        if (!RedirectUriPolicy.isAllowed(client.redirectUris(), redirectUri)) {
            throw new SsoClientException("redirect_uri is not allowed");
        }
        return client;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
