package site.muyin.sso.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import site.muyin.sso.core.SsoConstants;
import site.muyin.sso.oauth.OAuthEndpointPaths;

class SsoRememberMeRedirectFilterTest {

    private static final String PROVIDER_LOGIN = "/login/social/"
        + SsoConstants.AUTH_PROVIDER_NAME;

    private final SsoRememberMeRedirectFilter filter = new SsoRememberMeRedirectFilter();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void carriesQueryChoiceIntoClientLoginRedirect(boolean rememberMe) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN + "?remember-me=" + rememberMe).build());

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN
                + "?remember-me=" + rememberMe));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void carriesFormChoiceWhenQueryChoiceIsMissing(boolean rememberMe) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("remember-me=" + rememberMe));

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN
                + "?remember-me=" + rememberMe));
    }

    @Test
    void defaultsToFalseWhenChoiceIsMissing() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN).build());

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN + "?remember-me=false"));
    }

    @Test
    void prefersExplicitQueryChoiceOverFormChoice() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN + "?remember-me=false")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("remember-me=true"));

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN + "?remember-me=false"));
    }

    @Test
    void preservesEncodedRedirectParametersAndReplacesExistingChoice() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN + "?remember-me=true").build());

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN
            + "?return_url=%2Fposts%2F1%3Ftab%3Dcomments&source=login%2Bmenu"
            + "&remember-me=false#finish");

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN
                + "?return_url=%2Fposts%2F1%3Ftab%3Dcomments&source=login%2Bmenu"
                + "&remember-me=true#finish"));
    }

    @Test
    void leavesGetRequestUnchanged() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get(PROVIDER_LOGIN + "?remember-me=true").build());

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/login/social/other", "/login/social/muyin-sso/extra"})
    void leavesUnrelatedLoginPathsUnchanged(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(path + "?remember-me=true").build());

        redirect(exchange, OAuthEndpointPaths.CLIENT_LOGIN);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://other.example.com" + OAuthEndpointPaths.CLIENT_LOGIN,
        "//other.example.com" + OAuthEndpointPaths.CLIENT_LOGIN,
        "/other-login",
        OAuthEndpointPaths.CLIENT_LOGIN + "/extra"
    })
    void leavesExternalAndUnrelatedRedirectsUnchanged(String location) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN + "?remember-me=true").build());

        redirect(exchange, location);

        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(location));
    }

    @Test
    void leavesNonRedirectResponseUnchanged() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post(PROVIDER_LOGIN + "?remember-me=true").build());

        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.OK);
            filteredExchange.getResponse().getHeaders()
                .setLocation(URI.create(OAuthEndpointPaths.CLIENT_LOGIN));
            return filteredExchange.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getLocation())
            .isEqualTo(URI.create(OAuthEndpointPaths.CLIENT_LOGIN));
    }

    private void redirect(MockServerWebExchange exchange, String location) {
        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.FOUND);
            filteredExchange.getResponse().getHeaders().setLocation(URI.create(location));
            return filteredExchange.getResponse().setComplete();
        }).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
    }
}
