package site.muyin.sso.service.impl;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    static final String MANAGED_ROLE_BINDING_ANNOTATION =
        "sso.muyin.site/managed-role-binding";
    private static final String MANAGED_ROLE_BINDING_VALUE = "true";

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
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }
        return reactiveExtensionClient.get(User.class, username)
            .flatMap(user -> {
                var subject = new RoleBinding.Subject();
                subject.setKind(User.KIND);
                subject.setApiGroup(User.GROUP);
                subject.setName(username);

                return roleService.listRoleBindings(subject)
                    .collectList()
                    .flatMap(bindings -> {
                        var existingRoles = roleNames(bindings);
                        var rolesToAdd = new HashSet<>(targetRoles);
                        rolesToAdd.removeAll(existingRoles);
                        var bindingsToRemove = bindings.stream()
                            .filter(SsoRoleGrantServiceImpl::isManagedRoleBinding)
                            .filter(binding -> !targetRoles.contains(roleName(binding)))
                            .toList();
                        if (rolesToAdd.isEmpty() && bindingsToRemove.isEmpty()) {
                            return Mono.just(user);
                        }
                        var additions = Flux.fromIterable(rolesToAdd)
                            .map(roleName -> createManagedRoleBinding(username, roleName))
                            .concatMap(reactiveExtensionClient::create);
                        var removals = Flux.fromIterable(bindingsToRemove)
                            .concatMap(reactiveExtensionClient::delete);
                        return Flux.concat(additions, removals)
                            .then(updateUserRoleRefreshMarker(user));
                    });
            })
            .then();
    }

    private Mono<User> updateUserRoleRefreshMarker(User user) {
        var annotations = new HashMap<>(Optional.ofNullable(user.getMetadata().getAnnotations())
            .orElseGet(HashMap::new));
        user.getMetadata().setAnnotations(annotations);
        annotations.put(User.REQUEST_TO_UPDATE, clock.instant().toString());
        return reactiveExtensionClient.update(user);
    }

    private static RoleBinding createManagedRoleBinding(String username, String roleName) {
        var binding = RoleBinding.create(username, roleName);
        var annotations = new HashMap<>(Optional.ofNullable(
                binding.getMetadata().getAnnotations())
            .orElseGet(HashMap::new));
        annotations.put(MANAGED_ROLE_BINDING_ANNOTATION, MANAGED_ROLE_BINDING_VALUE);
        binding.getMetadata().setAnnotations(annotations);
        return binding;
    }

    private static boolean isManagedRoleBinding(RoleBinding binding) {
        return Optional.ofNullable(binding.getMetadata())
            .map(metadata -> metadata.getAnnotations())
            .map(annotations -> annotations.get(MANAGED_ROLE_BINDING_ANNOTATION))
            .filter(MANAGED_ROLE_BINDING_VALUE::equals)
            .isPresent();
    }

    private static String roleName(RoleBinding binding) {
        return binding.getRoleRef() == null ? null : binding.getRoleRef().getName();
    }

    private static Set<String> roleNames(List<RoleBinding> bindings) {
        return bindings.stream()
            .map(SsoRoleGrantServiceImpl::roleName)
            .filter(role -> role != null && !role.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream()
            .filter(role -> role != null && !role.isBlank())
            .map(String::trim)
            .collect(Collectors.toUnmodifiableSet());
    }
}
