package site.muyin.sso.service.impl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.CenterUserClaim;
import site.muyin.sso.model.oauth.OAuthAuthorizeRequest;
import site.muyin.sso.model.oauth.OAuthAuthorizeResult;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.AuthorizationCodeConsumeRequest;
import site.muyin.sso.oauth.AuthorizationCodeException;
import site.muyin.sso.oauth.AuthorizationCodeIssueRequest;
import site.muyin.sso.oauth.AuthorizationCodeManager;
import site.muyin.sso.oauth.token.OAuthTokenClaims;
import site.muyin.sso.oauth.token.OAuthTokenStore;
import site.muyin.sso.service.CenterUserClaimService;
import site.muyin.sso.service.OAuthAuthorizationService;
import site.muyin.sso.service.SsoClientService;

@Service
public class OAuthAuthorizationServiceImpl implements OAuthAuthorizationService {

    private final SsoClientService ssoClientService;
    private final CenterUserClaimService centerUserClaimService;
    private final AuthorizationCodeManager authorizationCodeManager;
    private final OAuthTokenStore tokenStore;

    public OAuthAuthorizationServiceImpl(SsoClientService ssoClientService,
        CenterUserClaimService centerUserClaimService,
        AuthorizationCodeManager authorizationCodeManager,
        OAuthTokenStore tokenStore) {
        this.ssoClientService = ssoClientService;
        this.centerUserClaimService = centerUserClaimService;
        this.authorizationCodeManager = authorizationCodeManager;
        this.tokenStore = tokenStore;
    }

    @Override
    public Mono<OAuthAuthorizeResult> authorize(OAuthAuthorizeRequest request) {
        validateAuthorizeRequest(request);
        return ssoClientService.requireAuthorizedClientWithRX(request.getClientId(),
                request.getRedirectUri())
            .then(centerUserClaimService.currentUser())
            .map(user -> issueCodeAndBuildRedirect(request, user))
            .onErrorMap(AuthorizationCodeException.class,
                error -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    error.getMessage(), error));
    }

    @Override
    public Mono<OAuthTokenResponse> token(OAuthTokenRequest request) {
        validateTokenRequest(request);
        return ssoClientService.verifySecretWithRX(request.getClientId(), request.getClientSecret())
            .flatMap(valid -> {
                if (!valid) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "client_secret 校验失败"));
                }
                return Mono.fromCallable(() -> authorizationCodeManager.consume(
                    AuthorizationCodeConsumeRequest.builder()
                        .code(request.getCode())
                        .clientId(request.getClientId())
                        .redirectUri(request.getRedirectUri())
                        .codeVerifier(request.getCodeVerifier())
                        .build()
                ));
            })
            .onErrorMap(AuthorizationCodeException.class,
                error -> new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage()))
            .zipWhen(grant -> ssoClientService.getByClientIdWithRX(grant.clientId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "client_id 无效"))))
            .map(tuple -> {
                var grant = tuple.getT1();
                var client = tuple.getT2();
                var tokens = tokenStore.issue(new OAuthTokenClaims(
                    grant.clientId(),
                    grant.subject(),
                    fallbackUsername(grant.username(), grant.subject()),
                    grant.email(),
                    grant.displayName(),
                    grant.avatar(),
                    grant.roles(),
                    grant.scopes(),
                    grant.issuedAt(),
                    null
                ), client.getClientSecretHash());
                return OAuthTokenResponse.builder()
                    .accessToken(tokens.accessToken())
                    .idToken(tokens.idToken())
                    .tokenType(tokens.tokenType())
                    .expiresIn(tokens.expiresIn())
                    .build();
            });
    }

    @Override
    public Mono<OAuthUserInfoResponse> userInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "access_token 不能为空"));
        }
        return Mono.justOrEmpty(tokenStore.clientId(accessToken))
            .switchIfEmpty(Mono.error(invalidAccessToken()))
            .flatMap(clientId -> ssoClientService.getByClientIdWithRX(clientId)
                .filter(client -> Boolean.TRUE.equals(client.getEnabled()))
                .switchIfEmpty(Mono.error(invalidAccessToken()))
                .flatMap(client -> Mono.justOrEmpty(tokenStore.findAccessToken(accessToken,
                    client.getClientSecretHash())))
            )
            .switchIfEmpty(Mono.error(invalidAccessToken()))
            .map(claims -> OAuthUserInfoResponse.builder()
                .sub(claims.subject())
                .preferredUsername(claims.username())
                .email(claims.email())
                .name(claims.displayName())
                .picture(claims.avatar())
                .roles(claims.roles())
                .build());
    }

    private static ResponseStatusException invalidAccessToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "access_token 无效或已过期");
    }

    private OAuthAuthorizeResult issueCodeAndBuildRedirect(OAuthAuthorizeRequest request,
        CenterUserClaim user) {
        var issuedCode = authorizationCodeManager.issue(AuthorizationCodeIssueRequest.builder()
            .clientId(request.getClientId())
            .redirectUri(request.getRedirectUri())
            .subject(user.getSubject())
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .avatar(user.getAvatar())
            .roles(user.getRoles())
            .codeChallenge(request.getCodeChallenge())
            .scopes(request.scopes())
            .build());
        var redirectUri = UriComponentsBuilder.fromUriString(request.getRedirectUri())
            .queryParam("code", issuedCode.code())
            .queryParam("state", request.getState())
            .encode()
            .build()
            .toUriString();
        return OAuthAuthorizeResult.builder()
            .redirectUri(redirectUri)
            .build();
    }

    private static void validateAuthorizeRequest(OAuthAuthorizeRequest request) {
        requireEquals(request.getResponseType(), "code", "response_type");
        requireText(request.getClientId(), "client_id");
        requireText(request.getRedirectUri(), "redirect_uri");
        requireText(request.getState(), "state");
        requireText(request.getCodeChallenge(), "code_challenge");
        requireEquals(request.getCodeChallengeMethod(), "S256", "code_challenge_method");
    }

    private static void validateTokenRequest(OAuthTokenRequest request) {
        requireEquals(request.getGrantType(), "authorization_code", "grant_type");
        requireText(request.getCode(), "code");
        requireText(request.getRedirectUri(), "redirect_uri");
        requireText(request.getClientId(), "client_id");
        requireText(request.getClientSecret(), "client_secret");
        requireText(request.getCodeVerifier(), "code_verifier");
    }

    private static void requireEquals(String actual, String expected, String fieldName) {
        if (!expected.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fieldName + " 必须为 " + expected);
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 不能为空");
        }
    }

    private static String fallbackUsername(String username, String subject) {
        return username == null || username.isBlank() ? subject : username;
    }
}
