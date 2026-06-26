package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static site.muyin.sso.endpoint.SsoConsoleEndpoint.CONSOLE_TAG;

import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.service.SsoUserBindingService;

@Component
public class SsoUserBindingRoutes {

    private final SsoUserBindingService ssoUserBindingService;

    public SsoUserBindingRoutes(SsoUserBindingService ssoUserBindingService) {
        this.ssoUserBindingService = ssoUserBindingService;
    }

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listUserBindings, builder -> builder
                .operationId("listSsoUserBindings")
                .description("获取 SSO 用户绑定列表")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementationArray(SsoUserBinding.class)))
            .build();
    }

    private Mono<ServerResponse> listUserBindings(ServerRequest request) {
        return ssoUserBindingService.listAllWithRX()
            .collectList()
            .flatMap(bindings -> ServerResponse.ok().bodyValue(bindings));
    }
}
