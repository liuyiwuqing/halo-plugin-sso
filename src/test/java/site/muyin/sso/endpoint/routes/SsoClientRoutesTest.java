package site.muyin.sso.endpoint.routes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.service.SsoClientService;

class SsoClientRoutesTest {

    @Test
    void deletesClientByClientId() {
        var service = mock(SsoClientService.class);
        when(service.deleteWithRX("site-b"))
            .thenReturn(Mono.just(new SsoClient().setClientId("site-b")));

        webClient(service)
            .delete()
            .uri("/site-b")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        verify(service).deleteWithRX("site-b");
    }

    private static WebTestClient webClient(SsoClientService service) {
        return WebTestClient.bindToRouterFunction(
            new SsoClientRoutes(service).consoleRoutes()
        ).build();
    }
}
