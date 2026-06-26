package site.muyin.sso.clientlogin;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;

public interface CenterOAuthClient {

    Mono<OAuthTokenResponse> exchangeCode(String centerUrl, OAuthTokenRequest request);

    Mono<OAuthUserInfoResponse> userInfo(String centerUrl, String accessToken);
}
