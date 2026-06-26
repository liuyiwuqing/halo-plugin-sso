package site.muyin.sso.service;

import java.util.Set;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.client.LocalUserProvisioningResult;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;

public interface SsoLocalUserProvisioningService {

    Mono<LocalUserProvisioningResult> provisionWithRX(SsoUserBinding binding,
        OAuthUserInfoResponse userInfo, Set<String> localRoles, boolean syncProfile);
}
