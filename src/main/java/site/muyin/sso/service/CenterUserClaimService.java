package site.muyin.sso.service;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.CenterUserClaim;

public interface CenterUserClaimService {

    Mono<CenterUserClaim> currentUser();
}
