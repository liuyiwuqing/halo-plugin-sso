package site.muyin.sso.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import site.muyin.sso.scheme.SsoClient;

public final class SsoClientName {

    private SsoClientName() {
    }

    public static String fromClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(clientId.getBytes(StandardCharsets.UTF_8));
            var suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
                .toLowerCase()
                .replace('_', '-')
                .substring(0, 32);
            return SsoClient.NAME_PREFIX + suffix;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
