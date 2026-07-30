package site.muyin.sso.service.impl;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.clientlogin.CenterOAuthClient;
import site.muyin.sso.clientlogin.ClientLoginException;
import site.muyin.sso.clientlogin.ClientLoginSession;
import site.muyin.sso.clientlogin.ClientLoginSessionManager;
import site.muyin.sso.model.client.ClientLoginCallbackResult;
import site.muyin.sso.model.client.ClientLoginStartResult;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.service.ClientLoginService;
import site.muyin.sso.service.SsoAuditLogService;
import site.muyin.sso.service.SsoLocalUserProvisioningService;
import site.muyin.sso.service.SsoRoleMappingService;
import site.muyin.sso.service.SsoUserBindingService;
import site.muyin.sso.setting.SsoGeneralSetting;

@Service
public class ClientLoginServiceImpl implements ClientLoginService {

    private static final String DEFAULT_SCOPE = "openid profile email";

    private final ReactiveSettingFetcher settingFetcher;
    private final ClientLoginSessionManager sessionManager;
    private final CenterOAuthClient centerOAuthClient;
    private final SsoUserBindingService userBindingService;
    private final SsoRoleMappingService roleMappingService;
    private final SsoLocalUserProvisioningService localUserProvisioningService;
    private final SsoAuditLogService auditLogService;

    public ClientLoginServiceImpl(ReactiveSettingFetcher settingFetcher,
        ClientLoginSessionManager sessionManager,
        CenterOAuthClient centerOAuthClient,
        SsoUserBindingService userBindingService,
        SsoRoleMappingService roleMappingService,
        SsoLocalUserProvisioningService localUserProvisioningService,
        SsoAuditLogService auditLogService) {
        this.settingFetcher = settingFetcher;
        this.sessionManager = sessionManager;
        this.centerOAuthClient = centerOAuthClient;
        this.userBindingService = userBindingService;
        this.roleMappingService = roleMappingService;
        this.localUserProvisioningService = localUserProvisioningService;
        this.auditLogService = auditLogService;
    }

    @Override
    public Mono<ClientLoginStartResult> startLogin(String returnUrl, String externalUrl,
        String requesterKey) {
        var safeReturnUrl = sanitizeReturnUrl(returnUrl);
        return runtimeSettings(externalUrl)
            .map(settings -> {
                var session = sessionManager.start(safeReturnUrl, requesterKey);
                var callbackUri = callbackUri(settings.externalUrl());
                var redirectUri = UriComponentsBuilder
                    .fromUriString(endpoint(settings.general().getCenterUrl(),
                        OAuthEndpointPaths.AUTHORIZE))
                    .queryParam("response_type", "code")
                    .queryParam("client_id", settings.general().getClientId())
                    .queryParam("redirect_uri", callbackUri)
                    .queryParam("scope", DEFAULT_SCOPE)
                    .queryParam("state", session.state())
                    .queryParam("code_challenge", session.codeChallenge())
                    .queryParam("code_challenge_method", "S256")
                    .encode()
                    .build()
                    .toUriString();
                return ClientLoginStartResult.builder()
                    .redirectUri(redirectUri)
                    .build();
            })
            .onErrorMap(ClientLoginException.class,
                error -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    error.getMessage(), error));
    }

    @Override
    public Mono<ClientLoginCallbackResult> handleCallback(String code, String state,
        String externalUrl) {
        requireText(code, "code");
        return runtimeSettings(externalUrl)
            .flatMap(settings -> {
                var session = consumeState(state);
                var loadedUserInfo = new AtomicReference<OAuthUserInfoResponse>();
                return exchangeAndLoadUserInfo(settings, session, code)
                    .doOnNext(loadedUserInfo::set)
                    .flatMap(userInfo -> userBindingService.bindOrUpdateWithRX(userInfo)
                        .flatMap(binding -> roleMappingService.resolveLocalRoles(
                                userInfo.getRoles(), settings.general().getDefaultRole())
                            .flatMap(localRoles -> localUserProvisioningService.provisionWithRX(
                                    binding,
                                    userInfo,
                                    localRoles,
                                    !Boolean.FALSE.equals(
                                        settings.general().getSyncProfileOnLogin())
                                )
                                .map(provisioning -> ClientLoginCallbackResult.builder()
                                    .userInfo(userInfo)
                                    .localUsername(provisioning.getLocalUsername())
                                    .grantedRoles(provisioning.getGrantedRoles())
                                    .localUserCreated(provisioning.isCreated())
                                    .returnUrl(session.returnUrl())
                                    .build())
                                .flatMap(result -> safeAudit(auditLogService.recordLoginSuccess(
                                    settings.general().getClientId(),
                                    userInfo,
                                    "接入站登录成功"
                                )).thenReturn(result)))))
                    .onErrorResume(error -> safeAudit(auditLogService.recordLoginFailure(
                            settings.general().getClientId(),
                            loadedUserInfo.get(),
                            errorMessage(error)
                        ))
                        .then(Mono.<ClientLoginCallbackResult>error(error)));
            });
    }

    private Mono<OAuthUserInfoResponse> exchangeAndLoadUserInfo(ClientRuntimeSettings settings,
        ClientLoginSession session, String code) {
        var tokenRequest = OAuthTokenRequest.builder()
            .grantType("authorization_code")
            .code(code)
            .redirectUri(callbackUri(settings.externalUrl()))
            .clientId(settings.general().getClientId())
            .clientSecret(settings.general().getClientSecret())
            .codeVerifier(session.codeVerifier())
            .build();
        return centerOAuthClient.exchangeCode(settings.general().getCenterUrl(), tokenRequest)
            .flatMap(token -> centerOAuthClient.userInfo(settings.general().getCenterUrl(),
                token.getAccessToken()))
            .onErrorMap(WebClientResponseException.class,
                ClientLoginServiceImpl::toCenterOAuthException)
            .onErrorMap(WebClientRequestException.class,
                error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法连接身份中心 OAuth 服务", error))
            .onErrorMap(TimeoutException.class,
                error -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "身份中心 OAuth 请求超时", error));
    }

    private Mono<ClientRuntimeSettings> runtimeSettings(String externalUrl) {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .map(general -> {
                var settings = new ClientRuntimeSettings(general,
                    resolveExternalUrl(externalUrl, general));
                validateRuntimeSettings(settings);
                return settings;
            });
    }

    private static void validateRuntimeSettings(ClientRuntimeSettings settings) {
        if (!"client".equals(settings.general().getMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前插件不是接入站模式");
        }
        requireText(settings.externalUrl(), "externalUrl");
        requireText(settings.general().getCenterUrl(), "centerUrl");
        requireText(settings.general().getClientId(), "clientId");
        requireText(settings.general().getClientSecret(), "clientSecret");
    }

    private static String sanitizeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "/";
        }
        var trimmed = returnUrl.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "return_url 必须是站内路径");
        }
        return trimmed;
    }

    private ClientLoginSession consumeState(String state) {
        try {
            return sessionManager.consume(state);
        } catch (ClientLoginException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    private static String callbackUri(String externalUrl) {
        return endpoint(externalUrl, OAuthEndpointPaths.CLIENT_CALLBACK);
    }

    private static String endpoint(String baseUrl, String path) {
        return trimTrailingSlash(baseUrl) + path;
    }

    private static String resolveExternalUrl(String requestExternalUrl, SsoGeneralSetting general) {
        if (requestExternalUrl != null && !requestExternalUrl.isBlank()) {
            return requestExternalUrl;
        }
        return general.getExternalUrl();
    }

    private static String trimTrailingSlash(String value) {
        var trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Mono<Void> safeAudit(Mono<?> auditAction) {
        return auditAction.onErrorResume(error -> Mono.empty()).then();
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof ResponseStatusException responseStatusException
            && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static ResponseStatusException toCenterOAuthException(WebClientResponseException error) {
        var statusCode = callbackStatusCode(error.getStatusCode());
        return new ResponseStatusException(statusCode,
            "身份中心 OAuth 请求失败: HTTP " + error.getStatusCode().value(), error);
    }

    private static HttpStatusCode callbackStatusCode(HttpStatusCode upstreamStatusCode) {
        return upstreamStatusCode.is5xxServerError() ? HttpStatus.BAD_GATEWAY : upstreamStatusCode;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 不能为空");
        }
    }

    private record ClientRuntimeSettings(SsoGeneralSetting general, String externalUrl) {
    }
}
