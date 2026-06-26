package site.muyin.sso.model.oauth;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
public class OAuthUserInfoResponse {

    String sub;

    @JsonProperty("preferred_username")
    String preferredUsername;

    String email;

    String name;

    String picture;

    Set<String> roles;

    @Builder
    @JsonCreator
    public OAuthUserInfoResponse(@JsonProperty("sub") String sub,
        @JsonProperty("preferred_username") String preferredUsername,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("picture") String picture,
        @JsonProperty("roles") Set<String> roles) {
        this.sub = sub;
        this.preferredUsername = preferredUsername;
        this.email = email;
        this.name = name;
        this.picture = picture;
        this.roles = roles;
    }
}
