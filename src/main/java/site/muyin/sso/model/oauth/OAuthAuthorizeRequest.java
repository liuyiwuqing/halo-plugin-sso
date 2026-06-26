package site.muyin.sso.model.oauth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OAuthAuthorizeRequest {

    String responseType;

    String clientId;

    String redirectUri;

    String scope;

    String state;

    String codeChallenge;

    String codeChallengeMethod;

    public Set<String> scopes() {
        if (scope == null || scope.isBlank()) {
            return Set.of("openid", "profile", "email");
        }
        return Arrays.stream(scope.trim().split("\\s+"))
            .filter(item -> !item.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
