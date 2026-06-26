package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.core.user.service.UserService;
import run.halo.app.extension.Metadata;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.setting.SsoGeneralSetting;

class HaloCenterUserClaimServiceTest {

    @Test
    void onlyEmitsConfiguredStandardRoles() {
        var userService = mock(UserService.class);
        var roleService = mock(RoleService.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var service = new HaloCenterUserClaimService(userService, roleService, settingFetcher);
        var setting = new SsoGeneralSetting();
        setting.setStandardRoles(Set.of("author", "subscriber"));

        when(userService.getUser("lywq")).thenReturn(Mono.just(localUser("lywq")));
        when(roleService.getRolesByUsername("lywq"))
            .thenReturn(Flux.just("author", "super_admin", "guest"));
        when(settingFetcher.fetch("general", SsoGeneralSetting.class)).thenReturn(Mono.just(setting));

        var claim = service.currentUser()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken("lywq", "n/a",
                    AuthorityUtils.NO_AUTHORITIES)
            ))
            .block();

        assertThat(claim.getRoles()).containsExactlyInAnyOrder("author");
    }

    @Test
    void usesDefaultStandardRolesWhenCenterSettingIsMissing() {
        var userService = mock(UserService.class);
        var roleService = mock(RoleService.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var service = new HaloCenterUserClaimService(userService, roleService, settingFetcher);

        when(userService.getUser("lywq")).thenReturn(Mono.just(localUser("lywq")));
        when(roleService.getRolesByUsername("lywq"))
            .thenReturn(Flux.just("author", "super_admin"));
        when(settingFetcher.fetch("general", SsoGeneralSetting.class)).thenReturn(Mono.empty());

        var claim = service.currentUser()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken("lywq", "n/a",
                    AuthorityUtils.NO_AUTHORITIES)
            ))
            .block();

        assertThat(claim.getRoles()).containsExactlyInAnyOrder("author");
    }

    @Test
    void rejectsUnverifiedEmailWhenRequired() {
        var userService = mock(UserService.class);
        var roleService = mock(RoleService.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var service = new HaloCenterUserClaimService(userService, roleService, settingFetcher);
        var setting = new SsoGeneralSetting();
        setting.setRequireVerifiedEmail(true);
        var user = localUser("lywq");
        user.getSpec().setEmailVerified(false);

        when(userService.getUser("lywq")).thenReturn(Mono.just(user));
        when(settingFetcher.fetch("general", SsoGeneralSetting.class)).thenReturn(Mono.just(setting));

        assertThatThrownBy(() -> service.currentUser()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken("lywq", "n/a",
                    AuthorityUtils.NO_AUTHORITIES)
            ))
            .block())
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("用户邮箱未验证");
    }

    private static User localUser(String username) {
        var user = new User();
        user.setMetadata(new Metadata());
        user.getMetadata().setName(username);
        var spec = new User.UserSpec();
        spec.setEmail("lywq@example.com");
        spec.setEmailVerified(true);
        spec.setDisplayName("Lywq");
        spec.setAvatar("https://example.com/avatar.png");
        user.setSpec(spec);
        return user;
    }
}
