package site.muyin.sso.client;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ClientIdGenerator {

    private static final int CLIENT_ID_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        var bytes = new byte[CLIENT_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return "sso-" + HexFormat.of().formatHex(bytes);
    }
}
