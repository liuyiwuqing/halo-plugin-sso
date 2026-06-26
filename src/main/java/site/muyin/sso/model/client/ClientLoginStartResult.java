package site.muyin.sso.model.client;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientLoginStartResult {

    String redirectUri;
}
