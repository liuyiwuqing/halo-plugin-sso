package site.muyin.sso.model.oauth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
public class OAuthTokenResponse {

    @JsonProperty("access_token")
    String accessToken;

    @JsonProperty("id_token")
    String idToken;

    @JsonProperty("token_type")
    String tokenType;

    @JsonProperty("expires_in")
    long expiresIn;

    @Builder
    @JsonCreator
    public OAuthTokenResponse(@JsonProperty("access_token") String accessToken,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {
        this.accessToken = accessToken;
        this.idToken = idToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }
}
