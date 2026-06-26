package site.muyin.sso.oauth;

import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthorizationCodeIssueRequest {

    String clientId;

    String redirectUri;

    String subject;

    String username;

    String email;

    String displayName;

    String avatar;

    String codeChallenge;

    @Builder.Default
    Set<String> roles = Set.of();

    @Builder.Default
    Set<String> scopes = Set.of();
}
