package site.muyin.sso.service.impl;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.core.user.service.UserService;
import run.halo.app.infra.AnonymousUserConst;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.model.oauth.CenterUserClaim;
import site.muyin.sso.service.CenterUserClaimService;
import site.muyin.sso.setting.SsoGeneralSetting;

@Service
public class HaloCenterUserClaimService implements CenterUserClaimService {

    private final UserService userService;
    private final RoleService roleService;
    private final ReactiveSettingFetcher settingFetcher;

    public HaloCenterUserClaimService(UserService userService, RoleService roleService,
        ReactiveSettingFetcher settingFetcher) {
        this.userService = userService;
        this.roleService = roleService;
        this.settingFetcher = settingFetcher;
    }

    @Override
    public Mono<CenterUserClaim> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
            .map(context -> context.getAuthentication())
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .filter(username -> !AnonymousUserConst.PRINCIPAL.equals(username))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "用户未登录身份中心")))
            .flatMap(userService::getUser)
            .flatMap(user -> {
                var spec = user.getSpec();
                var email = spec.getEmail();
                if (email == null || email.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户邮箱为空，不能跨站登录");
                }
                var username = user.getMetadata().getName();
                return generalSetting()
                    .flatMap(setting -> {
                        if (Boolean.TRUE.equals(setting.getRequireVerifiedEmail())
                            && !Boolean.TRUE.equals(spec.isEmailVerified())) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "用户邮箱未验证，不能跨站登录"));
                        }
                        var allowedRoles = normalizeStandardRoles(setting.getStandardRoles());
                        return roleService.getRolesByUsername(username)
                            .collectList()
                            .map(roles -> CenterUserClaim.builder()
                                .subject(username)
                                .username(username)
                                .email(email)
                                .displayName(spec.getDisplayName())
                                .avatar(spec.getAvatar())
                                .roles(filterAllowedRoles(Set.copyOf(roles), allowedRoles))
                                .build());
                    });
            });
    }

    private Mono<SsoGeneralSetting> generalSetting() {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting());
    }

    private static Set<String> filterAllowedRoles(Set<String> roles, Set<String> allowedRoles) {
        if (roles == null || roles.isEmpty() || allowedRoles == null || allowedRoles.isEmpty()) {
            return Set.of();
        }
        return roles.stream()
            .map(HaloCenterUserClaimService::normalizeRole)
            .filter(role -> role != null && allowedRoles.contains(role))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalizeStandardRoles(Set<String> roles) {
        var source = roles == null ? SsoGeneralSetting.DEFAULT_STANDARD_ROLES : roles;
        return source.stream()
            .map(HaloCenterUserClaimService::normalizeRole)
            .filter(role -> role != null)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeRole(String role) {
        return role == null || role.isBlank() ? null : role.trim();
    }
}
