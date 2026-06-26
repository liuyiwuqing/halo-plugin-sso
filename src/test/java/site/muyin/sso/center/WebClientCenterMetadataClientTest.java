package site.muyin.sso.center;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.oauth.OAuthEndpointPaths;

class WebClientCenterMetadataClientTest {

    @Test
    void requestsMetadataEndpointAndDecodesResponse() {
        var client = new WebClientCenterMetadataClient(WebClient.builder()
            .exchangeFunction(request -> {
                assertThat(request.url().toString())
                    .isEqualTo("https://auth.muyin.site" + OAuthEndpointPaths.METADATA);
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        {
                          "displayName": "木因身份中心",
                          "description": "使用木因账号登录",
                          "logo": "https://auth.muyin.site/upload/auth-logo.png",
                          "website": "https://auth.muyin.site"
                        }
                        """)
                    .build());
            })
            .build());

        var metadata = client.getMetadata("https://auth.muyin.site/").block();

        assertThat(metadata).isNotNull();
        assertThat(metadata.displayName()).isEqualTo("木因身份中心");
        assertThat(metadata.description()).isEqualTo("使用木因账号登录");
        assertThat(metadata.logo()).isEqualTo("https://auth.muyin.site/upload/auth-logo.png");
        assertThat(metadata.website()).isEqualTo("https://auth.muyin.site");
    }
}
