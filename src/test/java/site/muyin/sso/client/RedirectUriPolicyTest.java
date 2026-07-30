package site.muyin.sso.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RedirectUriPolicyTest {

    @Test
    void rejectsRedirectUrisWithoutAnAuthorityOrWithUnsafeComponents() {
        assertThatThrownBy(() -> RedirectUriPolicy.normalize(List.of("https:/callback"), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host");
        assertThatThrownBy(() -> RedirectUriPolicy.normalize(
            List.of("https://user@example.com/callback"), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userinfo");
        assertThatThrownBy(() -> RedirectUriPolicy.normalize(
            List.of("https://example.com/callback#token"), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fragment");
    }
}
