package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.sso.endpoint.SsoConsoleEndpoint.CONSOLE_TAG;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.fn.builders.schema.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.CreateSsoClientRequest;
import site.muyin.sso.model.CreateSsoClientResponse;
import site.muyin.sso.model.SsoClientView;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.service.SsoClientService;

@Component
public class SsoClientRoutes {

    private final SsoClientService ssoClientService;

    public SsoClientRoutes(SsoClientService ssoClientService) {
        this.ssoClientService = ssoClientService;
    }

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listClients, builder -> builder
                .operationId("listSsoClients")
                .description("获取 SSO 接入站列表")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementationArray(SsoClientView.class)))
            .GET("/{clientId}", this::getClient, builder -> builder
                .operationId("getSsoClient")
                .description("获取 SSO 接入站详情")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("clientId")
                    .in(ParameterIn.PATH)
                    .description("Client ID")
                    .required(true)
                    .implementation(String.class))
                .response(responseBuilder().implementation(SsoClientView.class)))
            .POST("/create", this::createClient, builder -> builder
                .operationId("createSsoClient")
                .description("创建 SSO 接入站")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder()
                            .implementation(CreateSsoClientRequest.class))))
                .response(responseBuilder().implementation(CreateSsoClientResponse.class)))
            .POST("/update", this::updateClient, builder -> builder
                .operationId("updateSsoClient")
                .description("更新 SSO 接入站")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(SsoClient.class))))
                .response(responseBuilder().implementation(SsoClientView.class)))
            .DELETE("/{clientId}", this::deleteClient, builder -> builder
                .operationId("deleteSsoClient")
                .description("删除 SSO 接入站")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("clientId")
                    .in(ParameterIn.PATH)
                    .description("Client ID")
                    .required(true)
                    .implementation(String.class)))
            .build();
    }

    private Mono<ServerResponse> listClients(ServerRequest request) {
        return ssoClientService.listAllWithRX()
            .map(SsoClientView::from)
            .collectList()
            .flatMap(clients -> ServerResponse.ok().bodyValue(clients));
    }

    private Mono<ServerResponse> getClient(ServerRequest request) {
        return ssoClientService.getByClientIdWithRX(request.pathVariable("clientId"))
            .map(SsoClientView::from)
            .flatMap(client -> ServerResponse.ok().bodyValue(client));
    }

    private Mono<ServerResponse> createClient(ServerRequest request) {
        return request.bodyToMono(CreateSsoClientRequest.class)
            .flatMap(ssoClientService::createWithRX)
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> updateClient(ServerRequest request) {
        return request.bodyToMono(SsoClient.class)
            .flatMap(ssoClientService::updateWithRX)
            .map(SsoClientView::from)
            .flatMap(client -> ServerResponse.ok().bodyValue(client));
    }

    private Mono<ServerResponse> deleteClient(ServerRequest request) {
        return ssoClientService.deleteWithRX(request.pathVariable("clientId"))
            .then(ServerResponse.noContent().build());
    }
}
