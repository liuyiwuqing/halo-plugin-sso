package site.muyin.sso.oauth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OAuthTokenStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final int TOKEN_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ACCESS_TOKEN_USE = "access";
    private static final String ID_TOKEN_USE = "id";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Base64.Encoder BASE64_URL_ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final Clock clock;
    private final SecureRandom secureRandom;

    public OAuthTokenStore() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    OAuthTokenStore(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public IssuedOAuthTokens issue(OAuthTokenClaims sourceClaims, String signingKey) {
        requireText(signingKey, "signingKey");
        var now = clock.instant();
        var expiresAt = now.plus(DEFAULT_TTL);
        var claims = new OAuthTokenClaims(
            sourceClaims.clientId(),
            sourceClaims.subject(),
            sourceClaims.username(),
            sourceClaims.email(),
            sourceClaims.displayName(),
            sourceClaims.avatar(),
            sourceClaims.roles(),
            sourceClaims.scopes(),
            now,
            expiresAt
        );
        var accessToken = encode(payload(ACCESS_TOKEN_USE, claims), signingKey);
        var idToken = encode(payload(ID_TOKEN_USE, claims), signingKey);
        return new IssuedOAuthTokens(accessToken, idToken, "Bearer", DEFAULT_TTL.toSeconds(), expiresAt);
    }

    public Optional<String> clientId(String token) {
        return readPayload(token).map(TokenPayload::clientId);
    }

    public Optional<OAuthTokenClaims> findAccessToken(String accessToken, String signingKey) {
        if (signingKey == null || signingKey.isBlank()) {
            return Optional.empty();
        }
        return readSignedPayload(accessToken, signingKey)
            .filter(payload -> ACCESS_TOKEN_USE.equals(payload.tokenUse()))
            .filter(payload -> clock.instant().isBefore(Instant.ofEpochSecond(
                payload.expiresAtEpochSecond())))
            .map(OAuthTokenStore::claims);
    }

    private TokenPayload payload(String tokenUse, OAuthTokenClaims claims) {
        return new TokenPayload(
            tokenUse,
            nextTokenId(),
            claims.clientId(),
            claims.subject(),
            claims.username(),
            claims.email(),
            claims.displayName(),
            claims.avatar(),
            nullSafeSet(claims.roles()),
            nullSafeSet(claims.scopes()),
            claims.issuedAt().getEpochSecond(),
            claims.expiresAt().getEpochSecond()
        );
    }

    private static OAuthTokenClaims claims(TokenPayload payload) {
        return new OAuthTokenClaims(
            payload.clientId(),
            payload.subject(),
            payload.username(),
            payload.email(),
            payload.displayName(),
            payload.avatar(),
            nullSafeSet(payload.roles()),
            nullSafeSet(payload.scopes()),
            Instant.ofEpochSecond(payload.issuedAtEpochSecond()),
            Instant.ofEpochSecond(payload.expiresAtEpochSecond())
        );
    }

    private static Set<String> nullSafeSet(Set<String> source) {
        return source == null ? Set.of() : Set.copyOf(source);
    }

    private String encode(TokenPayload payload, String signingKey) {
        try {
            var header = encodeSegment("{\"alg\":\"HS256\",\"typ\":\"SSO\"}"
                .getBytes(StandardCharsets.UTF_8));
            var body = encodeSegment(JSON_MAPPER.writeValueAsBytes(payload));
            var unsignedToken = header + "." + body;
            return unsignedToken + "." + encodeSegment(sign(unsignedToken, signingKey));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode OAuth token", e);
        }
    }

    private static Optional<TokenPayload> readSignedPayload(String token, String signingKey) {
        var parts = tokenParts(token);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        var tokenParts = parts.get();
        var unsignedToken = tokenParts.header() + "." + tokenParts.payload();
        try {
            var actualSignature = BASE64_URL_DECODER.decode(tokenParts.signature());
            var expectedSignature = sign(unsignedToken, signingKey);
            if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
                return Optional.empty();
            }
            return readPayload(token);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    private static Optional<TokenPayload> readPayload(String token) {
        var parts = tokenParts(token);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        try {
            var payloadJson = new String(BASE64_URL_DECODER.decode(parts.get().payload()),
                StandardCharsets.UTF_8);
            return Optional.of(JSON_MAPPER.readValue(payloadJson, TokenPayload.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<JwtParts> tokenParts(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        var parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank()
            || parts[2].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new JwtParts(parts[0], parts[1], parts[2]));
    }

    private static byte[] sign(String value, String signingKey) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign OAuth token", e);
        }
    }

    private static String encodeSegment(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private String nextTokenId() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private record JwtParts(String header, String payload, String signature) {
    }

    private record TokenPayload(
        String tokenUse,
        String tokenId,
        String clientId,
        String subject,
        String username,
        String email,
        String displayName,
        String avatar,
        Set<String> roles,
        Set<String> scopes,
        long issuedAtEpochSecond,
        long expiresAtEpochSecond
    ) {
    }
}
