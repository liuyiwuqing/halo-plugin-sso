package site.muyin.sso.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.setting.SsoGeneralSetting;

class ClientModeLoginRedirectFilterTest {

    @Test
    void redirectsThemeLoginToClientSsoLoginInClientMode() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        var filter = new ClientModeLoginRedirectFilter(settingFetcher);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("https://client.example.com/login"
                + "?redirect_uri=https%3A%2F%2Fclient.example.com%2Fposts%2F1%3Ftab%3Dcomments")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        var location = exchange.getResponse().getHeaders().getLocation();
        var params = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        var returnUrl = URLDecoder.decode(params.getFirst("return_url"), StandardCharsets.UTF_8);

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        assertThat(location.getPath()).isEqualTo(OAuthEndpointPaths.CLIENT_LOGIN);
        assertThat(returnUrl).isEqualTo("/posts/1?tab=comments");
    }

    @Test
    void allowsLocalLoginBypassInClientMode() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        var filter = new ClientModeLoginRedirectFilter(settingFetcher);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("https://client.example.com/login?sso_local=1")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsLogoutLandingLoginInClientMode() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        var filter = new ClientModeLoginRedirectFilter(settingFetcher);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("https://client.example.com/login?logout")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void doesNotRedirectLoginOutsideClientMode() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("center");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        var filter = new ClientModeLoginRedirectFilter(settingFetcher);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("https://auth.example.com/login")
            .build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, filteredExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void dropsCrossOriginRedirectUriWhenRedirectingToClientSsoLogin() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        var filter = new ClientModeLoginRedirectFilter(settingFetcher);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("https://client.example.com/login"
                + "?redirect_uri=https%3A%2F%2Fevil.example.com%2Fcapture")
            .build());

        filter.filter(exchange, filteredExchange -> Mono.empty()).block();

        var location = exchange.getResponse().getHeaders().getLocation();
        var params = UriComponentsBuilder.fromUri(location).build().getQueryParams();

        assertThat(URLDecoder.decode(params.getFirst("return_url"), StandardCharsets.UTF_8))
            .isEqualTo("/");
    }
}
