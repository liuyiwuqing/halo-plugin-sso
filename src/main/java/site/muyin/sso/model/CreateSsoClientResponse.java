package site.muyin.sso.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateSsoClientResponse {

    SsoClientView client;

    String clientSecret;
}
