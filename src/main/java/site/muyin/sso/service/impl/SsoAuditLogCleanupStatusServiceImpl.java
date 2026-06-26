package site.muyin.sso.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.service.SsoAuditLogCleanupStatusService;

@Service
public class SsoAuditLogCleanupStatusServiceImpl implements SsoAuditLogCleanupStatusService {

    private final AtomicReference<SsoAuditLogCleanupStatus> current = new AtomicReference<>();

    @Override
    public Optional<SsoAuditLogCleanupStatus> current() {
        return Optional.ofNullable(current.get());
    }

    @Override
    public SsoAuditLogCleanupStatus recordSuccess(String trigger, Instant startedAt,
        Instant finishedAt, SsoAuditLogCleanupResult result) {
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(trigger)
            .setSuccess(true)
            .setStartedAt(startedAt)
            .setFinishedAt(finishedAt)
            .setResult(result);
        current.set(status);
        return status;
    }

    @Override
    public SsoAuditLogCleanupStatus recordFailure(String trigger, Instant startedAt,
        Instant finishedAt, Throwable error) {
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(trigger)
            .setSuccess(false)
            .setStartedAt(startedAt)
            .setFinishedAt(finishedAt)
            .setMessage(errorMessage(error));
        current.set(status);
        return status;
    }

    private static String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "未知错误";
        }
        return error.getMessage();
    }
}
