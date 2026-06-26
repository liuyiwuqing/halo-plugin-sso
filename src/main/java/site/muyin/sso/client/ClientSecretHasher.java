package site.muyin.sso.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ClientSecretHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String clientSecret) {
        requireSecret(clientSecret);
        var salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        var digest = derive(clientSecret, salt, ITERATIONS);
        return PREFIX + ":" + ITERATIONS + ":"
            + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + ":"
            + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    public boolean matches(String clientSecret, String hash) {
        if (clientSecret == null || hash == null) {
            return false;
        }
        var parts = hash.split(":");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            var iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            var salt = Base64.getUrlDecoder().decode(parts[2]);
            var expected = Base64.getUrlDecoder().decode(parts[3]);
            var actual = derive(clientSecret, salt, iterations);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    private static byte[] derive(String clientSecret, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(clientSecret.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        } catch (java.security.spec.InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid client secret key spec", e);
        }
    }

    private static void requireSecret(String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("clientSecret must not be blank");
        }
    }
}
