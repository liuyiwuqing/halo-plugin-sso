package site.muyin.sso.endpoint.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.center.CenterRoleClient;
import site.muyin.sso.model.SsoPublicRole;
import site.muyin.sso.setting.SsoGeneralSetting;

class SsoSettingRoutesTest {

    @Test
    void exposesOnlyRuntimeFieldsNeededByRoleMappingSelectors() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerRoleClient = mock(CenterRoleClient.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        setting.setCenterUrl("https://auth.example.com");
        setting.setClientId("site-b");
        setting.setClientSecret("secret-should-not-leak");

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));

        var response = webClient(settingFetcher, centerRoleClient)
            .get()
            .uri("/general")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();

        assertThat(response)
            .containsEntry("mode", "client")
            .containsEntry("centerUrl", "https://auth.example.com")
            .doesNotContainKeys("clientId", "clientSecret");
    }

    @Test
    void fallsBackToDefaultGeneralSettingWhenMissing() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerRoleClient = mock(CenterRoleClient.class);
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.empty());

        var response = webClient(settingFetcher, centerRoleClient)
            .get()
            .uri("/general")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();

        assertThat(response)
            .containsEntry("mode", "center")
            .containsEntry("centerUrl", null);
    }

    @Test
    void proxiesCenterRolesFromConfiguredCenterUrl() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerRoleClient = mock(CenterRoleClient.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        setting.setCenterUrl("https://auth.example.com/");

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        when(centerRoleClient.listRoles("https://auth.example.com/"))
            .thenReturn(Flux.just(new SsoPublicRole("author", "作者", "内容管理", false)));

        var response = webClient(settingFetcher, centerRoleClient)
            .get()
            .uri("/center-roles")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(SsoPublicRole.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).extracting(SsoPublicRole::name)
            .containsExactly("author");
        verify(centerRoleClient).listRoles("https://auth.example.com/");
    }

    @Test
    void rejectsCenterRoleProxyOutsideClientMode() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerRoleClient = mock(CenterRoleClient.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("center");

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));

        webClient(settingFetcher, centerRoleClient)
            .get()
            .uri("/center-roles")
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(centerRoleClient);
    }

    private static WebTestClient webClient(ReactiveSettingFetcher settingFetcher,
        CenterRoleClient centerRoleClient) {
        return WebTestClient.bindToRouterFunction(
            new SsoSettingRoutes(settingFetcher, centerRoleClient).consoleRoutes()
        ).build();
    }
}
