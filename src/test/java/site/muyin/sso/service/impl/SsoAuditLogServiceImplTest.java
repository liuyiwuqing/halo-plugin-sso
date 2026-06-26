package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.query.SsoAuditLogQuery;
import site.muyin.sso.scheme.SsoAuditLog;
import site.muyin.sso.service.SsoAuditLogService;

class SsoAuditLogServiceImplTest {

    @Test
    void recordsSuccessfulClientLogin() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);

        when(reactiveExtensionClient.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var log = service.recordLoginSuccess(" site-b ", OAuthUserInfoResponse.builder()
            .sub(" user-001 ")
            .email(" lywq@example.com ")
            .roles(Set.of("author"))
            .build(), " ok ").block();

        assertThat(log.getMetadata().getGenerateName()).isEqualTo(SsoAuditLog.NAME_PREFIX);
        assertThat(log.getEventType()).isEqualTo(SsoAuditLogService.EVENT_CLIENT_LOGIN);
        assertThat(log.getOutcome()).isEqualTo(SsoAuditLogService.OUTCOME_SUCCESS);
        assertThat(log.getClientId()).isEqualTo("site-b");
        assertThat(log.getSubject()).isEqualTo("user-001");
        assertThat(log.getEmail()).isEqualTo("lywq@example.com");
        assertThat(log.getMessage()).isEqualTo("ok");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void recordsFailedClientLoginWithoutUserInfo() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);

        when(reactiveExtensionClient.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var log = service.recordLoginFailure("site-b", null, "invalid state").block();

        assertThat(log.getOutcome()).isEqualTo(SsoAuditLogService.OUTCOME_FAILURE);
        assertThat(log.getClientId()).isEqualTo("site-b");
        assertThat(log.getSubject()).isNull();
        assertThat(log.getEmail()).isNull();
        assertThat(log.getMessage()).isEqualTo("invalid state");
    }

    @Test
    void listsAuditLogsByCreatedTimeDesc() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);
        var log = new SsoAuditLog().setEventType("client_login");

        when(reactiveExtensionClient.listAll(eq(SsoAuditLog.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.just(log));

        var logs = service.listAllWithRX().collectList().block();

        assertThat(logs).containsExactly(log);
    }

    @Test
    void filtersAndPaginatesAuditLogs() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);
        var first = auditLog("failure", "site-b", "user-001", "a@example.com",
            "invalid state", Instant.parse("2026-01-03T00:00:00Z"));
        var second = auditLog("failure", "site-b", "user-002", "b@example.com",
            "token rejected", Instant.parse("2026-01-02T00:00:00Z"));
        var ignoredOutcome = auditLog("success", "site-b", "user-003", "c@example.com",
            "ok", Instant.parse("2026-01-01T00:00:00Z"));
        var ignoredClient = auditLog("failure", "site-c", "user-004", "d@example.com",
            "token rejected", Instant.parse("2026-01-01T00:00:00Z"));

        when(reactiveExtensionClient.listAll(eq(SsoAuditLog.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.fromIterable(List.of(first, second, ignoredOutcome, ignoredClient)));

        var page = service.listWithRX(new SsoAuditLogQuery("failure", "site-b", null, 2, 1))
            .block();

        assertThat(page.getItems()).containsExactly(second);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.isHasPrevious()).isTrue();
        assertThat(page.isHasNext()).isFalse();
    }

    @Test
    void searchesAuditLogsBySubjectEmailOrMessage() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);
        var matchedByEmail = auditLog("failure", "site-b", "user-001", "lywq@example.com",
            "invalid state", Instant.parse("2026-01-03T00:00:00Z"));
        var matchedByMessage = auditLog("failure", "site-b", "user-002", "b@example.com",
            "profile sync failed", Instant.parse("2026-01-02T00:00:00Z"));
        var ignored = auditLog("failure", "site-b", "user-003", "c@example.com",
            "token rejected", Instant.parse("2026-01-01T00:00:00Z"));

        when(reactiveExtensionClient.listAll(eq(SsoAuditLog.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.fromIterable(List.of(matchedByEmail, matchedByMessage, ignored)));

        var page = service.listWithRX(new SsoAuditLogQuery(null, null, "LYWQ", 1, 10)).block();
        var messagePage = service.listWithRX(new SsoAuditLogQuery(null, null, "sync", 1, 10))
            .block();

        assertThat(page.getItems()).containsExactly(matchedByEmail);
        assertThat(messagePage.getItems()).containsExactly(matchedByMessage);
    }

    @Test
    void aggregatesRecentFailureReasons() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);
        var newestInvalidState = auditLog("failure", "site-b", "user-001", "a@example.com",
            "invalid state", Instant.parse("2026-01-03T00:00:00Z"));
        var olderInvalidState = auditLog("failure", "site-c", "user-002", "b@example.com",
            " invalid state ", Instant.parse("2026-01-01T00:00:00Z"));
        var tokenRejected = auditLog("failure", "site-b", "user-003", "c@example.com",
            "token rejected", Instant.parse("2026-01-02T00:00:00Z"));
        var success = auditLog("success", "site-b", "user-004", "d@example.com",
            "ok", Instant.parse("2026-01-04T00:00:00Z"));

        when(reactiveExtensionClient.listAll(eq(SsoAuditLog.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.fromIterable(List.of(newestInvalidState, olderInvalidState,
                tokenRejected, success)));

        var summaries = service.listRecentFailuresWithRX(2).collectList().block();

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).getMessage()).isEqualTo("invalid state");
        assertThat(summaries.get(0).getCount()).isEqualTo(2);
        assertThat(summaries.get(0).getLastOccurredAt())
            .isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
        assertThat(summaries.get(0).getClientIds()).containsExactly("site-b", "site-c");
        assertThat(summaries.get(1).getMessage()).isEqualTo("token rejected");
    }

    @Test
    void previewsExpiredAuditLogCleanupWithoutDeleting() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);
        var expired = auditLog("failure", "site-b", "user-001", "a@example.com",
            "invalid state", Instant.parse("2026-01-01T00:00:00Z"));
        var retained = auditLog("success", "site-b", "user-002", "b@example.com",
            "ok", Instant.parse("2026-06-01T00:00:00Z"));

        when(reactiveExtensionClient.listAll(eq(SsoAuditLog.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.fromIterable(List.of(expired, retained)));

        var result = service.cleanupBeforeWithRX(Instant.parse("2026-03-01T00:00:00Z"), 90, true)
            .block();

        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getRetentionDays()).isEqualTo(90);
        assertThat(result.getCutoffAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(result.getScanned()).isEqualTo(2);
        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(result.getDeleted()).isZero();
        assertThat(result.getRetained()).isEqualTo(1);
        verify(reactiveExtensionClient, never()).delete(any(SsoAuditLog.class));
    }

    @Test
    void deletesExpiredAuditLogs() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuditLogServiceImpl(reactiveExtensionClient);
        var expired = auditLog("failure", "site-b", "user-001", "a@example.com",
            "invalid state", Instant.parse("2026-01-01T00:00:00Z"));
        var retained = auditLog("success", "site-b", "user-002", "b@example.com",
            "ok", Instant.parse("2026-06-01T00:00:00Z"));

        when(reactiveExtensionClient.listAll(eq(SsoAuditLog.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.fromIterable(List.of(expired, retained)));
        when(reactiveExtensionClient.delete(any(SsoAuditLog.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, SsoAuditLog.class)));

        var result = service.cleanupBeforeWithRX(Instant.parse("2026-03-01T00:00:00Z"), 90, false)
            .block();

        assertThat(result.isDryRun()).isFalse();
        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(result.getDeleted()).isEqualTo(1);
        assertThat(result.getRetained()).isEqualTo(1);
        verify(reactiveExtensionClient).delete(eq(expired));
        verify(reactiveExtensionClient, never()).delete(eq(retained));
    }

    private static SsoAuditLog auditLog(String outcome, String clientId, String subject,
        String email, String message, Instant createdAt) {
        return new SsoAuditLog()
            .setEventType(SsoAuditLogService.EVENT_CLIENT_LOGIN)
            .setOutcome(outcome)
            .setClientId(clientId)
            .setSubject(subject)
            .setEmail(email)
            .setMessage(message)
            .setCreatedAt(createdAt);
    }
}
