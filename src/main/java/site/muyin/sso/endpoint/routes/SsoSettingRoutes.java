package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static site.muyin.sso.endpoint.SsoConsoleEndpoint.CONSOLE_TAG;

import java.util.concurrent.TimeoutException;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.center.CenterRoleClient;
import site.muyin.sso.model.SsoPublicRole;
import site.muyin.sso.setting.SsoGeneralSetting;

@Component
public class SsoSettingRoutes {

    private final ReactiveSettingFetcher settingFetcher;
    private final CenterRoleClient centerRoleClient;

    public SsoSettingRoutes(ReactiveSettingFetcher settingFetcher,
        CenterRoleClient centerRoleClient) {
        this.settingFetcher = settingFetcher;
        this.centerRoleClient = centerRoleClient;
    }

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/general", this::getGeneralSetting, builder -> builder
                .operationId("getSsoGeneralRuntimeSetting")
                .description("获取 SSO 控制台运行设置")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementation(SsoGeneralRuntimeSetting.class)))
            .GET("/center-roles", this::listCenterRoles, builder -> builder
                .operationId("listSsoCenterRoles")
                .description("通过服务端代理获取身份中心角色列表")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementationArray(SsoPublicRole.class)))
            .build();
    }

    private Mono<ServerResponse> getGeneralSetting(ServerRequest request) {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .map(SsoGeneralRuntimeSetting::from)
            .flatMap(setting -> ServerResponse.ok().bodyValue(setting));
    }

    private Mono<ServerResponse> listCenterRoles(ServerRequest request) {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .map(SsoSettingRoutes::requireClientMode)
            .flatMapMany(setting -> centerRoleClient.listRoles(setting.getCenterUrl()))
            .onErrorMap(WebClientResponseException.class,
                error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "身份中心角色服务请求失败: HTTP " + error.getStatusCode().value(), error))
            .onErrorMap(WebClientRequestException.class,
                error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法连接身份中心角色服务", error))
            .onErrorMap(TimeoutException.class,
                error -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "身份中心角色列表请求超时", error))
            .collectList()
            .flatMap(roles -> ServerResponse.ok().bodyValue(roles));
    }

    private static SsoGeneralSetting requireClientMode(SsoGeneralSetting setting) {
        if (!"client".equals(setting.getMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "当前插件不是接入站模式");
        }
        if (!StringUtils.hasText(setting.getCenterUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "请先配置身份中心地址");
        }
        return setting;
    }

    public record SsoGeneralRuntimeSetting(String mode, String centerUrl) {

        static SsoGeneralRuntimeSetting from(SsoGeneralSetting setting) {
            return new SsoGeneralRuntimeSetting(setting.getMode(), setting.getCenterUrl());
        }
    }
}
