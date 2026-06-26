package site.muyin.sso.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogCleanupStatus;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;
import site.muyin.sso.service.SsoAuditLogCleanupRecordService;

@Service
@RequiredArgsConstructor
public class SsoAuditLogCleanupRecordServiceImpl implements SsoAuditLogCleanupRecordService {

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final ObjectMapper objectMapper = Unstructured.OBJECT_MAPPER;

    @Override
    public Mono<SsoAuditLogCleanupRecord> createWithRX(SsoAuditLogCleanupStatus status) {
        return create(buildRecord(status));
    }

    @Override
    public Flux<SsoAuditLogCleanupRecord> listRecentWithRX(int limit) {
        return reactiveExtensionClient.listAll(
                SsoAuditLogCleanupRecord.class,
                ListOptions.builder().fieldQuery(ExtensionUtil.notDeleting()).build(),
                Sort.by(Sort.Order.desc("finishedAt"))
            )
            .take(normalizeLimit(limit));
    }

    private SsoAuditLogCleanupRecord buildRecord(SsoAuditLogCleanupStatus status) {
        var result = status.getResult();
        var record = new SsoAuditLogCleanupRecord()
            .setTrigger(status.getTrigger())
            .setSuccess(status.isSuccess())
            .setStartedAt(status.getStartedAt())
            .setFinishedAt(status.getFinishedAt())
            .setMessage(status.getMessage());
        applyResult(record, result);

        record.setMetadata(new Metadata());
        record.getMetadata().setGenerateName(SsoAuditLogCleanupRecord.NAME_PREFIX);
        record.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(record));
        record.getMetadata().setLabels(MetadataUtil.nullSafeLabels(record));
        return record;
    }

    private static void applyResult(SsoAuditLogCleanupRecord record,
        SsoAuditLogCleanupResult result) {
        if (result == null) {
            return;
        }
        record.setDryRun(result.isDryRun())
            .setRetentionDays(result.getRetentionDays())
            .setCutoffAt(result.getCutoffAt())
            .setScanned(result.getScanned())
            .setMatched(result.getMatched())
            .setDeleted(result.getDeleted())
            .setRetained(result.getRetained());
    }

    private Mono<SsoAuditLogCleanupRecord> create(SsoAuditLogCleanupRecord record) {
        Map<?, ?> extensionMap = objectMapper.convertValue(record, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.create(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured,
                SsoAuditLogCleanupRecord.class));
    }

    private static int normalizeLimit(int limit) {
        return Math.min(50, Math.max(1, limit));
    }
}
