package site.muyin.sso.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void createsRfc7636S256Challenge() {
        var verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        var challenge = Pkce.challengeS256(verifier);

        assertThat(challenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        assertThat(Pkce.matchesS256(verifier, challenge)).isTrue();
    }
}
