package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;
import site.muyin.sso.service.SsoAuditLogCleanupStatusService;

class SsoAuditLogCleanupRecordServiceImplTest {

    @Test
    void createsRecordFromSuccessfulCleanupStatus() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogCleanupRecordServiceImpl(client);
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(SsoAuditLogCleanupStatusService.TRIGGER_AUTO)
            .setSuccess(true)
            .setStartedAt(Instant.parse("2026-06-24T08:00:00Z"))
            .setFinishedAt(Instant.parse("2026-06-24T08:00:01Z"))
            .setResult(new SsoAuditLogCleanupResult()
                .setDryRun(false)
                .setRetentionDays(90)
                .setCutoffAt(Instant.parse("2026-03-26T08:00:00Z"))
                .setScanned(100)
                .setMatched(8)
                .setDeleted(8)
                .setRetained(92));

        when(client.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var record = service.createWithRX(status).block();

        assertThat(record.getMetadata().getGenerateName())
            .isEqualTo(SsoAuditLogCleanupRecord.NAME_PREFIX);
        assertThat(record.getTrigger()).isEqualTo(SsoAuditLogCleanupStatusService.TRIGGER_AUTO);
        assertThat(record.getSuccess()).isTrue();
        assertThat(record.getDryRun()).isFalse();
        assertThat(record.getRetentionDays()).isEqualTo(90);
        assertThat(record.getScanned()).isEqualTo(100L);
        assertThat(record.getMatched()).isEqualTo(8L);
        assertThat(record.getDeleted()).isEqualTo(8L);
        assertThat(record.getRetained()).isEqualTo(92L);
    }

    @Test
    void createsRecordFromFailedCleanupStatus() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogCleanupRecordServiceImpl(client);
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(SsoAuditLogCleanupStatusService.TRIGGER_MANUAL)
            .setSuccess(false)
            .setStartedAt(Instant.parse("2026-06-24T08:00:00Z"))
            .setFinishedAt(Instant.parse("2026-06-24T08:00:01Z"))
            .setMessage("cleanup failed");

        when(client.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var record = service.createWithRX(status).block();

        assertThat(record.getTrigger()).isEqualTo(SsoAuditLogCleanupStatusService.TRIGGER_MANUAL);
        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getMessage()).isEqualTo("cleanup failed");
        assertThat(record.getDeleted()).isNull();
    }

    @Test
    void listsRecentCleanupRecords() {
        var client = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogCleanupRecordServiceImpl(client);
        var newest = new SsoAuditLogCleanupRecord().setDeleted(3L);
        var older = new SsoAuditLogCleanupRecord().setDeleted(1L);

        when(client.listAll(eq(SsoAuditLogCleanupRecord.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.just(newest, older));

        var records = service.listRecentWithRX(1).collectList().block();

        assertThat(records).containsExactly(newest);
    }
}
