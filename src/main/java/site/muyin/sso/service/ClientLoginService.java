package site.muyin.sso.service;

import reactor.core.publisher.Mono;
import site.muyin.sso.model.client.ClientLoginCallbackResult;
import site.muyin.sso.model.client.ClientLoginStartResult;

public interface ClientLoginService {

    Mono<ClientLoginStartResult> startLogin(String returnUrl, String externalUrl);

    Mono<ClientLoginCallbackResult> handleCallback(String code, String state, String externalUrl);
}
