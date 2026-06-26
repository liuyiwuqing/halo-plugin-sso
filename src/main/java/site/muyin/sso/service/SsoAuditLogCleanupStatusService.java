package site.muyin.sso.service;

import java.time.Instant;
import java.util.Optional;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;

public interface SsoAuditLogCleanupStatusService {

    String TRIGGER_MANUAL = "manual";
    String TRIGGER_AUTO = "auto";

    Optional<SsoAuditLogCleanupStatus> current();

    SsoAuditLogCleanupStatus recordSuccess(String trigger, Instant startedAt, Instant finishedAt,
        SsoAuditLogCleanupResult result);

    SsoAuditLogCleanupStatus recordFailure(String trigger, Instant startedAt, Instant finishedAt,
        Throwable error);
}
