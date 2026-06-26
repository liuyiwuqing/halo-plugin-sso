package site.muyin.sso.model.oauth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OAuthAuthorizeResult {

    String redirectUri;
}
