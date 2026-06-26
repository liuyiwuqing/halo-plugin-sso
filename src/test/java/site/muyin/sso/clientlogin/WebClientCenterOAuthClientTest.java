package site.muyin.sso.clientlogin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import tools.jackson.databind.json.JsonMapper;

class WebClientCenterOAuthClientTest {

    @Test
    void createsWithoutWebClientBuilderBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(WebClientCenterOAuthClient.class);

            context.refresh();

            assertThat(context.getBean(CenterOAuthClient.class))
                .isInstanceOf(WebClientCenterOAuthClient.class);
        }
    }

    @Test
    void exchangesCodeAndDecodesTokenResponse() {
        var client = new WebClientCenterOAuthClient(WebClient.builder()
            .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("""
                    {
                      "access_token": "access-001",
                      "id_token": "id-001",
                      "token_type": "Bearer",
                      "expires_in": 3600
                    }
                    """)
                .build()))
            .build());

        var response = client.exchangeCode("https://demo.muyin.site/",
            OAuthTokenRequest.builder()
                .grantType("authorization_code")
                .code("code-001")
                .redirectUri("https://b.example.com/callback")
                .clientId("site-b")
                .clientSecret("client-secret")
                .codeVerifier("verifier-001")
                .build())
            .block();

        assertThat(response.getAccessToken()).isEqualTo("access-001");
        assertThat(response.getIdToken()).isEqualTo("id-001");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600);
    }

    @Test
    void jackson3DecodesTokenResponseJson() throws Exception {
        var response = JsonMapper.builder().build().readValue("""
            {
              "access_token": "access-001",
              "id_token": "id-001",
              "token_type": "Bearer",
              "expires_in": 3600
            }
            """, OAuthTokenResponse.class);

        assertThat(response.getAccessToken()).isEqualTo("access-001");
        assertThat(response.getIdToken()).isEqualTo("id-001");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600);
    }

    @Test
    void requestsUserInfoAndDecodesResponse() {
        var client = new WebClientCenterOAuthClient(WebClient.builder()
            .exchangeFunction(request -> {
                assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer access-001");
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        {
                          "sub": "user-001",
                          "preferred_username": "lywq",
                          "email": "lywq@example.com",
                          "name": "Lywq",
                          "picture": "https://example.com/avatar.png",
                          "roles": ["author", "subscriber"]
                        }
                        """)
                    .build());
            })
            .build());

        var response = client.userInfo("https://demo.muyin.site/", "access-001").block();

        assertThat(response.getSub()).isEqualTo("user-001");
        assertThat(response.getPreferredUsername()).isEqualTo("lywq");
        assertThat(response.getEmail()).isEqualTo("lywq@example.com");
        assertThat(response.getName()).isEqualTo("Lywq");
        assertThat(response.getPicture()).isEqualTo("https://example.com/avatar.png");
        assertThat(response.getRoles()).containsExactlyInAnyOrder("author", "subscriber");
    }
}
