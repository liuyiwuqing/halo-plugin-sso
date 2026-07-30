package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.UserService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.service.SsoRoleGrantService;
import site.muyin.sso.userbinding.SsoLocalUsername;

class SsoLocalUserProvisioningServiceImplTest {

    @Test
    void createsLocalUserWhenBindingHasNoExistingUser() {
        var client = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new SsoLocalUserProvisioningServiceImpl(client, userService, roleGrantService);
        var managedBinding = binding()
            .setLocalUsername(SsoLocalUsername.fromSubject("user-001"));

        when(client.fetch(User.class, managedBinding.getLocalUsername())).thenReturn(Mono.empty());
        when(userService.createUser(any(User.class), eq(Set.of("guest"))))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        var result = service.provisionWithRX(
            managedBinding, userInfo(), Set.of("guest"), true).block();

        assertThat(result.isCreated()).isTrue();
        assertThat(result.getLocalUsername()).startsWith("sso-");
        assertThat(result.getGrantedRoles()).containsExactly("guest");
        var createdUser = ArgumentCaptor.forClass(User.class);
        verify(userService).createUser(createdUser.capture(), eq(Set.of("guest")));
        assertThat(createdUser.getValue().getMetadata().getAnnotations())
            .containsEntry(SsoLocalUserProvisioningServiceImpl.SSO_SUBJECT_ANNOTATION, "user-001");
        assertThat(createdUser.getValue().getSpec().isEmailVerified()).isFalse();
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
        assertThat(existing.getMetadata().getAnnotations())
            .containsEntry(SsoLocalUserProvisioningServiceImpl.SSO_SUBJECT_ANNOTATION, "user-001");
    }

    @Test
    void rejectsPreexistingUserForManagedBindingWithoutMatchingOwnership() {
        var client = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new SsoLocalUserProvisioningServiceImpl(client, userService, roleGrantService);
        var binding = binding()
            .setLocalUsername(SsoLocalUsername.fromSubject("user-001"));

        when(client.fetch(User.class, binding.getLocalUsername()))
            .thenReturn(Mono.just(localUser(binding.getLocalUsername())));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));
        when(roleGrantService.grantRoles(anyString(), eq(Set.of("guest"))))
            .thenReturn(Mono.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provisionWithRX(
                binding, userInfo(), Set.of("guest"), true).block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsLegacyBindingWhenLocalUserBelongsToAnotherSubject() {
        var client = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new SsoLocalUserProvisioningServiceImpl(client, userService, roleGrantService);
        var existing = localUser("lywq");
        existing.getMetadata().setAnnotations(new HashMap<>());
        existing.getMetadata().getAnnotations().put(
            SsoLocalUserProvisioningServiceImpl.SSO_SUBJECT_ANNOTATION, "user-002");

        when(client.fetch(User.class, "lywq")).thenReturn(Mono.just(existing));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provisionWithRX(
                binding(), userInfo(), Set.of("guest"), true).block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void claimsLegacyBindingWhenUsernameOnlySharesManagedPrefix() {
        var client = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new SsoLocalUserProvisioningServiceImpl(client, userService, roleGrantService);
        var legacyBinding = binding().setLocalUsername("sso-legacy-user");
        var existing = localUser("sso-legacy-user");

        when(client.fetch(User.class, "sso-legacy-user")).thenReturn(Mono.just(existing));
        when(client.update(any(User.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));
        when(roleGrantService.grantRoles(anyString(), eq(Set.of("guest"))))
            .thenReturn(Mono.empty());

        var result = service.provisionWithRX(
            legacyBinding, userInfo(), Set.of("guest"), true).block();

        assertThat(result.isCreated()).isFalse();
        assertThat(result.getLocalUsername()).isEqualTo("sso-legacy-user");
        assertThat(existing.getMetadata().getAnnotations())
            .containsEntry(SsoLocalUserProvisioningServiceImpl.SSO_SUBJECT_ANNOTATION, "user-001");
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
