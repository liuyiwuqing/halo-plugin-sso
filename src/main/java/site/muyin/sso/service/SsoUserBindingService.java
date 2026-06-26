package site.muyin.sso.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;

public interface SsoUserBindingService {

    Flux<SsoUserBinding> listAllWithRX();

    Mono<SsoUserBinding> bindOrUpdateWithRX(OAuthUserInfoResponse userInfo);
}
