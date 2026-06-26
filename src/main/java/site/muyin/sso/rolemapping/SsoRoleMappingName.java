package site.muyin.sso.rolemapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import site.muyin.sso.scheme.SsoRoleMapping;

public final class SsoRoleMappingName {

    private SsoRoleMappingName() {
    }

    public static String fromCenterRole(String centerRole) {
        if (centerRole == null || centerRole.isBlank()) {
            throw new IllegalArgumentException("centerRole must not be blank");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(centerRole.trim().getBytes(StandardCharsets.UTF_8));
            var suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
                .toLowerCase()
                .replace('_', '-')
                .substring(0, 32);
            return SsoRoleMapping.NAME_PREFIX + suffix;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }
}
