package site.muyin.sso.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.core.SsoConstants;
import site.muyin.sso.endpoint.routes.ClientLoginRoutes;
import site.muyin.sso.endpoint.routes.OAuthRoutes;
import site.muyin.sso.model.SsoAuthProviderMetadata;
import site.muyin.sso.model.SsoPublicRole;
import site.muyin.sso.service.SsoCenterMetadataService;
import site.muyin.sso.setting.SsoGeneralSetting;

@Component
@RequiredArgsConstructor
public class SsoPublicEndpoint implements CustomEndpoint {

    public static final String PUBLIC_GROUP_VERSION =
        SsoConstants.PUBLIC_API_GROUP + "/" + SsoConstants.EXTENSION_VERSION;
    public static final String PUBLIC_TAG = PUBLIC_GROUP_VERSION + "/OAuth";
    private static final String ANNOTATION_DISPLAY_NAME =
        "rbac.authorization.halo.run/display-name";
    private static final String ANNOTATION_MODULE = "rbac.authorization.halo.run/module";
    private static final String LABEL_HIDDEN = "halo.run/hidden";
    private static final String LABEL_ROLE_TEMPLATE = "halo.run/role-template";

    private final OAuthRoutes oauthRoutes;
    private final ClientLoginRoutes clientLoginRoutes;
    private final ReactiveExtensionClient reactiveExtensionClient;
    private final ReactiveSettingFetcher settingFetcher;
    private final SsoCenterMetadataService centerMetadataService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("/metadata", this::metadata, builder -> builder
                .operationId("getSsoAuthProviderMetadata")
                .description("获取身份中心认证提供者元信息")
                .tag(PUBLIC_TAG)
                .response(responseBuilder().implementation(SsoAuthProviderMetadata.class)))
            .GET("/roles/list", this::listRoles, builder -> builder
                .operationId("listPublicSsoRoles")
                .description("获取中心身份站角色列表")
                .tag(PUBLIC_TAG)
                .response(responseBuilder().implementationArray(SsoPublicRole.class)))
            .nest(RequestPredicates.path("oauth"), oauthRoutes::publicRoutes)
            .nest(RequestPredicates.path("client"), clientLoginRoutes::publicRoutes)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(PUBLIC_GROUP_VERSION);
    }

    private Mono<ServerResponse> metadata(ServerRequest request) {
        return centerModeSetting()
            .then(centerMetadataService.getMetadata(RequestBaseUrlResolver.resolve(request)))
            .flatMap(metadata -> ServerResponse.ok().bodyValue(metadata));
    }

    private Mono<ServerResponse> listRoles(ServerRequest request) {
        return centerModeSetting()
            .flatMap(setting -> {
                var allowedRoles = normalizeStandardRoles(setting.getStandardRoles());
                return reactiveExtensionClient.listAll(
                        Role.class,
                        ListOptions.builder().fieldQuery(ExtensionUtil.notDeleting()).build(),
                        Sort.by("metadata.name")
                    )
                    .filter(role -> isAllowedStandardRole(role, allowedRoles))
                    .map(SsoPublicEndpoint::toPublicRole)
                    .sort(Comparator.comparing(SsoPublicRole::name))
                    .collectList()
                    .flatMap(roles -> ServerResponse.ok().bodyValue(roles));
            });
    }

    static boolean isAssignableRole(Role role) {
        var metadata = role == null ? null : role.getMetadata();
        return !labels(metadata).containsKey(LABEL_ROLE_TEMPLATE);
    }

    static boolean isAllowedStandardRole(Role role, Set<String> allowedRoles) {
        var metadata = role == null ? null : role.getMetadata();
        var name = metadata == null ? null : normalizeRole(metadata.getName());
        return name != null && isAssignableRole(role) && allowedRoles.contains(name);
    }

    static SsoPublicRole toPublicRole(Role role) {
        var metadata = role == null ? null : role.getMetadata();
        var name = metadata == null ? "" : metadata.getName();
        var annotations = annotations(metadata);
        var labels = labels(metadata);
        var displayName = annotations.getOrDefault(ANNOTATION_DISPLAY_NAME, name);
        return new SsoPublicRole(
            name,
            displayName == null || displayName.isBlank() ? name : displayName,
            annotations.get(ANNOTATION_MODULE),
            Boolean.parseBoolean(labels.getOrDefault(LABEL_HIDDEN, "false"))
        );
    }

    private static Map<String, String> annotations(MetadataOperator metadata) {
        return metadata == null || metadata.getAnnotations() == null
            ? Map.of()
            : metadata.getAnnotations();
    }

    private static Map<String, String> labels(MetadataOperator metadata) {
        return metadata == null || metadata.getLabels() == null
            ? Map.of()
            : metadata.getLabels();
    }

    private Mono<SsoGeneralSetting> centerModeSetting() {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .map(setting -> {
                if (!"center".equals(setting.getMode())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "当前插件不是身份中心模式");
                }
                return setting;
            });
    }

    private static Set<String> normalizeStandardRoles(Set<String> roles) {
        var source = roles == null ? SsoGeneralSetting.DEFAULT_STANDARD_ROLES : roles;
        return source.stream()
            .map(SsoPublicEndpoint::normalizeRole)
            .filter(role -> role != null)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeRole(String role) {
        return role == null || role.isBlank() ? null : role.trim();
    }
}
