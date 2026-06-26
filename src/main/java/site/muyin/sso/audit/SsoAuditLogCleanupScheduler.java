package site.muyin.sso.audit;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.service.SsoAuditLogCleanupRecordService;
import site.muyin.sso.service.SsoAuditLogCleanupStatusService;
import site.muyin.sso.service.SsoAuditLogService;
import site.muyin.sso.setting.SsoAuditSetting;

@Slf4j
@Component
public class SsoAuditLogCleanupScheduler {

    private static final long INITIAL_DELAY_MILLIS = 5 * 60 * 1000L;
    private static final long FIXED_DELAY_MILLIS = 6 * 60 * 60 * 1000L;

    private final ReactiveSettingFetcher settingFetcher;
    private final SsoAuditLogService auditLogService;
    private final SsoAuditLogCleanupStatusService cleanupStatusService;
    private final SsoAuditLogCleanupRecordService cleanupRecordService;

    public SsoAuditLogCleanupScheduler(ReactiveSettingFetcher settingFetcher,
        SsoAuditLogService auditLogService,
        SsoAuditLogCleanupStatusService cleanupStatusService,
        SsoAuditLogCleanupRecordService cleanupRecordService) {
        this.settingFetcher = settingFetcher;
        this.auditLogService = auditLogService;
        this.cleanupStatusService = cleanupStatusService;
        this.cleanupRecordService = cleanupRecordService;
    }

    @Scheduled(initialDelay = INITIAL_DELAY_MILLIS, fixedDelay = FIXED_DELAY_MILLIS)
    public void runScheduledCleanup() {
        runOnceWithRX()
            .doOnNext(result -> log.info(
                "SSO audit log auto cleanup finished, retentionDays={}, matched={}, deleted={}",
                result.getRetentionDays(),
                result.getMatched(),
                result.getDeleted()
            ))
            .doOnError(error -> log.warn("SSO audit log auto cleanup failed.", error))
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    public Mono<SsoAuditLogCleanupResult> runOnceWithRX() {
        var startedAt = Instant.now();
        return settingFetcher.fetch("audit", SsoAuditSetting.class)
            .defaultIfEmpty(new SsoAuditSetting())
            .filter(setting -> Boolean.TRUE.equals(setting.getAutoCleanupEnabled()))
            .flatMap(setting -> auditLogService.cleanupExpiredWithRX(
                normalizeRetentionDays(setting.getRetentionDays()),
                false
            ))
            .flatMap(result -> {
                var status = cleanupStatusService.recordSuccess(
                    SsoAuditLogCleanupStatusService.TRIGGER_AUTO,
                    startedAt,
                    Instant.now(),
                    result
                );
                return persistStatus(status).thenReturn(result);
            })
            .onErrorResume(error -> {
                var status = cleanupStatusService.recordFailure(
                    SsoAuditLogCleanupStatusService.TRIGGER_AUTO,
                    startedAt,
                    Instant.now(),
                    error
                );
                return persistStatus(status).then(Mono.error(error));
            });
    }

    private Mono<Void> persistStatus(SsoAuditLogCleanupStatus status) {
        if (status == null) {
            return Mono.empty();
        }
        return cleanupRecordService.createWithRX(status)
            .doOnError(error -> log.warn("Failed to persist SSO audit cleanup record.", error))
            .onErrorResume(error -> Mono.empty())
            .then();
    }

    private static int normalizeRetentionDays(Integer retentionDays) {
        if (retentionDays == null) {
            return 90;
        }
        return Math.min(3650, Math.max(1, retentionDays));
    }
}
