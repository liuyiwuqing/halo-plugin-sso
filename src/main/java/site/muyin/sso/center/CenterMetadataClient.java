package site.muyin.sso.center;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.SsoAuthProviderMetadata;

public interface CenterMetadataClient {

    Mono<SsoAuthProviderMetadata> getMetadata(String centerUrl);
}
