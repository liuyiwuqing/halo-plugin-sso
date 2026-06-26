package site.muyin.sso.client;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RegisterSsoClientRequest {

    String clientId;

    String clientSecret;

    String displayName;

    String siteUrl;

    List<String> redirectUris;

    @Builder.Default
    Boolean enabled = true;
}
