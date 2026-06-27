package site.muyin.sso.oauth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationCodeManager {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_CODES = 10_000;
    private static final int CODE_BYTES = 32;

    private final Clock clock;
    private final Duration ttl;
    private final int maxCodes;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, StoredAuthorizationCode> codes;

    public AuthorizationCodeManager() {
        this(Clock.systemUTC(), DEFAULT_TTL, new SecureRandom());
    }

    private AuthorizationCodeManager(Clock clock, Duration ttl, SecureRandom secureRandom) {
        this(clock, ttl, secureRandom, DEFAULT_MAX_CODES);
    }

    private AuthorizationCodeManager(Clock clock, Duration ttl, SecureRandom secureRandom,
        int maxCodes) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = requirePositive(ttl, "ttl");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.maxCodes = requirePositive(maxCodes, "maxCodes");
        this.codes = new ConcurrentHashMap<>();
    }

    public static AuthorizationCodeManager inMemory(Clock clock) {
        return new AuthorizationCodeManager(clock, DEFAULT_TTL, new SecureRandom());
    }

    static AuthorizationCodeManager inMemory(Clock clock, Duration ttl, int maxCodes) {
        return new AuthorizationCodeManager(clock, ttl, new SecureRandom(), maxCodes);
    }

    public synchronized IssuedAuthorizationCode issue(AuthorizationCodeIssueRequest request) {
        requireText(request.getClientId(), "clientId");
        requireText(request.getRedirectUri(), "redirectUri");
        requireText(request.getSubject(), "subject");
        requireText(request.getEmail(), "email");
        requireText(request.getCodeChallenge(), "codeChallenge");

        var now = clock.instant();
        cleanupExpired(now);
        ensureCapacity();
        var expiresAt = now.plus(ttl);
        while (true) {
            var code = nextCode();
            var storedCode = new StoredAuthorizationCode(
                request.getClientId(),
                request.getRedirectUri(),
                request.getSubject(),
                normalizeOptionalText(request.getUsername()),
                request.getEmail(),
                normalizeOptionalText(request.getDisplayName()),
                normalizeOptionalText(request.getAvatar()),
                Set.copyOf(request.getRoles()),
                request.getCodeChallenge(),
                Set.copyOf(request.getScopes()),
                now,
                expiresAt
            );
            if (codes.putIfAbsent(code, storedCode) == null) {
                return new IssuedAuthorizationCode(code, expiresAt);
            }
        }
    }

    public AuthorizationCodeGrant consume(AuthorizationCodeConsumeRequest request) {
        requireText(request.getCode(), "code");
        requireText(request.getClientId(), "clientId");
        requireText(request.getRedirectUri(), "redirectUri");
        requireText(request.getCodeVerifier(), "codeVerifier");

        var now = clock.instant();
        var storedCode = codes.get(request.getCode());
        if (storedCode == null) {
            cleanupExpired(now);
            throw new AuthorizationCodeException("authorization_code not found or already used");
        }
        if (storedCode.isExpired(now)) {
            codes.remove(request.getCode(), storedCode);
            cleanupExpired(now);
            throw new AuthorizationCodeException("authorization_code expired");
        }
        var grant = storedCode.consume(request, now);
        codes.remove(request.getCode(), storedCode);
        return grant;
    }

    private String nextCode() {
        var bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private void cleanupExpired(Instant now) {
        codes.forEach((code, storedCode) -> {
            if (storedCode.isExpired(now)) {
                codes.remove(code, storedCode);
            }
        });
    }

    private void ensureCapacity() {
        if (codes.size() >= maxCodes) {
            throw new AuthorizationCodeException("authorization_code capacity exceeded");
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

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class StoredAuthorizationCode {

        private final String clientId;
        private final String redirectUri;
        private final String subject;
        private final String username;
        private final String email;
        private final String displayName;
        private final String avatar;
        private final Set<String> roles;
        private final String codeChallenge;
        private final Set<String> scopes;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private boolean consumed;

        private StoredAuthorizationCode(String clientId, String redirectUri, String subject,
            String username, String email, String displayName, String avatar, Set<String> roles,
            String codeChallenge, Set<String> scopes, Instant issuedAt, Instant expiresAt) {
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.subject = subject;
            this.username = username;
            this.email = email;
            this.displayName = displayName;
            this.avatar = avatar;
            this.roles = roles;
            this.codeChallenge = codeChallenge;
            this.scopes = scopes;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }

        private synchronized AuthorizationCodeGrant consume(AuthorizationCodeConsumeRequest request,
            Instant now) {
            if (consumed) {
                throw new AuthorizationCodeException("authorization_code already used");
            }
            if (isExpired(now)) {
                throw new AuthorizationCodeException("authorization_code expired");
            }
            if (!clientId.equals(request.getClientId())) {
                throw new AuthorizationCodeException("authorization_code client mismatch");
            }
            if (!redirectUri.equals(request.getRedirectUri())) {
                throw new AuthorizationCodeException("authorization_code redirect_uri mismatch");
            }
            if (!Pkce.matchesS256(request.getCodeVerifier(), codeChallenge)) {
                throw new AuthorizationCodeException("authorization_code PKCE verification failed");
            }
            consumed = true;
            return new AuthorizationCodeGrant(clientId, redirectUri, subject, username, email,
                displayName, avatar, roles, scopes, issuedAt);
        }
    }
}
