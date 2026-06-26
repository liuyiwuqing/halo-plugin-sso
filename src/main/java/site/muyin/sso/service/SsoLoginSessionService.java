package site.muyin.sso.service;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface SsoLoginSessionService {

    Mono<Void> login(ServerWebExchange exchange, String username);
}
