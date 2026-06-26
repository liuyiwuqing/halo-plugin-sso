package site.muyin.sso.oauth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthorizationCodeConsumeRequest {

    String code;

    String clientId;

    String redirectUri;

    String codeVerifier;
}
