package site.muyin.sso.userbinding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import site.muyin.sso.scheme.SsoUserBinding;

public final class SsoUserBindingName {

    private SsoUserBindingName() {
    }

    public static String fromSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(subject.getBytes(StandardCharsets.UTF_8));
            var suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
                .toLowerCase()
                .replace('_', '-')
                .substring(0, 32);
            return SsoUserBinding.NAME_PREFIX + suffix;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }
}
