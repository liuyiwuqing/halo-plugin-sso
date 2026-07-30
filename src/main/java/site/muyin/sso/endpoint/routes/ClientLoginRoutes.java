package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static site.muyin.sso.endpoint.SsoPublicEndpoint.PUBLIC_TAG;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.net.URI;
import java.util.Optional;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import site.muyin.sso.endpoint.RequestBaseUrlResolver;
import site.muyin.sso.endpoint.ReturnUrlPolicy;
import site.muyin.sso.service.ClientLoginService;
import site.muyin.sso.service.SsoLoginSessionService;

@Component
public class ClientLoginRoutes {

    private final ClientLoginService clientLoginService;
    private final SsoLoginSessionService loginSessionService;

    public ClientLoginRoutes(ClientLoginService clientLoginService,
        SsoLoginSessionService loginSessionService) {
        this.clientLoginService = clientLoginService;
        this.loginSessionService = loginSessionService;
    }

    public RouterFunction<ServerResponse> publicRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/login", this::login, builder -> builder
                .operationId("clientSsoLogin")
                .description("接入站发起 SSO 登录")
                .tag(PUBLIC_TAG)
                .parameter(parameterBuilder().name("return_url").in(ParameterIn.QUERY)
                    .required(false).implementation(String.class))
                .parameter(parameterBuilder().name("redirect_uri").in(ParameterIn.QUERY)
                    .required(false).implementation(String.class)))
            .GET("/callback", this::callback, builder -> builder
                .operationId("clientSsoCallback")
                .description("接入站处理 SSO 回调并建立本地登录态")
                .tag(PUBLIC_TAG)
                .parameter(parameterBuilder().name("code").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class))
                .parameter(parameterBuilder().name("state").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class)))
            .build();
    }

    private Mono<ServerResponse> login(ServerRequest request) {
        return clientLoginService.startLogin(returnUrl(request),
                externalUrl(request), requesterKey(request))
            .flatMap(result -> ServerResponse.temporaryRedirect(URI.create(result.getRedirectUri()))
                .build());
    }

    private Mono<ServerResponse> callback(ServerRequest request) {
        return clientLoginService.handleCallback(
                request.queryParam("code").orElse(null),
                request.queryParam("state").orElse(null),
                externalUrl(request))
            .flatMap(result -> loginSessionService.login(request.exchange(),
                    result.getLocalUsername())
                .then(ServerResponse.temporaryRedirect(URI.create(result.getReturnUrl()))
                    .build()));
    }

    private static String externalUrl(ServerRequest request) {
        return RequestBaseUrlResolver.require(request);
    }

    private static String returnUrl(ServerRequest request) {
        return request.queryParam("return_url")
            .orElseGet(() -> ReturnUrlPolicy.safeLoginStartReturnUrl(
                request.exchange().getRequest()));
    }

    private static String requesterKey(ServerRequest request) {
        // Halo enables native forwarded-header handling. Use the address normalized by the server
        // instead of parsing client-controlled X-Forwarded-For values in the plugin.
        return Optional.ofNullable(request.exchange().getRequest().getRemoteAddress())
            .map(address -> address.getAddress())
            .map(address -> address.getHostAddress())
            .filter(address -> !address.isBlank())
            .orElse("unknown");
    }

}
