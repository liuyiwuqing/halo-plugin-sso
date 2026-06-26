package site.muyin.sso.service;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.SsoAuthProviderMetadata;

public interface SsoCenterMetadataService {

    Mono<SsoAuthProviderMetadata> getMetadata(String fallbackBaseUrl);
}
