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
    private static final int STATE_BYTES = 32;
    private static final int CODE_VERIFIER_BYTES = 32;

    private final Clock clock;
    private final Duration ttl;
    private final int maxSessions;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, ClientLoginSession> sessions;

    public ClientLoginSessionManager() {
        this(Clock.systemUTC(), DEFAULT_TTL, new SecureRandom());
    }

    ClientLoginSessionManager(Clock clock, Duration ttl, SecureRandom secureRandom) {
        this(clock, ttl, secureRandom, DEFAULT_MAX_SESSIONS);
    }

    ClientLoginSessionManager(Clock clock, Duration ttl, SecureRandom secureRandom,
        int maxSessions) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = requirePositive(ttl, "ttl");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.maxSessions = requirePositive(maxSessions, "maxSessions");
        this.sessions = new ConcurrentHashMap<>();
    }

    public synchronized ClientLoginSession start(String returnUrl) {
        var now = clock.instant();
        cleanupExpired(now);
        ensureCapacity();
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
                return session;
            }
        }
    }

    public ClientLoginSession consume(String state) {
        if (state == null || state.isBlank()) {
            throw new ClientLoginException("state 不能为空");
        }
        var now = clock.instant();
        var session = sessions.remove(state);
        cleanupExpired(now);
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
                sessions.remove(state, session);
            }
        });
    }

    private void ensureCapacity() {
        if (sessions.size() >= maxSessions) {
            throw new ClientLoginException("未完成登录状态数量已达上限，请稍后再试");
        }
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
