package site.muyin.sso.model.client;

import java.util.Set;
import lombok.Builder;
import lombok.Value;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;

@Value
@Builder
public class ClientLoginCallbackResult {

    OAuthUserInfoResponse userInfo;

    String localUsername;

    Set<String> grantedRoles;

    boolean localUserCreated;

    String returnUrl;
}
