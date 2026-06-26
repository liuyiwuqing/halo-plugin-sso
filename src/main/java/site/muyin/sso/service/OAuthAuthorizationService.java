package site.muyin.sso.service;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthAuthorizeRequest;
import site.muyin.sso.model.oauth.OAuthAuthorizeResult;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;

public interface OAuthAuthorizationService {

    Mono<OAuthAuthorizeResult> authorize(OAuthAuthorizeRequest request);

    Mono<OAuthTokenResponse> token(OAuthTokenRequest request);

    Mono<OAuthUserInfoResponse> userInfo(String accessToken);
}
