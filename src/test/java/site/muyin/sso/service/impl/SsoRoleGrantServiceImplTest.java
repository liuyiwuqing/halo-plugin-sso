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
import java.util.Set;
import org.junit.jupiter.api.Test;
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
    void addsMissingRolesWithoutRevokingExistingRoles() {
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
            .thenReturn(Flux.just(RoleBinding.create("lywq", "guest")));
        when(client.create(any(RoleBinding.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, RoleBinding.class)));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        service.grantRoles("lywq", Set.of("guest", "author")).block();

        verify(client).create(any(RoleBinding.class));
        verify(client, never()).delete(any(RoleBinding.class));
        verify(client).update(eq(user));
    }

    private static User localUser(String username) {
        var user = new User();
        user.setMetadata(new Metadata());
        user.getMetadata().setName(username);
        user.setSpec(new User.UserSpec());
        return user;
    }
}
