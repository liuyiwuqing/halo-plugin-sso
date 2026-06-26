package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.UserService;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.clientlogin.CenterOAuthClient;
import site.muyin.sso.clientlogin.ClientLoginSessionManager;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.service.SsoAuditLogService;
import site.muyin.sso.service.SsoLocalUserProvisioningService;
import site.muyin.sso.service.SsoRoleMappingService;
import site.muyin.sso.service.SsoRoleGrantService;
import site.muyin.sso.service.SsoUserBindingService;
import site.muyin.sso.scheme.SsoRoleMapping;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.setting.SsoGeneralSetting;

class ClientLoginServiceImplTest {

    @Test
    void startsClientLoginAndExchangesCallbackForUserInfo() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerOAuthClient = mock(CenterOAuthClient.class);
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var userService = mock(UserService.class);
        var roleGrantService = mock(SsoRoleGrantService.class);
        var service = new ClientLoginServiceImpl(
            settingFetcher,
            new ClientLoginSessionManager(),
            centerOAuthClient,
            new SsoUserBindingServiceImpl(reactiveExtensionClient),
            new SsoRoleMappingServiceImpl(reactiveExtensionClient),
            new SsoLocalUserProvisioningServiceImpl(
                reactiveExtensionClient,
                userService,
                roleGrantService
            ),
            new SsoAuditLogServiceImpl(reactiveExtensionClient)
        );
        var generalSetting = clientModeGeneralSetting();

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(generalSetting));

        var startResult = service.startLogin("/posts/1", "https://b.example.com/").block();
        var authorizeParams = UriComponentsBuilder
            .fromUri(URI.create(startResult.getRedirectUri()))
            .build()
            .getQueryParams();
        var state = authorizeParams.getFirst("state");
        var callbackUri = "https://b.example.com" + OAuthEndpointPaths.CLIENT_CALLBACK;

        assertThat(URI.create(startResult.getRedirectUri()).getHost()).isEqualTo("auth.example.com");
        assertThat(URI.create(startResult.getRedirectUri()).getPath())
            .isEqualTo(OAuthEndpointPaths.AUTHORIZE);
        assertThat(authorizeParams.getFirst("client_id")).isEqualTo("site-b");
        assertThat(decoded(authorizeParams.getFirst("redirect_uri"))).isEqualTo(callbackUri);
        assertThat(decoded(authorizeParams.getFirst("scope"))).isEqualTo("openid profile email");
        assertThat(authorizeParams.getFirst("code_challenge")).isNotBlank();
        assertThat(state).isNotBlank();

        when(centerOAuthClient.exchangeCode(eq("https://auth.example.com/"),
            argThat(request -> tokenRequestMatches(request, callbackUri))))
            .thenReturn(Mono.just(OAuthTokenResponse.builder()
                .accessToken("access-001")
                .idToken("id-001")
                .tokenType("Bearer")
                .expiresIn(900)
                .build()));
        when(centerOAuthClient.userInfo("https://auth.example.com/", "access-001"))
            .thenReturn(Mono.just(OAuthUserInfoResponse.builder()
                .sub("user-001")
                .preferredUsername("lywq")
                .email("lywq@example.com")
                .name("Lywq")
                .picture("https://example.com/avatar.png")
                .roles(Set.of("author"))
                .build()));
        when(reactiveExtensionClient.fetch(eq(SsoUserBinding.class), anyString()))
            .thenReturn(Mono.empty());
        when(reactiveExtensionClient.fetch(eq(User.class), eq("lywq")))
            .thenReturn(Mono.empty());
        when(reactiveExtensionClient.listAll(eq(SsoRoleMapping.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.empty());
        when(reactiveExtensionClient.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));
        when(userService.createUser(any(User.class), eq(Set.of("guest"))))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));
        when(roleGrantService.grantRoles(anyString(), any()))
            .thenReturn(Mono.empty());

        var callbackResult = service.handleCallback("code-001", state,
            "https://b.example.com/").block();

        assertThat(callbackResult.getReturnUrl()).isEqualTo("/posts/1");
        assertThat(callbackResult.getLocalUsername()).isEqualTo("lywq");
        assertThat(callbackResult.getGrantedRoles()).containsExactly("guest");
        assertThat(callbackResult.isLocalUserCreated()).isTrue();
        assertThat(callbackResult.getUserInfo().getSub()).isEqualTo("user-001");
        assertThat(callbackResult.getUserInfo().getPreferredUsername()).isEqualTo("lywq");
        assertThat(callbackResult.getUserInfo().getEmail()).isEqualTo("lywq@example.com");
    }

    @Test
    void mapsCenterUserInfoUnauthorizedToCallbackUnauthorized() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerOAuthClient = mock(CenterOAuthClient.class);
        var auditLogService = mock(SsoAuditLogService.class);
        var service = new ClientLoginServiceImpl(
            settingFetcher,
            new ClientLoginSessionManager(),
            centerOAuthClient,
            mock(SsoUserBindingService.class),
            mock(SsoRoleMappingService.class),
            mock(SsoLocalUserProvisioningService.class),
            auditLogService
        );
        var generalSetting = clientModeGeneralSetting();

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(generalSetting));
        when(auditLogService.recordLoginFailure(eq("site-b"),
            nullable(OAuthUserInfoResponse.class), anyString()))
            .thenReturn(Mono.empty());

        var startResult = service.startLogin("/posts/1", "https://b.example.com/").block();
        var state = UriComponentsBuilder
            .fromUri(URI.create(startResult.getRedirectUri()))
            .build()
            .getQueryParams()
            .getFirst("state");
        var callbackUri = "https://b.example.com" + OAuthEndpointPaths.CLIENT_CALLBACK;

        when(centerOAuthClient.exchangeCode(eq("https://auth.example.com/"),
            argThat(request -> tokenRequestMatches(request, callbackUri))))
            .thenReturn(Mono.just(OAuthTokenResponse.builder()
                .accessToken("access-001")
                .idToken("id-001")
                .tokenType("Bearer")
                .expiresIn(900)
                .build()));
        when(centerOAuthClient.userInfo("https://auth.example.com/", "access-001"))
            .thenReturn(Mono.error(WebClientResponseException.create(
                401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null)));

        assertThatThrownBy(() -> service.handleCallback("code-001", state,
                "https://b.example.com/").block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                var responseStatus = (ResponseStatusException) error;
                assertThat(responseStatus.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(responseStatus.getReason())
                    .contains("身份中心 OAuth 请求失败")
                    .contains("401");
            });
    }

    private static boolean tokenRequestMatches(OAuthTokenRequest request, String callbackUri) {
        return request != null
            && "authorization_code".equals(request.getGrantType())
            && "code-001".equals(request.getCode())
            && callbackUri.equals(request.getRedirectUri())
            && "site-b".equals(request.getClientId())
            && "secret-b".equals(request.getClientSecret())
            && request.getCodeVerifier() != null
            && !request.getCodeVerifier().isBlank();
    }

    private static String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static SsoGeneralSetting clientModeGeneralSetting() {
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        setting.setCenterUrl("https://auth.example.com/");
        setting.setClientId("site-b");
        setting.setClientSecret("secret-b");
        return setting;
    }
}
