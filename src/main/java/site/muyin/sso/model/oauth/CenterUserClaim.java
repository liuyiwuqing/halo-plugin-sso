package site.muyin.sso.model.oauth;

import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CenterUserClaim {

    String subject;

    String username;

    String email;

    String displayName;

    String avatar;

    @Builder.Default
    Set<String> roles = Set.of();
}
