package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.sso.rolemapping.SsoRoleMappingName;
import site.muyin.sso.scheme.SsoRoleMapping;

class SsoRoleMappingServiceImplTest {

    @Test
    void createsMappingWithStableNameAndDefaults() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoRoleMappingServiceImpl(client);

        when(client.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var mapping = service.createWithRX(new SsoRoleMapping()
            .setCenterRole(" author ")
            .setLocalRole(" content-author ")).block();

        assertThat(mapping.getMetadata().getName())
            .isEqualTo(SsoRoleMappingName.fromCenterRole("author"));
        assertThat(mapping.getMetadata().getGenerateName())
            .isEqualTo(SsoRoleMapping.NAME_PREFIX);
        assertThat(mapping.getCenterRole()).isEqualTo("author");
        assertThat(mapping.getLocalRole()).isEqualTo("content-author");
        assertThat(mapping.getEnabled()).isTrue();
        assertThat(mapping.getSort()).isZero();
    }

    @Test
    void updatesExistingMappingByCenterRole() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoRoleMappingServiceImpl(client);
        var mappingName = SsoRoleMappingName.fromCenterRole("author");
        var existing = mapping("author", "old-role", true, 1);
        existing.setMetadata(new Metadata());
        existing.getMetadata().setName(mappingName);

        when(client.fetch(SsoRoleMapping.class, mappingName))
            .thenReturn(Mono.just(existing));
        when(client.update(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var mapping = service.updateWithRX(new SsoRoleMapping()
            .setCenterRole("author")
            .setLocalRole("content-author")
            .setEnabled(false)
            .setSort(5)).block();

        assertThat(mapping.getMetadata().getName()).isEqualTo(mappingName);
        assertThat(mapping.getCenterRole()).isEqualTo("author");
        assertThat(mapping.getLocalRole()).isEqualTo("content-author");
        assertThat(mapping.getEnabled()).isFalse();
        assertThat(mapping.getSort()).isEqualTo(5);
    }

    @Test
    void resolvesEnabledCenterRolesToLocalRoles() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoRoleMappingServiceImpl(client);

        when(client.listAll(eq(SsoRoleMapping.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                mapping(" ", "bad-role", true, 0),
                mapping(" author ", " content-author ", true, 1),
                mapping("editor", "content-editor", false, 2),
                mapping("subscriber", "subscriber", true, 3)
            ));

        var roles = service.resolveLocalRoles(Set.of("author", "editor"), "guest").block();

        assertThat(roles).containsExactly("content-author");
    }

    @Test
    void fallsBackToDefaultRoleWhenNoMappingMatches() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoRoleMappingServiceImpl(client);

        when(client.listAll(eq(SsoRoleMapping.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        var roles = service.resolveLocalRoles(Set.of("unknown"), "guest").block();

        assertThat(roles).containsExactly("guest");
    }

    private static SsoRoleMapping mapping(String centerRole, String localRole, boolean enabled,
        int sort) {
        return new SsoRoleMapping()
            .setCenterRole(centerRole)
            .setLocalRole(localRole)
            .setEnabled(enabled)
            .setSort(sort);
    }
}
