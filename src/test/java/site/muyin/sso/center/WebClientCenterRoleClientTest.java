package site.muyin.sso.center;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.oauth.OAuthEndpointPaths;

class WebClientCenterRoleClientTest {

    @Test
    void requestsRolesEndpointAndDecodesCompleteResponse() {
        var client = new WebClientCenterRoleClient(WebClient.builder()
            .exchangeFunction(request -> {
                assertThat(request.url().toString())
                    .isEqualTo("https://auth.muyin.site" + OAuthEndpointPaths.ROLES_LIST);
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        [
                          {
                            "name": "author",
                            "displayName": "作者",
                            "module": "content",
                            "hidden": false
                          }
                        ]
                        """)
                    .build());
            })
            .build());

        var roles = client.listRoles("https://auth.muyin.site/").collectList().block();

        assertThat(roles).hasSize(1);
        assertThat(roles.getFirst().name()).isEqualTo("author");
        assertThat(roles.getFirst().displayName()).isEqualTo("作者");
        assertThat(roles.getFirst().module()).isEqualTo("content");
        assertThat(roles.getFirst().hidden()).isFalse();
    }

    @Test
    void timesOutWhenCenterRolesEndpointDoesNotRespond() {
        var client = new WebClientCenterRoleClient(WebClient.builder()
            .exchangeFunction(request -> Mono.never())
            .build(), Duration.ofMillis(25));

        assertThatThrownBy(() -> client.listRoles("https://auth.muyin.site/")
                .collectList()
                .block())
            .hasRootCauseInstanceOf(TimeoutException.class);
    }
}
