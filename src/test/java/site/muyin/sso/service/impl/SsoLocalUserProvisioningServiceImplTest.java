package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.UserService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.service.SsoRoleGrantService;

class SsoLocalUserProvisioningServiceImplTest {

    @Test
    void createsLocalUserWhenBindingHasNoExistingUser() {
        var client = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new SsoLocalUserProvisioningServiceImpl(client, userService, roleGrantService);

        when(client.fetch(User.class, "lywq")).thenReturn(Mono.empty());
        when(userService.createUser(any(User.class), eq(Set.of("guest"))))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        var result = service.provisionWithRX(binding(), userInfo(), Set.of("guest"), true).block();

        assertThat(result.isCreated()).isTrue();
        assertThat(result.getLocalUsername()).isEqualTo("lywq");
        assertThat(result.getGrantedRoles()).containsExactly("guest");
    }

    @Test
    void syncsProfileAndGrantsRolesWhenLocalUserExists() {
        var client = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new SsoLocalUserProvisioningServiceImpl(client, userService, roleGrantService);
        var existing = localUser("lywq");

        when(client.fetch(User.class, "lywq")).thenReturn(Mono.just(existing));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));
        when(roleGrantService.grantRoles(anyString(), eq(Set.of("guest"))))
            .thenReturn(Mono.empty());

        var result = service.provisionWithRX(binding(), userInfo(), Set.of("guest"), true).block();

        assertThat(result.isCreated()).isFalse();
        assertThat(result.getLocalUsername()).isEqualTo("lywq");
        assertThat(existing.getSpec().getEmail()).isEqualTo("lywq@example.com");
        assertThat(existing.getSpec().getDisplayName()).isEqualTo("Lywq");
        assertThat(existing.getSpec().getAvatar()).isEqualTo("https://example.com/avatar.png");
    }

    private static SsoUserBinding binding() {
        return new SsoUserBinding()
            .setSubject("user-001")
            .setEmail("lywq@example.com")
            .setLocalUsername("lywq");
    }

    private static OAuthUserInfoResponse userInfo() {
        return OAuthUserInfoResponse.builder()
            .sub("user-001")
            .preferredUsername("lywq")
            .email("lywq@example.com")
            .name("Lywq")
            .picture("https://example.com/avatar.png")
            .roles(Set.of("author"))
            .build();
    }

    private static User localUser(String username) {
        var user = new User();
        user.setMetadata(new Metadata());
        user.getMetadata().setName(username);
        user.setSpec(new User.UserSpec());
        return user;
    }
}
