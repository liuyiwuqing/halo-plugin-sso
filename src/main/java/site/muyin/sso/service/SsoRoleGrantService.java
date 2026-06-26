package site.muyin.sso.service;

import java.util.Set;
import reactor.core.publisher.Mono;

public interface SsoRoleGrantService {

    Mono<Void> grantRoles(String username, Set<String> roles);
}
