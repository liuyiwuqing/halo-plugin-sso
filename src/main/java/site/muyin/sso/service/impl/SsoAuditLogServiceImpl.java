package site.muyin.sso.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import site.muyin.sso.model.audit.SsoAuditFailureSummary;
import site.muyin.sso.model.audit.SsoAuditLogCleanupResult;
import site.muyin.sso.model.audit.SsoAuditLogPage;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.query.SsoAuditLogQuery;
import site.muyin.sso.scheme.SsoAuditLog;
import site.muyin.sso.service.SsoAuditLogService;

@Service
@RequiredArgsConstructor
public class SsoAuditLogServiceImpl implements SsoAuditLogService {

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final ObjectMapper objectMapper = Unstructured.OBJECT_MAPPER;

    @Override
    public Flux<SsoAuditLog> listAllWithRX() {
        return reactiveExtensionClient.listAll(
            SsoAuditLog.class,
            ListOptions.builder().fieldQuery(ExtensionUtil.notDeleting()).build(),
            Sort.by(Sort.Order.desc("createdAt"))
        );
    }

    @Override
    public Mono<SsoAuditLogPage> listWithRX(SsoAuditLogQuery query) {
        var normalizedQuery = normalizeQuery(query);
        return listAllWithRX()
            .filter(log -> matches(log, normalizedQuery))
            .collectList()
            .map(logs -> toPage(logs, normalizedQuery));
    }

    @Override
    public Flux<SsoAuditFailureSummary> listRecentFailuresWithRX(int limit) {
        var normalizedLimit = Math.min(20, Math.max(1, limit));
        return listAllWithRX()
            .filter(log -> OUTCOME_FAILURE.equals(log.getOutcome()))
            .collectList()
            .flatMapMany(logs -> Flux.fromIterable(toFailureSummaries(logs, normalizedLimit)));
    }

    @Override
    public Mono<SsoAuditLogCleanupResult> cleanupExpiredWithRX(int retentionDays, boolean dryRun) {
        var normalizedRetentionDays = normalizeRetentionDays(retentionDays);
        var cutoffAt = Instant.now().minus(normalizedRetentionDays, ChronoUnit.DAYS);
        return cleanupBeforeWithRX(cutoffAt, normalizedRetentionDays, dryRun);
    }

    @Override
    public Mono<SsoAuditLogCleanupResult> cleanupBeforeWithRX(Instant cutoffAt, int retentionDays,
        boolean dryRun) {
        var normalizedRetentionDays = normalizeRetentionDays(retentionDays);
        var normalizedCutoffAt = cutoffAt == null
            ? Instant.now().minus(normalizedRetentionDays, ChronoUnit.DAYS)
            : cutoffAt;
        return listAllWithRX()
            .collectList()
            .flatMap(logs -> cleanupExpiredLogs(logs, normalizedCutoffAt, normalizedRetentionDays,
                dryRun));
    }

    @Override
    public Mono<SsoAuditLog> recordLoginSuccess(String clientId, OAuthUserInfoResponse userInfo,
        String message) {
        return create(buildLoginLog(clientId, userInfo, OUTCOME_SUCCESS, messageOrDefault(message,
            "接入站登录成功")));
    }

    @Override
    public Mono<SsoAuditLog> recordLoginFailure(String clientId, OAuthUserInfoResponse userInfo,
        String message) {
        return create(buildLoginLog(clientId, userInfo, OUTCOME_FAILURE, messageOrDefault(message,
            "接入站登录失败")));
    }

    private SsoAuditLog buildLoginLog(String clientId, OAuthUserInfoResponse userInfo,
        String outcome, String message) {
        var log = new SsoAuditLog()
            .setEventType(EVENT_CLIENT_LOGIN)
            .setOutcome(outcome)
            .setClientId(normalize(clientId))
            .setSubject(userInfo == null ? null : normalize(userInfo.getSub()))
            .setEmail(userInfo == null ? null : normalize(userInfo.getEmail()))
            .setMessage(message)
            .setCreatedAt(Instant.now());

        log.setMetadata(new Metadata());
        log.getMetadata().setGenerateName(SsoAuditLog.NAME_PREFIX);
        log.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(log));
        log.getMetadata().setLabels(MetadataUtil.nullSafeLabels(log));
        return log;
    }

    private Mono<SsoAuditLog> create(SsoAuditLog log) {
        Map<?, ?> extensionMap = objectMapper.convertValue(log, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.create(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoAuditLog.class));
    }

    private Mono<SsoAuditLogCleanupResult> cleanupExpiredLogs(List<SsoAuditLog> logs,
        Instant cutoffAt, int retentionDays, boolean dryRun) {
        var expiredLogs = logs.stream()
            .filter(log -> isExpired(log, cutoffAt))
            .toList();
        if (dryRun || expiredLogs.isEmpty()) {
            return Mono.just(cleanupResult(dryRun, retentionDays, cutoffAt, logs.size(),
                expiredLogs.size(), 0));
        }
        return Flux.fromIterable(expiredLogs)
            .flatMap(reactiveExtensionClient::delete)
            .count()
            .map(deleted -> cleanupResult(false, retentionDays, cutoffAt, logs.size(),
                expiredLogs.size(), deleted));
    }

    private static boolean isExpired(SsoAuditLog log, Instant cutoffAt) {
        return log.getCreatedAt() != null && log.getCreatedAt().isBefore(cutoffAt);
    }

    private static SsoAuditLogCleanupResult cleanupResult(boolean dryRun, int retentionDays,
        Instant cutoffAt, long scanned, long matched, long deleted) {
        return new SsoAuditLogCleanupResult()
            .setDryRun(dryRun)
            .setRetentionDays(retentionDays)
            .setCutoffAt(cutoffAt)
            .setScanned(scanned)
            .setMatched(matched)
            .setDeleted(deleted)
            .setRetained(Math.max(0, scanned - matched));
    }

    private static SsoAuditLogQuery normalizeQuery(SsoAuditLogQuery query) {
        if (query == null) {
            return new SsoAuditLogQuery(null, null, null, 1, 10);
        }
        return new SsoAuditLogQuery(
            normalize(query.outcome()),
            normalize(query.clientId()),
            normalize(query.keyword()),
            Math.max(1, query.page()),
            Math.min(100, Math.max(1, query.size()))
        );
    }

    private static boolean matches(SsoAuditLog log, SsoAuditLogQuery query) {
        if (hasText(query.outcome()) && !query.outcome().equals(log.getOutcome())) {
            return false;
        }
        if (hasText(query.clientId()) && !query.clientId().equals(log.getClientId())) {
            return false;
        }
        return !hasText(query.keyword()) || containsKeyword(log, query.keyword());
    }

    private static boolean containsKeyword(SsoAuditLog log, String keyword) {
        var normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(log.getSubject(), normalizedKeyword)
            || containsIgnoreCase(log.getEmail(), normalizedKeyword)
            || containsIgnoreCase(log.getMessage(), normalizedKeyword);
    }

    private static boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private static SsoAuditLogPage toPage(List<SsoAuditLog> logs, SsoAuditLogQuery query) {
        var total = logs.size();
        var totalPages = Math.max(1, (int) Math.ceil((double) total / query.size()));
        var page = Math.min(query.page(), totalPages);
        var fromIndex = Math.min((page - 1) * query.size(), total);
        var toIndex = Math.min(fromIndex + query.size(), total);
        return new SsoAuditLogPage()
            .setItems(new ArrayList<>(logs.subList(fromIndex, toIndex)))
            .setPage(page)
            .setSize(query.size())
            .setTotal(total)
            .setTotalPages(totalPages)
            .setHasPrevious(page > 1)
            .setHasNext(page < totalPages);
    }

    private static List<SsoAuditFailureSummary> toFailureSummaries(List<SsoAuditLog> logs,
        int limit) {
        Map<String, FailureAccumulator> grouped = new java.util.LinkedHashMap<>();
        for (var log : logs) {
            var message = messageOrDefault(log.getMessage(), "未知失败原因");
            grouped.computeIfAbsent(message, FailureAccumulator::new).add(log);
        }
        return grouped.values().stream()
            .map(FailureAccumulator::toSummary)
            .sorted(Comparator
                .comparingLong(SsoAuditFailureSummary::getCount).reversed()
                .thenComparing(SsoAuditFailureSummary::getLastOccurredAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .toList();
    }

    private static String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int normalizeRetentionDays(int retentionDays) {
        return Math.min(3650, Math.max(1, retentionDays));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class FailureAccumulator {

        private final String message;
        private final LinkedHashSet<String> clientIds = new LinkedHashSet<>();
        private long count;
        private Instant lastOccurredAt;

        private FailureAccumulator(String message) {
            this.message = message;
        }

        private void add(SsoAuditLog log) {
            count++;
            if (hasText(log.getClientId())) {
                clientIds.add(log.getClientId());
            }
            if (lastOccurredAt == null || isAfter(log.getCreatedAt(), lastOccurredAt)) {
                lastOccurredAt = log.getCreatedAt();
            }
        }

        private SsoAuditFailureSummary toSummary() {
            return new SsoAuditFailureSummary()
                .setMessage(message)
                .setCount(count)
                .setLastOccurredAt(lastOccurredAt)
                .setClientIds(clientIds.stream().filter(Objects::nonNull).toList());
        }

        private static boolean isAfter(Instant value, Instant baseline) {
            return value != null && (baseline == null || value.isAfter(baseline));
        }
    }
}
