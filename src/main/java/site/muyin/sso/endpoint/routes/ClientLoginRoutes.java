package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static site.muyin.sso.endpoint.SsoPublicEndpoint.PUBLIC_TAG;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.net.URI;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
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
                externalUrl(request))
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
        var forwarded = request.headers().firstHeader("Forwarded");
        var forwardedProto = forwardedValue(forwarded, "proto");
        var forwardedHost = forwardedValue(forwarded, "host");
        if (hasText(forwardedProto) && hasText(forwardedHost)) {
            return forwardedProto + "://" + forwardedHost;
        }

        var xForwardedProto = firstForwardedHeader(request, "X-Forwarded-Proto");
        var xForwardedHost = firstForwardedHeader(request, "X-Forwarded-Host");
        if (hasText(xForwardedProto) && hasText(xForwardedHost)) {
            return xForwardedProto + "://" + xForwardedHost;
        }

        var uri = request.uri();
        if (!hasText(uri.getScheme()) || !hasText(uri.getRawAuthority())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法识别当前站点访问地址");
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static String returnUrl(ServerRequest request) {
        return request.queryParam("return_url")
            .orElseGet(() -> ReturnUrlPolicy.safeLoginStartReturnUrl(
                request.exchange().getRequest()));
    }

    private static String firstForwardedHeader(ServerRequest request, String name) {
        var value = request.headers().firstHeader(name);
        if (!hasText(value)) {
            return null;
        }
        var commaIndex = value.indexOf(',');
        return commaIndex < 0 ? value.trim() : value.substring(0, commaIndex).trim();
    }

    private static String forwardedValue(String forwarded, String name) {
        if (!hasText(forwarded)) {
            return null;
        }
        var first = forwarded.split(",", 2)[0];
        for (var part : first.split(";")) {
            var trimmed = part.trim();
            var separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            var key = trimmed.substring(0, separator).trim();
            if (!name.equalsIgnoreCase(key)) {
                continue;
            }
            return unquote(trimmed.substring(separator + 1).trim());
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
