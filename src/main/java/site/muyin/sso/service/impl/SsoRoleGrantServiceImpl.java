package site.muyin.sso.service.impl;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.RoleBinding;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.sso.service.SsoRoleGrantService;

@Service
public class SsoRoleGrantServiceImpl implements SsoRoleGrantService {

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final RoleService roleService;
    private final Clock clock;

    @Autowired
    public SsoRoleGrantServiceImpl(ReactiveExtensionClient reactiveExtensionClient,
        RoleService roleService) {
        this(reactiveExtensionClient, roleService, Clock.systemUTC());
    }

    SsoRoleGrantServiceImpl(ReactiveExtensionClient reactiveExtensionClient,
        RoleService roleService, Clock clock) {
        this.reactiveExtensionClient = reactiveExtensionClient;
        this.roleService = roleService;
        this.clock = clock;
    }

    @Override
    public Mono<Void> grantRoles(String username, Set<String> roles) {
        var targetRoles = normalizeRoles(roles);
        if (username == null || username.isBlank() || targetRoles.isEmpty()) {
            return Mono.empty();
        }
        return reactiveExtensionClient.get(User.class, username)
            .flatMap(user -> {
                var subject = new RoleBinding.Subject();
                subject.setKind(User.KIND);
                subject.setApiGroup(User.GROUP);
                subject.setName(username);

                return roleService.listRoleBindings(subject)
                    .map(binding -> binding.getRoleRef().getName())
                    .collect(java.util.stream.Collectors.toSet())
                    .flatMap(existingRoles -> {
                        var rolesToAdd = new HashSet<>(targetRoles);
                        rolesToAdd.removeAll(existingRoles);
                        if (rolesToAdd.isEmpty()) {
                            return Mono.just(user);
                        }
                        return Flux.fromIterable(rolesToAdd)
                            .map(roleName -> RoleBinding.create(username, roleName))
                            .flatMap(reactiveExtensionClient::create)
                            .then(updateUserRoleRefreshMarker(user));
                    });
            })
            .then();
    }

    private Mono<User> updateUserRoleRefreshMarker(User user) {
        var annotations = Optional.ofNullable(user.getMetadata().getAnnotations())
            .orElseGet(HashMap::new);
        user.getMetadata().setAnnotations(annotations);
        annotations.put(User.REQUEST_TO_UPDATE, clock.instant().toString());
        return reactiveExtensionClient.update(user);
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream()
            .filter(role -> role != null && !role.isBlank())
            .map(String::trim)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
