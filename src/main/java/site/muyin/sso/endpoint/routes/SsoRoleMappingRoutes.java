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
import site.muyin.sso.scheme.SsoRoleMapping;
import site.muyin.sso.service.SsoRoleMappingService;

@Component
public class SsoRoleMappingRoutes {

    private final SsoRoleMappingService ssoRoleMappingService;

    public SsoRoleMappingRoutes(SsoRoleMappingService ssoRoleMappingService) {
        this.ssoRoleMappingService = ssoRoleMappingService;
    }

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listRoleMappings, builder -> builder
                .operationId("listSsoRoleMappings")
                .description("获取 SSO 角色映射列表")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementationArray(SsoRoleMapping.class)))
            .GET("/{centerRole}", this::getRoleMapping, builder -> builder
                .operationId("getSsoRoleMapping")
                .description("获取 SSO 角色映射详情")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("centerRole")
                    .in(ParameterIn.PATH)
                    .description("中心身份站角色")
                    .required(true)
                    .implementation(String.class))
                .response(responseBuilder().implementation(SsoRoleMapping.class)))
            .POST("/create", this::createRoleMapping, builder -> builder
                .operationId("createSsoRoleMapping")
                .description("创建 SSO 角色映射")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(SsoRoleMapping.class))))
                .response(responseBuilder().implementation(SsoRoleMapping.class)))
            .POST("/update", this::updateRoleMapping, builder -> builder
                .operationId("updateSsoRoleMapping")
                .description("更新 SSO 角色映射")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(SsoRoleMapping.class))))
                .response(responseBuilder().implementation(SsoRoleMapping.class)))
            .build();
    }

    private Mono<ServerResponse> listRoleMappings(ServerRequest request) {
        return ssoRoleMappingService.listAllWithRX()
            .collectList()
            .flatMap(mappings -> ServerResponse.ok().bodyValue(mappings));
    }

    private Mono<ServerResponse> getRoleMapping(ServerRequest request) {
        return ssoRoleMappingService.getByCenterRoleWithRX(request.pathVariable("centerRole"))
            .flatMap(mapping -> ServerResponse.ok().bodyValue(mapping));
    }

    private Mono<ServerResponse> createRoleMapping(ServerRequest request) {
        return request.bodyToMono(SsoRoleMapping.class)
            .flatMap(ssoRoleMappingService::createWithRX)
            .flatMap(mapping -> ServerResponse.ok().bodyValue(mapping));
    }

    private Mono<ServerResponse> updateRoleMapping(ServerRequest request) {
        return request.bodyToMono(SsoRoleMapping.class)
            .flatMap(ssoRoleMappingService::updateWithRX)
            .flatMap(mapping -> ServerResponse.ok().bodyValue(mapping));
    }
}
