package site.muyin.sso.endpoint;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import site.muyin.sso.core.SsoConstants;
import site.muyin.sso.endpoint.routes.SsoAuditLogRoutes;
import site.muyin.sso.endpoint.routes.SsoClientRoutes;
import site.muyin.sso.endpoint.routes.SsoRoleMappingRoutes;
import site.muyin.sso.endpoint.routes.SsoSettingRoutes;
import site.muyin.sso.endpoint.routes.SsoUserBindingRoutes;

@Component
@RequiredArgsConstructor
public class SsoConsoleEndpoint implements CustomEndpoint {

    public static final String CONSOLE_GROUP_VERSION =
        SsoConstants.CONSOLE_API_GROUP + "/" + SsoConstants.EXTENSION_VERSION;
    public static final String CONSOLE_TAG = CONSOLE_GROUP_VERSION + "/Sso";

    private final SsoClientRoutes ssoClientRoutes;
    private final SsoRoleMappingRoutes ssoRoleMappingRoutes;
    private final SsoUserBindingRoutes ssoUserBindingRoutes;
    private final SsoAuditLogRoutes ssoAuditLogRoutes;
    private final SsoSettingRoutes ssoSettingRoutes;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .nest(RequestPredicates.path("clients"), ssoClientRoutes::consoleRoutes)
            .nest(RequestPredicates.path("role-mappings"), ssoRoleMappingRoutes::consoleRoutes)
            .nest(RequestPredicates.path("user-bindings"), ssoUserBindingRoutes::consoleRoutes)
            .nest(RequestPredicates.path("audit-logs"), ssoAuditLogRoutes::consoleRoutes)
            .nest(RequestPredicates.path("settings"), ssoSettingRoutes::consoleRoutes)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(CONSOLE_GROUP_VERSION);
    }
}
