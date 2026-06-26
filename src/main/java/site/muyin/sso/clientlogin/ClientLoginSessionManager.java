package site.muyin.sso.clientlogin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import site.muyin.sso.oauth.Pkce;

@Component
public class ClientLoginSessionManager {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;
    private static final int CODE_VERIFIER_BYTES = 32;

    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, ClientLoginSession> sessions;

    public ClientLoginSessionManager() {
        this(Clock.systemUTC(), DEFAULT_TTL, new SecureRandom());
    }

    ClientLoginSessionManager(Clock clock, Duration ttl, SecureRandom secureRandom) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.sessions = new ConcurrentHashMap<>();
    }

    public ClientLoginSession start(String returnUrl) {
        var expiresAt = clock.instant().plus(ttl);
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
        var session = sessions.remove(state);
        if (session == null) {
            throw new ClientLoginException("登录状态不存在或已使用");
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            throw new ClientLoginException("登录状态已过期");
        }
        return session;
    }

    private String nextRandomText(int byteLength) {
        var bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
