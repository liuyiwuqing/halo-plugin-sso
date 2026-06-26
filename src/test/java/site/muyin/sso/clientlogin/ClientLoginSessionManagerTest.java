package site.muyin.sso.clientlogin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import site.muyin.sso.oauth.Pkce;

class ClientLoginSessionManagerTest {

    @Test
    void createsPkceLoginSessionAndConsumesStateOnce() {
        var manager = new ClientLoginSessionManager();

        var session = manager.start("/posts/1");

        assertThat(session.state()).isNotBlank();
        assertThat(session.codeVerifier()).isNotBlank();
        assertThat(Pkce.matchesS256(session.codeVerifier(), session.codeChallenge())).isTrue();
        assertThat(session.returnUrl()).isEqualTo("/posts/1");

        var consumed = manager.consume(session.state());
        assertThat(consumed.returnUrl()).isEqualTo("/posts/1");

        assertThatThrownBy(() -> manager.consume(session.state()))
            .isInstanceOf(ClientLoginException.class)
            .hasMessageContaining("已使用");
    }
}
