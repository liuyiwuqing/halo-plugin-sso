package site.muyin.sso.service;

import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.sso.scheme.SsoRoleMapping;

public interface SsoRoleMappingService {

    Flux<SsoRoleMapping> listAllWithRX();

    Mono<SsoRoleMapping> getByCenterRoleWithRX(String centerRole);

    Mono<SsoRoleMapping> createWithRX(SsoRoleMapping mapping);

    Mono<SsoRoleMapping> updateWithRX(SsoRoleMapping mapping);

    Mono<Set<String>> resolveLocalRoles(Set<String> centerRoles, String defaultRole);
}
