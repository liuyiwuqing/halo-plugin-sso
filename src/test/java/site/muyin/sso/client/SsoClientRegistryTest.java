package site.muyin.sso.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SsoClientRegistryTest {

    @Test
    void registersClientWithHashedSecretAndValidatesAuthorizeRequest() {
        var registry = new SsoClientRegistry(new ClientSecretHasher());

        var client = registry.register(RegisterSsoClientRequest.builder()
            .clientId("site-b")
            .clientSecret("plain-secret")
            .displayName("B 站")
            .siteUrl("https://b.example.com")
            .redirectUris(List.of("https://b.example.com/plugins/sso/callback"))
            .enabled(true)
            .build());

        assertThat(client.clientSecretHash()).isNotEqualTo("plain-secret");
        assertThat(registry.verifySecret("site-b", "plain-secret")).isTrue();
        assertThat(registry.verifySecret("site-b", "wrong-secret")).isFalse();

        var authorized = registry.requireAuthorizedClient(
            "site-b",
            "https://b.example.com/plugins/sso/callback"
        );

        assertThat(authorized.clientId()).isEqualTo("site-b");
    }

    @Test
    void rejectsUnexpectedRedirectUri() {
        var registry = new SsoClientRegistry(new ClientSecretHasher());
        registry.register(RegisterSsoClientRequest.builder()
            .clientId("site-b")
            .clientSecret("plain-secret")
            .displayName("B 站")
            .siteUrl("https://b.example.com")
            .redirectUris(List.of("https://b.example.com/plugins/sso/callback"))
            .enabled(true)
            .build());

        assertThatThrownBy(() -> registry.requireAuthorizedClient(
            "site-b",
            "https://evil.example.com/plugins/sso/callback"
        ))
            .isInstanceOf(SsoClientException.class)
            .hasMessageContaining("redirect_uri");
    }

    @Test
    void rejectsDisabledClient() {
        var registry = new SsoClientRegistry(new ClientSecretHasher());
        registry.register(RegisterSsoClientRequest.builder()
            .clientId("site-b")
            .clientSecret("plain-secret")
            .displayName("B 站")
            .siteUrl("https://b.example.com")
            .redirectUris(List.of("https://b.example.com/plugins/sso/callback"))
            .enabled(false)
            .build());

        assertThatThrownBy(() -> registry.requireAuthorizedClient(
            "site-b",
            "https://b.example.com/plugins/sso/callback"
        ))
            .isInstanceOf(SsoClientException.class)
            .hasMessageContaining("disabled");
    }
}
