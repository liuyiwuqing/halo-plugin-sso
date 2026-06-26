package site.muyin.sso.model.client;

import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LocalUserProvisioningResult {

    String localUsername;

    Set<String> grantedRoles;

    boolean created;
}
