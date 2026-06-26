package site.muyin.sso.service;

import java.time.Instant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.audit.SsoAuditFailureSummary;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogPage;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.query.SsoAuditLogQuery;
import site.muyin.sso.scheme.SsoAuditLog;

public interface SsoAuditLogService {

    String EVENT_CLIENT_LOGIN = "client_login";
    String OUTCOME_SUCCESS = "success";
    String OUTCOME_FAILURE = "failure";

    Flux<SsoAuditLog> listAllWithRX();

    Mono<SsoAuditLogPage> listWithRX(SsoAuditLogQuery query);

    Flux<SsoAuditFailureSummary> listRecentFailuresWithRX(int limit);

    Mono<SsoAuditLogCleanupResult> cleanupExpiredWithRX(int retentionDays, boolean dryRun);

    Mono<SsoAuditLogCleanupResult> cleanupBeforeWithRX(Instant cutoffAt, int retentionDays,
        boolean dryRun);

    Mono<SsoAuditLog> recordLoginSuccess(String clientId, OAuthUserInfoResponse userInfo,
        String message);

    Mono<SsoAuditLog> recordLoginFailure(String clientId, OAuthUserInfoResponse userInfo,
        String message);
}
