package site.muyin.sso.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class Pkce {

    private Pkce() {
    }

    public static String challengeS256(String verifier) {
        if (!hasText(verifier)) {
            throw new IllegalArgumentException("code_verifier must not be blank");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static boolean matchesS256(String verifier, String challenge) {
        if (!hasText(verifier) || !hasText(challenge)) {
            return false;
        }
        return MessageDigest.isEqual(
            challengeS256(verifier).getBytes(StandardCharsets.US_ASCII),
            challenge.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
