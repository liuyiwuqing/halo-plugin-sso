package site.muyin.sso.setting;

import java.util.Set;
import lombok.Data;

@Data
public class SsoGeneralSetting {

    public static final Set<String> DEFAULT_STANDARD_ROLES =
        Set.of("subscriber", "author", "editor");

    private String mode = "center";

    private String externalUrl;

    private Boolean allowHttpForLocalhost = true;

    private Set<String> standardRoles = DEFAULT_STANDARD_ROLES;

    private Boolean requireVerifiedEmail = true;

    private String centerUrl;

    private String clientId;

    private String clientSecret;

    private String defaultRole = "guest";

    private Boolean syncProfileOnLogin = true;

    private Boolean autoSsoLoginEnabled = true;
}
