package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.RoleBinding;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.sso.service.SsoRoleGrantService;

class SsoRoleGrantServiceImplTest {

    @Test
    void createsWithProductionConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ReactiveExtensionClient.class,
                () -> mock(ReactiveExtensionClient.class));
            context.registerBean(RoleService.class, () -> mock(RoleService.class));
            context.register(SsoRoleGrantServiceImpl.class);

            context.refresh();

            assertThat(context.getBean(SsoRoleGrantService.class))
                .isInstanceOf(SsoRoleGrantServiceImpl.class);
        }
    }

    @Test
    void reconcilesManagedRolesWithoutRevokingManualRoles() {
        var client = mock(ReactiveExtensionClient.class);
        var roleService = mock(RoleService.class);
        var service = new SsoRoleGrantServiceImpl(
            client,
            roleService,
            Clock.fixed(Instant.parse("2026-06-24T08:00:00Z"), ZoneOffset.UTC)
        );
        var user = localUser("lywq");

        when(client.get(User.class, "lywq")).thenReturn(Mono.just(user));
        when(roleService.listRoleBindings(any(RoleBinding.Subject.class)))
            .thenReturn(Flux.just(
                managedRoleBinding("lywq", "guest"),
                managedRoleBinding("lywq", "editor"),
                RoleBinding.create("lywq", "administrator")
            ));
        when(client.create(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.delete(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        service.grantRoles("lywq", Set.of("guest", "author")).block();

        var created = ArgumentCaptor.forClass(RoleBinding.class);
        var deleted = ArgumentCaptor.forClass(RoleBinding.class);
        verify(client).create(created.capture());
        verify(client).delete(deleted.capture());
        assertThat(created.getValue().getRoleRef().getName()).isEqualTo("author");
        assertThat(created.getValue().getMetadata().getAnnotations())
            .containsEntry(SsoRoleGrantServiceImpl.MANAGED_ROLE_BINDING_ANNOTATION, "true");
        assertThat(deleted.getValue().getRoleRef().getName()).isEqualTo("editor");
        assertThat(user.getMetadata().getAnnotations())
            .containsEntry(User.REQUEST_TO_UPDATE, "2026-06-24T08:00:00Z");
        verify(client).update(eq(user));
    }

    @Test
    void preservesLegacyRolesWhenNoManagedStateExists() {
        var client = mock(ReactiveExtensionClient.class);
        var roleService = mock(RoleService.class);
        var service = new SsoRoleGrantServiceImpl(client, roleService, Clock.systemUTC());
        var user = localUser("lywq");

        when(client.get(User.class, "lywq")).thenReturn(Mono.just(user));
        when(roleService.listRoleBindings(any(RoleBinding.Subject.class)))
            .thenReturn(Flux.just(RoleBinding.create("lywq", "administrator")));
        when(client.create(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        service.grantRoles("lywq", Set.of("guest")).block();

        var created = ArgumentCaptor.forClass(RoleBinding.class);
        verify(client).create(created.capture());
        assertThat(created.getValue().getMetadata().getAnnotations())
            .containsEntry(SsoRoleGrantServiceImpl.MANAGED_ROLE_BINDING_ANNOTATION, "true");
        verify(client, never()).delete(any(RoleBinding.class));
    }

    @Test
    void doesNotTakeOwnershipOfExistingManualTargetRole() {
        var client = mock(ReactiveExtensionClient.class);
        var roleService = mock(RoleService.class);
        var service = new SsoRoleGrantServiceImpl(client, roleService, Clock.systemUTC());
        var user = localUser("lywq");
        var manualGuestBinding = RoleBinding.create("lywq", "guest");

        when(client.get(User.class, "lywq")).thenReturn(Mono.just(user));
        when(roleService.listRoleBindings(any(RoleBinding.Subject.class)))
            .thenReturn(Flux.just(manualGuestBinding));
        when(client.delete(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        service.grantRoles("lywq", Set.of("guest")).block();
        service.grantRoles("lywq", Set.of()).block();

        verify(client, never()).delete(any(RoleBinding.class));
        assertThat(user.getMetadata().getAnnotations())
            .isNullOrEmpty();
    }

    @Test
    void preservesManualReplacementOfPreviouslyManagedRole() {
        var client = mock(ReactiveExtensionClient.class);
        var roleService = mock(RoleService.class);
        var service = new SsoRoleGrantServiceImpl(client, roleService, Clock.systemUTC());
        var user = localUser("lywq");
        user.getMetadata().setAnnotations(new HashMap<>());
        user.getMetadata().getAnnotations().put(
            "sso.muyin.site/managed-roles", "guest");
        var manualGuestBinding = RoleBinding.create("lywq", "guest");

        when(client.get(User.class, "lywq")).thenReturn(Mono.just(user));
        when(roleService.listRoleBindings(any(RoleBinding.Subject.class)))
            .thenReturn(Flux.just(manualGuestBinding));
        when(client.delete(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        service.grantRoles("lywq", Set.of()).block();

        verify(client, never()).delete(any(RoleBinding.class));
    }

    @Test
    void revokesAllPreviouslyManagedRolesWhenTargetIsEmpty() {
        var client = mock(ReactiveExtensionClient.class);
        var roleService = mock(RoleService.class);
        var service = new SsoRoleGrantServiceImpl(client, roleService, Clock.systemUTC());
        var user = localUser("lywq");
        var editorBinding = managedRoleBinding("lywq", "editor");

        when(client.get(User.class, "lywq")).thenReturn(Mono.just(user));
        when(roleService.listRoleBindings(any(RoleBinding.Subject.class)))
            .thenReturn(Flux.just(editorBinding));
        when(client.delete(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        service.grantRoles("lywq", Set.of()).block();

        verify(client).delete(eq(editorBinding));
        assertThat(user.getMetadata().getAnnotations())
            .containsKey(User.REQUEST_TO_UPDATE);
    }

    private static RoleBinding managedRoleBinding(String username, String roleName) {
        var binding = RoleBinding.create(username, roleName);
        binding.getMetadata().setAnnotations(new HashMap<>());
        binding.getMetadata().getAnnotations().put(
            SsoRoleGrantServiceImpl.MANAGED_ROLE_BINDING_ANNOTATION, "true");
        return binding;
    }

    private static User localUser(String username) {
        var user = new User();
        user.setMetadata(new Metadata());
        user.getMetadata().setName(username);
        user.setSpec(new User.UserSpec());
        return user;
    }
}
