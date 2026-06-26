package site.muyin.sso.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;
import site.muyin.sso.service.SsoAuditLogCleanupRecordService;
import site.muyin.sso.service.SsoAuditLogCleanupStatusService;
import site.muyin.sso.service.SsoAuditLogService;
import site.muyin.sso.setting.SsoAuditSetting;

class SsoAuditLogCleanupSchedulerTest {

    @Test
    void skipsCleanupWhenAutoCleanupDisabled() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var auditLogService = mock(SsoAuditLogService.class);
        var statusService = mock(SsoAuditLogCleanupStatusService.class);
        var recordService = mock(SsoAuditLogCleanupRecordService.class);
        var scheduler = new SsoAuditLogCleanupScheduler(settingFetcher, auditLogService,
            statusService, recordService);
        var setting = new SsoAuditSetting();
        setting.setAutoCleanupEnabled(false);

        when(settingFetcher.fetch("audit", SsoAuditSetting.class))
            .thenReturn(Mono.just(setting));

        var result = scheduler.runOnceWithRX().blockOptional();

        assertThat(result).isEmpty();
        verify(auditLogService, never()).cleanupExpiredWithRX(90, false);
        verify(recordService, never()).createWithRX(any());
    }

    @Test
    void cleansExpiredAuditLogsWhenAutoCleanupEnabled() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var auditLogService = mock(SsoAuditLogService.class);
        var statusService = mock(SsoAuditLogCleanupStatusService.class);
        var recordService = mock(SsoAuditLogCleanupRecordService.class);
        var scheduler = new SsoAuditLogCleanupScheduler(settingFetcher, auditLogService,
            statusService, recordService);
        var setting = new SsoAuditSetting();
        setting.setAutoCleanupEnabled(true);
        setting.setRetentionDays(30);
        var cleanupResult = new SsoAuditLogCleanupResult()
            .setRetentionDays(30)
            .setDeleted(3);
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(SsoAuditLogCleanupStatusService.TRIGGER_AUTO)
            .setSuccess(true)
            .setResult(cleanupResult);
        var record = new SsoAuditLogCleanupRecord().setDeleted(3L);

        when(settingFetcher.fetch("audit", SsoAuditSetting.class))
            .thenReturn(Mono.just(setting));
        when(auditLogService.cleanupExpiredWithRX(30, false))
            .thenReturn(Mono.just(cleanupResult));
        when(statusService.recordSuccess(
            eq(SsoAuditLogCleanupStatusService.TRIGGER_AUTO),
            any(),
            any(),
            eq(cleanupResult)
        )).thenReturn(status);
        when(recordService.createWithRX(status)).thenReturn(Mono.just(record));

        Optional<SsoAuditLogCleanupResult> result = scheduler.runOnceWithRX().blockOptional();

        assertThat(result).contains(cleanupResult);
        verify(auditLogService).cleanupExpiredWithRX(30, false);
        verify(statusService).recordSuccess(
            eq(SsoAuditLogCleanupStatusService.TRIGGER_AUTO),
            any(),
            any(),
            eq(cleanupResult)
        );
        verify(recordService).createWithRX(status);
    }

    @Test
    void clampsRetentionDaysForScheduledCleanup() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var auditLogService = mock(SsoAuditLogService.class);
        var statusService = mock(SsoAuditLogCleanupStatusService.class);
        var recordService = mock(SsoAuditLogCleanupRecordService.class);
        var scheduler = new SsoAuditLogCleanupScheduler(settingFetcher, auditLogService,
            statusService, recordService);
        var setting = new SsoAuditSetting();
        setting.setAutoCleanupEnabled(true);
        setting.setRetentionDays(0);
        var cleanupResult = new SsoAuditLogCleanupResult()
            .setRetentionDays(1);
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(SsoAuditLogCleanupStatusService.TRIGGER_AUTO)
            .setSuccess(true)
            .setResult(cleanupResult);

        when(settingFetcher.fetch("audit", SsoAuditSetting.class))
            .thenReturn(Mono.just(setting));
        when(auditLogService.cleanupExpiredWithRX(1, false))
            .thenReturn(Mono.just(cleanupResult));
        when(statusService.recordSuccess(
            eq(SsoAuditLogCleanupStatusService.TRIGGER_AUTO),
            any(),
            any(),
            eq(cleanupResult)
        )).thenReturn(status);
        when(recordService.createWithRX(status))
            .thenReturn(Mono.just(new SsoAuditLogCleanupRecord()));

        scheduler.runOnceWithRX().block();

        verify(auditLogService).cleanupExpiredWithRX(1, false);
    }

    @Test
    void cleanupStillSucceedsWhenPersistingCleanupRecordFails() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var auditLogService = mock(SsoAuditLogService.class);
        var statusService = mock(SsoAuditLogCleanupStatusService.class);
        var recordService = mock(SsoAuditLogCleanupRecordService.class);
        var scheduler = new SsoAuditLogCleanupScheduler(settingFetcher, auditLogService,
            statusService, recordService);
        var setting = new SsoAuditSetting();
        setting.setAutoCleanupEnabled(true);
        setting.setRetentionDays(30);
        var cleanupResult = new SsoAuditLogCleanupResult()
            .setRetentionDays(30)
            .setDeleted(2);
        var status = new SsoAuditLogCleanupStatus()
            .setTrigger(SsoAuditLogCleanupStatusService.TRIGGER_AUTO)
            .setSuccess(true)
            .setResult(cleanupResult);

        when(settingFetcher.fetch("audit", SsoAuditSetting.class))
            .thenReturn(Mono.just(setting));
        when(auditLogService.cleanupExpiredWithRX(30, false))
            .thenReturn(Mono.just(cleanupResult));
        when(statusService.recordSuccess(
            eq(SsoAuditLogCleanupStatusService.TRIGGER_AUTO),
            any(),
            any(),
            eq(cleanupResult)
        )).thenReturn(status);
        when(recordService.createWithRX(status)).thenReturn(Mono.error(new IllegalStateException(
            "persist failed"
        )));

        var result = scheduler.runOnceWithRX().blockOptional();

        assertThat(result).contains(cleanupResult);
        verify(recordService).createWithRX(status);
    }
}
