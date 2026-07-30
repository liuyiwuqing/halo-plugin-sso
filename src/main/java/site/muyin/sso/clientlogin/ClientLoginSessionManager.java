package site.muyin.sso.clientlogin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import site.muyin.sso.oauth.Pkce;

@Component
public class ClientLoginSessionManager {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_SESSIONS = 10_000;
    private static final int DEFAULT_MAX_SESSIONS_PER_REQUESTER = 64;
    private static final Duration MAX_CLEANUP_INTERVAL = Duration.ofMinutes(1);
    private static final int STATE_BYTES = 32;
    private static final int CODE_VERIFIER_BYTES = 32;

    private final Clock clock;
    private final Duration ttl;
    private final int maxSessions;
    private final int maxSessionsPerRequester;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, ClientLoginSession> sessions;
    private final ConcurrentMap<String, String> sessionRequesters;
    private final ConcurrentMap<String, Integer> requesterSessionCounts;
    private Instant nextCleanupAt = Instant.EPOCH;

    public ClientLoginSessionManager() {
        this(Clock.systemUTC(), DEFAULT_TTL, new SecureRandom());
    }

    ClientLoginSessionManager(Clock clock, Duration ttl, SecureRandom secureRandom) {
        this(clock, ttl, secureRandom, DEFAULT_MAX_SESSIONS,
            DEFAULT_MAX_SESSIONS_PER_REQUESTER);
    }

    ClientLoginSessionManager(Clock clock, Duration ttl, SecureRandom secureRandom,
        int maxSessions) {
        this(clock, ttl, secureRandom, maxSessions, DEFAULT_MAX_SESSIONS_PER_REQUESTER);
    }

    ClientLoginSessionManager(Clock clock, Duration ttl, SecureRandom secureRandom,
        int maxSessions, int maxSessionsPerRequester) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = requirePositive(ttl, "ttl");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.maxSessions = requirePositive(maxSessions, "maxSessions");
        this.maxSessionsPerRequester = requirePositive(maxSessionsPerRequester,
            "maxSessionsPerRequester");
        this.sessions = new ConcurrentHashMap<>();
        this.sessionRequesters = new ConcurrentHashMap<>();
        this.requesterSessionCounts = new ConcurrentHashMap<>();
    }

    public synchronized ClientLoginSession start(String returnUrl) {
        return start(returnUrl, "unknown");
    }

    public synchronized ClientLoginSession start(String returnUrl, String requesterKey) {
        var now = clock.instant();
        var normalizedRequesterKey = normalizeRequesterKey(requesterKey);
        cleanupExpiredIfDue(now);
        ensureCapacity(now, normalizedRequesterKey);
        var expiresAt = now.plus(ttl);
        while (true) {
            var state = nextRandomText(STATE_BYTES);
            var codeVerifier = nextRandomText(CODE_VERIFIER_BYTES);
            var session = new ClientLoginSession(
                state,
                codeVerifier,
                Pkce.challengeS256(codeVerifier),
                returnUrl,
                expiresAt
            );
            if (sessions.putIfAbsent(state, session) == null) {
                sessionRequesters.put(state, normalizedRequesterKey);
                requesterSessionCounts.merge(normalizedRequesterKey, 1, Integer::sum);
                return session;
            }
        }
    }

    public synchronized ClientLoginSession consume(String state) {
        if (state == null || state.isBlank()) {
            throw new ClientLoginException("state 不能为空");
        }
        var now = clock.instant();
        var session = sessions.remove(state);
        removeRequester(state);
        if (session == null) {
            throw new ClientLoginException("登录状态不存在或已使用");
        }
        if (!now.isBefore(session.expiresAt())) {
            throw new ClientLoginException("登录状态已过期");
        }
        return session;
    }

    private void cleanupExpired(Instant now) {
        sessions.forEach((state, session) -> {
            if (!now.isBefore(session.expiresAt())) {
                if (sessions.remove(state, session)) {
                    removeRequester(state);
                }
            }
        });
    }

    private void cleanupExpiredIfDue(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }
        cleanupExpired(now);
        var cleanupInterval = ttl.compareTo(MAX_CLEANUP_INTERVAL) < 0
            ? ttl
            : MAX_CLEANUP_INTERVAL;
        nextCleanupAt = now.plus(cleanupInterval);
    }

    private void ensureCapacity(Instant now, String requesterKey) {
        if (requesterSessionCounts.getOrDefault(requesterKey, 0)
            >= maxSessionsPerRequester) {
            throw new ClientLoginException("当前请求来源的未完成登录状态数量已达上限，请稍后再试");
        }
        if (sessions.size() >= maxSessions) {
            cleanupExpired(now);
            if (sessions.size() >= maxSessions) {
                throw new ClientLoginException("未完成登录状态数量已达上限，请稍后再试");
            }
        }
    }

    private void removeRequester(String state) {
        var requesterKey = sessionRequesters.remove(state);
        if (requesterKey == null) {
            return;
        }
        requesterSessionCounts.computeIfPresent(requesterKey,
            (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private static String normalizeRequesterKey(String requesterKey) {
        return requesterKey == null || requesterKey.isBlank() ? "unknown" : requesterKey.trim();
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private String nextRandomText(int byteLength) {
        var bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
