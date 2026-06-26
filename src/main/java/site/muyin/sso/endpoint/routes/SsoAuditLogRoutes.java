package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.sso.endpoint.SsoConsoleEndpoint.CONSOLE_TAG;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import org.springdoc.core.fn.builders.schema.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.audit.SsoAuditFailureSummary;
import site.muyin.sso.model.audit.SsoAuditLogCleanupRequest;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.model.audit.SsoAuditLogPage;
import site.muyin.sso.query.SsoAuditLogQuery;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;
import site.muyin.sso.service.SsoAuditLogCleanupRecordService;
import site.muyin.sso.service.SsoAuditLogCleanupStatusService;
import site.muyin.sso.service.SsoAuditLogService;

@Component
public class SsoAuditLogRoutes {

    private final SsoAuditLogService ssoAuditLogService;
    private final SsoAuditLogCleanupStatusService cleanupStatusService;
    private final SsoAuditLogCleanupRecordService cleanupRecordService;

    public SsoAuditLogRoutes(SsoAuditLogService ssoAuditLogService,
        SsoAuditLogCleanupStatusService cleanupStatusService,
        SsoAuditLogCleanupRecordService cleanupRecordService) {
        this.ssoAuditLogService = ssoAuditLogService;
        this.cleanupStatusService = cleanupStatusService;
        this.cleanupRecordService = cleanupRecordService;
    }

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listAuditLogs, builder -> builder
                .operationId("listSsoAuditLogs")
                .description("获取 SSO 审计日志列表")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("outcome")
                    .in(ParameterIn.QUERY)
                    .description("处理结果：success 或 failure")
                    .implementation(String.class))
                .parameter(parameterBuilder().name("clientId")
                    .in(ParameterIn.QUERY)
                    .description("接入站 Client ID")
                    .implementation(String.class))
                .parameter(parameterBuilder().name("keyword")
                    .in(ParameterIn.QUERY)
                    .description("关键词，匹配 subject、email 或 message")
                    .implementation(String.class))
                .parameter(parameterBuilder().name("page")
                    .in(ParameterIn.QUERY)
                    .description("页码，从 1 开始")
                    .implementation(Integer.class))
                .parameter(parameterBuilder().name("size")
                    .in(ParameterIn.QUERY)
                    .description("每页数量，最大 100")
                    .implementation(Integer.class))
                .response(responseBuilder().implementation(SsoAuditLogPage.class)))
            .GET("/recent-failures", this::listRecentFailures, builder -> builder
                .operationId("listRecentSsoAuditFailures")
                .description("获取最近登录失败原因聚合")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("limit")
                    .in(ParameterIn.QUERY)
                    .description("返回数量，最大 20")
                    .implementation(Integer.class))
                .response(responseBuilder().implementationArray(SsoAuditFailureSummary.class)))
            .POST("/cleanup", this::cleanupAuditLogs, builder -> builder
                .operationId("cleanupSsoAuditLogs")
                .description("按保留天数预览或清理 SSO 审计日志")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder()
                            .implementation(SsoAuditLogCleanupRequest.class))))
                .response(responseBuilder().implementation(SsoAuditLogCleanupResult.class)))
            .GET("/cleanup-status", this::getCleanupStatus, builder -> builder
                .operationId("getSsoAuditLogCleanupStatus")
                .description("获取最近一次 SSO 审计日志清理状态")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementation(SsoAuditLogCleanupStatus.class)))
            .GET("/cleanup-records", this::listCleanupRecords, builder -> builder
                .operationId("listSsoAuditLogCleanupRecords")
                .description("获取最近的 SSO 审计日志清理记录")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("limit")
                    .in(ParameterIn.QUERY)
                    .description("返回数量，最大 50")
                    .implementation(Integer.class))
                .response(responseBuilder().implementationArray(SsoAuditLogCleanupRecord.class)))
            .build();
    }

    private Mono<ServerResponse> listAuditLogs(ServerRequest request) {
        return ssoAuditLogService.listWithRX(SsoAuditLogQuery.from(request))
            .flatMap(logs -> ServerResponse.ok().bodyValue(logs));
    }

    private Mono<ServerResponse> listRecentFailures(ServerRequest request) {
        return ssoAuditLogService.listRecentFailuresWithRX(parseLimit(request))
            .collectList()
            .flatMap(summaries -> ServerResponse.ok().bodyValue(summaries));
    }

    private Mono<ServerResponse> cleanupAuditLogs(ServerRequest request) {
        return request.bodyToMono(SsoAuditLogCleanupRequest.class)
            .defaultIfEmpty(new SsoAuditLogCleanupRequest())
            .flatMap(cleanupRequest -> cleanupAuditLogs(cleanupRequest))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<SsoAuditLogCleanupResult> cleanupAuditLogs(SsoAuditLogCleanupRequest request) {
        var startedAt = Instant.now();
        return ssoAuditLogService.cleanupExpiredWithRX(retentionDays(request), dryRun(request))
            .flatMap(result -> {
                var status = cleanupStatusService.recordSuccess(
                    SsoAuditLogCleanupStatusService.TRIGGER_MANUAL,
                    startedAt,
                    Instant.now(),
                    result
                );
                return persistStatus(status).thenReturn(result);
            })
            .onErrorResume(error -> {
                var status = cleanupStatusService.recordFailure(
                    SsoAuditLogCleanupStatusService.TRIGGER_MANUAL,
                    startedAt,
                    Instant.now(),
                    error
                );
                return persistStatus(status)
                    .then(Mono.<SsoAuditLogCleanupResult>error(error));
            });
    }

    private Mono<ServerResponse> getCleanupStatus(ServerRequest request) {
        return Mono.justOrEmpty(cleanupStatusService.current())
            .flatMap(status -> ServerResponse.ok().bodyValue(status))
            .switchIfEmpty(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> listCleanupRecords(ServerRequest request) {
        return cleanupRecordService.listRecentWithRX(parseLimit(request))
            .collectList()
            .flatMap(records -> ServerResponse.ok().bodyValue(records));
    }

    private Mono<Void> persistStatus(SsoAuditLogCleanupStatus status) {
        if (status == null) {
            return Mono.empty();
        }
        return cleanupRecordService.createWithRX(status)
            .onErrorResume(error -> Mono.empty())
            .then();
    }

    private static int retentionDays(SsoAuditLogCleanupRequest request) {
        return request.getRetentionDays() == null ? 90 : request.getRetentionDays();
    }

    private static boolean dryRun(SsoAuditLogCleanupRequest request) {
        return request.getDryRun() == null || request.getDryRun();
    }

    private static int parseLimit(ServerRequest request) {
        var rawLimit = request.queryParam("limit").orElse("5");
        try {
            return Integer.parseInt(rawLimit);
        } catch (NumberFormatException ignored) {
            return 5;
        }
    }
}
