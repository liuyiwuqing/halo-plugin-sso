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
    private static final int CODE_BYTES = 32;

    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, StoredAuthorizationCode> codes;

    public AuthorizationCodeManager() {
        this(Clock.systemUTC(), DEFAULT_TTL, new SecureRandom());
    }

    private AuthorizationCodeManager(Clock clock, Duration ttl, SecureRandom secureRandom) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.codes = new ConcurrentHashMap<>();
    }

    public static AuthorizationCodeManager inMemory(Clock clock) {
        return new AuthorizationCodeManager(clock, DEFAULT_TTL, new SecureRandom());
    }

    public IssuedAuthorizationCode issue(AuthorizationCodeIssueRequest request) {
        requireText(request.getClientId(), "clientId");
        requireText(request.getRedirectUri(), "redirectUri");
        requireText(request.getSubject(), "subject");
        requireText(request.getEmail(), "email");
        requireText(request.getCodeChallenge(), "codeChallenge");

        var now = clock.instant();
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

        var storedCode = codes.get(request.getCode());
        if (storedCode == null) {
            throw new AuthorizationCodeException("authorization_code not found");
        }
        return storedCode.consume(request, clock.instant());
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

        private synchronized AuthorizationCodeGrant consume(AuthorizationCodeConsumeRequest request,
            Instant now) {
            if (consumed) {
                throw new AuthorizationCodeException("authorization_code already used");
            }
            if (!now.isBefore(expiresAt)) {
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
