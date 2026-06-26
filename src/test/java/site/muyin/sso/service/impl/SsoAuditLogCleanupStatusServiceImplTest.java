package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.service.SsoAuditLogCleanupStatusService;

class SsoAuditLogCleanupStatusServiceImplTest {

    @Test
    void recordsSuccessfulCleanupStatus() {
        var service = new SsoAuditLogCleanupStatusServiceImpl();
        var result = new SsoAuditLogCleanupResult().setDeleted(3);
        var startedAt = Instant.parse("2026-06-24T08:00:00Z");
        var finishedAt = Instant.parse("2026-06-24T08:00:01Z");

        var status = service.recordSuccess(
            SsoAuditLogCleanupStatusService.TRIGGER_AUTO,
            startedAt,
            finishedAt,
            result
        );

        assertThat(status.isSuccess()).isTrue();
        assertThat(status.getTrigger()).isEqualTo(SsoAuditLogCleanupStatusService.TRIGGER_AUTO);
        assertThat(status.getStartedAt()).isEqualTo(startedAt);
        assertThat(status.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(status.getResult()).isSameAs(result);
        assertThat(service.current()).contains(status);
    }

    @Test
    void recordsFailedCleanupStatus() {
        var service = new SsoAuditLogCleanupStatusServiceImpl();
        var startedAt = Instant.parse("2026-06-24T08:00:00Z");
        var finishedAt = Instant.parse("2026-06-24T08:00:01Z");

        var status = service.recordFailure(
            SsoAuditLogCleanupStatusService.TRIGGER_MANUAL,
            startedAt,
            finishedAt,
            new IllegalStateException("cleanup failed")
        );

        assertThat(status.isSuccess()).isFalse();
        assertThat(status.getTrigger()).isEqualTo(SsoAuditLogCleanupStatusService.TRIGGER_MANUAL);
        assertThat(status.getMessage()).isEqualTo("cleanup failed");
        assertThat(status.getResult()).isNull();
        assertThat(service.current()).contains(status);
    }
}
