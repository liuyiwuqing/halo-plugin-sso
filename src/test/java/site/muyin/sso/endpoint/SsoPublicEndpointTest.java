package site.muyin.sso.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.endpoint.routes.ClientLoginRoutes;
import site.muyin.sso.endpoint.routes.OAuthRoutes;
import site.muyin.sso.model.SsoPublicRole;
import site.muyin.sso.setting.SsoGeneralSetting;
import org.yaml.snakeyaml.Yaml;

class SsoPublicEndpointTest {

    @Test
    void onlyListsConfiguredStandardRolesInCenterMode() {
        var client = mock(ReactiveExtensionClient.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("center");
        setting.setStandardRoles(Set.of("subscriber", "author"));

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        when(client.listAll(eq(Role.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                role("editor"),
                role("author"),
                role("subscriber"),
                roleTemplate("rt-console.sso.muyin.site")
            ));

        var response = webClient(client, settingFetcher)
            .get()
            .uri("/roles/list")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(SsoPublicRole.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).extracting(SsoPublicRole::name)
            .containsExactly("author", "subscriber");
    }

    @Test
    void rejectsRoleListOutsideCenterMode() {
        var client = mock(ReactiveExtensionClient.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));

        webClient(client, settingFetcher)
            .get()
            .uri("/roles/list")
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(client);
    }

    @Test
    void mapsRoleToPublicRoleView() {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName("author");
        metadata.setAnnotations(Map.of(
            "rbac.authorization.halo.run/display-name", "作者",
            "rbac.authorization.halo.run/module", "内容管理"
        ));
        metadata.setLabels(Map.of("halo.run/hidden", "true"));
        role.setMetadata(metadata);

        var view = SsoPublicEndpoint.toPublicRole(role);

        assertThat(view.name()).isEqualTo("author");
        assertThat(view.displayName()).isEqualTo("作者");
        assertThat(view.module()).isEqualTo("内容管理");
        assertThat(view.hidden()).isTrue();
    }

    @Test
    void fallsBackToRoleNameWhenDisplayNameIsMissing() {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName("authenticated");
        role.setMetadata(metadata);

        var view = SsoPublicEndpoint.toPublicRole(role);

        assertThat(view.name()).isEqualTo("authenticated");
        assertThat(view.displayName()).isEqualTo("authenticated");
        assertThat(view.module()).isNull();
        assertThat(view.hidden()).isFalse();
    }

    @Test
    void excludesRoleTemplateRolesFromPublicRoleList() {
        assertThat(SsoPublicEndpoint.isAssignableRole(
            roleTemplate("rt-console.sso.muyin.site"))).isFalse();
    }

    @Test
    void keepsNormalRolesInPublicRoleList() {
        assertThat(SsoPublicEndpoint.isAssignableRole(role("authenticated"))).isTrue();
    }

    @Test
    void publicOAuthRoleTemplateIsAvailableToAnonymousUsers() {
        var labels = roleLabels("rt-public.sso.muyin.site-anonymous");

        assertThat(labels)
            .containsEntry("halo.run/role-template", "true")
            .containsEntry("rbac.authorization.halo.run/aggregate-to-anonymous", "true");
    }

    @Test
    void publicOAuthRoleTemplateIsAvailableToAuthenticatedUsers() {
        var role = roleDocument("rt-public.sso.muyin.site-authenticated");
        var labels = roleLabels(role);

        assertThat(labels)
            .containsEntry("halo.run/role-template", "false")
            .containsEntry("rbac.authorization.halo.run/aggregate-to-authenticated", "true");
        assertThat(roleRules(role)).anySatisfy(rule -> {
            assertThat(rule.get("apiGroups")).isEqualTo(List.of("public.sso.muyin.site"));
            assertThat(rule.get("resources")).isEqualTo(List.of("oauth"));
            assertThat(rule.get("resourceNames")).isEqualTo(List.of("authorize"));
            assertThat(rule.get("verbs")).isEqualTo(List.of("get"));
        });
    }

    private static WebTestClient webClient(ReactiveExtensionClient client,
        ReactiveSettingFetcher settingFetcher) {
        var oauthRoutes = mock(OAuthRoutes.class);
        var clientLoginRoutes = mock(ClientLoginRoutes.class);
        var emptyRoutes = RouterFunctions.route(RequestPredicates.path("/__never__"),
            request -> ServerResponse.notFound().build());
        when(oauthRoutes.publicRoutes()).thenReturn(emptyRoutes);
        when(clientLoginRoutes.publicRoutes()).thenReturn(emptyRoutes);
        var endpoint = new SsoPublicEndpoint(oauthRoutes, clientLoginRoutes, client,
            settingFetcher);
        return WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    private static Role role(String name) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        role.setMetadata(metadata);
        return role;
    }

    private static Role roleTemplate(String name) {
        var role = role(name);
        role.getMetadata().setLabels(Map.of("halo.run/role-template", "true"));
        return role;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> roleLabels(String roleName) {
        return roleLabels(roleDocument(roleName));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> roleLabels(Map<String, Object> role) {
        return (Map<String, String>) metadata(role).get("labels");
    }

    private static Map<String, Object> roleDocument(String roleName) {
        return roleDocuments().stream()
            .filter(document -> roleName.equals(metadata(document).get("name")))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roleRules(Map<String, Object> role) {
        return (List<Map<String, Object>>) role.get("rules");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roleDocuments() {
        try (InputStream inputStream = SsoPublicEndpointTest.class.getClassLoader()
            .getResourceAsStream("extensions/roleTemplate.yaml")) {
            assertThat(inputStream).isNotNull();
            var documents = new ArrayList<Map<String, Object>>();
            new Yaml().loadAll(inputStream).forEach(document -> {
                if (document instanceof Map<?, ?> map) {
                    documents.add((Map<String, Object>) map);
                }
            });
            return documents;
        } catch (Exception e) {
            throw new AssertionError("Failed to load SSO role template", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Map<String, Object> document) {
        return (Map<String, Object>) document.get("metadata");
    }
}
