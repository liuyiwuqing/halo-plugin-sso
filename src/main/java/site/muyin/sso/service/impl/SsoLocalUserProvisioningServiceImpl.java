package site.muyin.sso.service.impl;

import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.UserService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.sso.model.client.LocalUserProvisioningResult;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.service.SsoLocalUserProvisioningService;
import site.muyin.sso.service.SsoRoleGrantService;

@Service
public class SsoLocalUserProvisioningServiceImpl implements SsoLocalUserProvisioningService {

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final UserService userService;
    private final SsoRoleGrantService roleGrantService;

    public SsoLocalUserProvisioningServiceImpl(ReactiveExtensionClient reactiveExtensionClient,
        UserService userService,
        SsoRoleGrantService roleGrantService) {
        this.reactiveExtensionClient = reactiveExtensionClient;
        this.userService = userService;
        this.roleGrantService = roleGrantService;
    }

    @Override
    public Mono<LocalUserProvisioningResult> provisionWithRX(SsoUserBinding binding,
        OAuthUserInfoResponse userInfo, Set<String> localRoles, boolean syncProfile) {
        var username = binding.getLocalUsername();
        var roles = localRoles == null ? Set.<String>of() : Set.copyOf(localRoles);
        return reactiveExtensionClient.fetch(User.class, username)
            .flatMap(existing -> updateExistingUser(existing, userInfo, roles, syncProfile))
            .switchIfEmpty(Mono.defer(() -> createUser(binding, userInfo, roles)));
    }

    private Mono<LocalUserProvisioningResult> updateExistingUser(User user,
        OAuthUserInfoResponse userInfo, Set<String> roles, boolean syncProfile) {
        var updateUser = syncProfile
            ? reactiveExtensionClient.update(applyProfile(user, userInfo))
            : Mono.just(user);
        return updateUser
            .flatMap(updated -> roleGrantService.grantRoles(user.getMetadata().getName(), roles)
                .thenReturn(LocalUserProvisioningResult.builder()
                    .localUsername(user.getMetadata().getName())
                    .grantedRoles(roles)
                    .created(false)
                    .build()));
    }

    private Mono<LocalUserProvisioningResult> createUser(SsoUserBinding binding,
        OAuthUserInfoResponse userInfo, Set<String> roles) {
        var user = buildUser(binding, userInfo);
        return userService.createUser(user, roles)
            .map(created -> LocalUserProvisioningResult.builder()
                .localUsername(created.getMetadata().getName())
                .grantedRoles(roles)
                .created(true)
                .build());
    }

    private static User buildUser(SsoUserBinding binding, OAuthUserInfoResponse userInfo) {
        var user = new User();
        user.setMetadata(new Metadata());
        user.getMetadata().setName(binding.getLocalUsername());
        user.setSpec(new User.UserSpec());
        applyProfile(user, userInfo);
        user.getSpec().setRegisteredAt(Instant.now());
        user.getSpec().setEmailVerified(true);
        return user;
    }

    private static User applyProfile(User user, OAuthUserInfoResponse userInfo) {
        var spec = user.getSpec();
        if (spec == null) {
            spec = new User.UserSpec();
            user.setSpec(spec);
        }
        spec.setEmail(userInfo.getEmail());
        spec.setDisplayName(displayName(userInfo));
        spec.setAvatar(userInfo.getPicture());
        return user;
    }

    private static String displayName(OAuthUserInfoResponse userInfo) {
        if (userInfo.getName() != null && !userInfo.getName().isBlank()) {
            return userInfo.getName();
        }
        if (userInfo.getPreferredUsername() != null && !userInfo.getPreferredUsername().isBlank()) {
            return userInfo.getPreferredUsername();
        }
        return userInfo.getSub();
    }
}
