package site.muyin.sso.service;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.client.ClientLoginCallbackResult;
import site.muyin.sso.model.client.ClientLoginStartResult;

public interface ClientLoginService {

    Mono<ClientLoginStartResult> startLogin(String returnUrl, String externalUrl,
        String requesterKey);

    default Mono<ClientLoginStartResult> startLogin(String returnUrl, String externalUrl) {
        return startLogin(returnUrl, externalUrl, "unknown");
    }

    Mono<ClientLoginCallbackResult> handleCallback(String code, String state, String externalUrl);
}
