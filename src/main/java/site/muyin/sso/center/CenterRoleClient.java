package site.muyin.sso.center;

import reactor.core.publisher.Flux;
import site.muyin.sso.model.SsoPublicRole;

public interface CenterRoleClient {

    Flux<SsoPublicRole> listRoles(String centerUrl);
}
