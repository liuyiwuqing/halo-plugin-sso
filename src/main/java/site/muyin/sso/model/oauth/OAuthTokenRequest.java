package site.muyin.sso.model.oauth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OAuthTokenRequest {

    String grantType;

    String code;

    String redirectUri;

    String clientId;

    String clientSecret;

    String codeVerifier;
}
