package site.muyin.sso.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.CreateSsoClientRequest;
import site.muyin.sso.model.CreateSsoClientResponse;
import site.muyin.sso.scheme.SsoClient;

public interface SsoClientService {

    Mono<CreateSsoClientResponse> createWithRX(CreateSsoClientRequest request);

    Mono<SsoClient> updateWithRX(SsoClient client);

    Mono<SsoClient> deleteWithRX(String clientId);

    Mono<SsoClient> getByClientIdWithRX(String clientId);

    Flux<SsoClient> listAllWithRX();

    Mono<Boolean> verifySecretWithRX(String clientId, String clientSecret);

    Mono<SsoClient> requireAuthorizedClientWithRX(String clientId, String redirectUri);
}
