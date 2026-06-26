package site.muyin.sso.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;

public interface SsoAuditLogCleanupRecordService {

    Mono<SsoAuditLogCleanupRecord> createWithRX(SsoAuditLogCleanupStatus status);

    Flux<SsoAuditLogCleanupRecord> listRecentWithRX(int limit);
}
