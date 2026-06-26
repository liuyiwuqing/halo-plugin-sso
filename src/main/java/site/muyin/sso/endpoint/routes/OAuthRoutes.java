package site.muyin.sso.endpoint.routes;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static site.muyin.sso.endpoint.SsoPublicEndpoint.PUBLIC_TAG;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import site.muyin.sso.model.oauth.OAuthTokenRequest;
import site.muyin.sso.model.oauth.OAuthTokenResponse;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.oauth.OAuthBearerToken;
import site.muyin.sso.service.OAuthAuthorizationService;

@Component
public class OAuthRoutes {

    private final OAuthAuthorizationService oauthAuthorizationService;
    private final OAuthAuthorizeHandler authorizeHandler;

    public OAuthRoutes(OAuthAuthorizationService oauthAuthorizationService,
        OAuthAuthorizeHandler authorizeHandler) {
        this.oauthAuthorizationService = oauthAuthorizationService;
        this.authorizeHandler = authorizeHandler;
    }

    public RouterFunction<ServerResponse> publicRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/authorize", this::authorize, builder -> builder
                .operationId("oauthAuthorize")
                .description("SSO OAuth 授权端点")
                .tag(PUBLIC_TAG)
                .parameter(parameterBuilder().name("response_type").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class))
                .parameter(parameterBuilder().name("client_id").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class))
                .parameter(parameterBuilder().name("redirect_uri").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class))
                .parameter(parameterBuilder().name("scope").in(ParameterIn.QUERY)
                    .required(false).implementation(String.class))
                .parameter(parameterBuilder().name("state").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class))
                .parameter(parameterBuilder().name("code_challenge").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class))
                .parameter(parameterBuilder().name("code_challenge_method").in(ParameterIn.QUERY)
                    .required(true).implementation(String.class)))
            .GET("/notice", this::notice, builder -> builder
                .operationId("oauthAuthorizeNotice")
                .description("SSO OAuth 授权拒绝提示页")
                .tag(PUBLIC_TAG)
                .parameter(parameterBuilder().name("code").in(ParameterIn.QUERY)
                    .required(false).implementation(String.class))
                .parameter(parameterBuilder().name("return_to").in(ParameterIn.QUERY)
                    .required(false).implementation(String.class))
                .response(responseBuilder().implementation(String.class)))
            .POST("/token", this::token, builder -> builder
                .operationId("oauthToken")
                .description("SSO OAuth Token 端点")
                .tag(PUBLIC_TAG)
                .response(responseBuilder().implementation(OAuthTokenResponse.class)))
            .GET("/userinfo", this::userinfo, builder -> builder
                .operationId("oauthUserinfo")
                .description("SSO OAuth UserInfo 端点")
                .tag(PUBLIC_TAG)
                .response(responseBuilder().implementation(OAuthUserInfoResponse.class)))
            .build();
    }

    private Mono<ServerResponse> authorize(ServerRequest request) {
        return authorizeHandler.authorize(request)
            .flatMap(location -> ServerResponse.temporaryRedirect(location).build());
    }

    private Mono<ServerResponse> notice(ServerRequest request) {
        var code = request.queryParam("code").orElse("access_denied");
        var returnTo = sanitizeReturnTo(request.queryParam("return_to").orElse("/login"));
        return ServerResponse.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(buildNoticePage(code, returnTo));
    }

    private Mono<ServerResponse> token(ServerRequest request) {
        return request.formData()
            .map(formData -> OAuthTokenRequest.builder()
                .grantType(formData.getFirst("grant_type"))
                .code(formData.getFirst("code"))
                .redirectUri(formData.getFirst("redirect_uri"))
                .clientId(formData.getFirst("client_id"))
                .clientSecret(formData.getFirst("client_secret"))
                .codeVerifier(formData.getFirst("code_verifier"))
                .build())
            .flatMap(oauthAuthorizationService::token)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> userinfo(ServerRequest request) {
        return oauthAuthorizationService.userInfo(extractBearerToken(request))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private static String extractBearerToken(ServerRequest request) {
        return OAuthBearerToken.fromAuthorizationHeader(
            request.headers().firstHeader(HttpHeaders.AUTHORIZATION));
    }

    private static String buildNoticePage(String code, String returnTo) {
        var title = "统一身份认证";
        var headline = switch (code) {
            case "email_not_verified" -> "邮箱未验证，暂时无法继续登录";
            case "email_missing" -> "邮箱为空，暂时无法继续登录";
            default -> "当前账号无法继续登录";
        };
        var message = switch (code) {
            case "email_not_verified" -> "当前账号邮箱未验证，不能跨站登录。请先前往个人中心完成邮箱验证，然后重新发起登录。";
            case "email_missing" -> "当前账号尚未填写邮箱，不能跨站登录。请先补充邮箱并完成验证，然后重新发起登录。";
            default -> "当前账号暂时无法完成跨站登录，请稍后再试。";
        };
        var primaryAction = "重新尝试登录";
        var secondaryAction = "前往个人中心";
        var safeReturnTo = HtmlUtils.htmlEscape(returnTo);
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>%s</title>
              <style>
                :root { color-scheme: light dark; }
                body {
                  margin: 0;
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background: #f5f7fb;
                  color: #111827;
                }
                .notice {
                  width: min(640px, calc(100vw - 32px));
                  border: 1px solid #dbe2ef;
                  background: #ffffff;
                  border-radius: 12px;
                  padding: 32px;
                  box-shadow: 0 12px 40px rgba(17, 24, 39, 0.08);
                }
                h1 {
                  margin: 0 0 12px;
                  font-size: 28px;
                  line-height: 1.2;
                }
                p {
                  margin: 0;
                  font-size: 16px;
                  line-height: 1.75;
                  color: #4b5563;
                }
                .actions {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 12px;
                  margin-top: 24px;
                }
                a {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  min-height: 44px;
                  padding: 0 16px;
                  border-radius: 10px;
                  text-decoration: none;
                  font-size: 15px;
                  font-weight: 600;
                }
                .primary {
                  background: #111827;
                  color: #fff;
                }
                .secondary {
                  border: 1px solid #d1d5db;
                  color: #111827;
                  background: #fff;
                }
                .hint {
                  margin-top: 16px;
                  font-size: 13px;
                  color: #6b7280;
                }
                code {
                  display: inline-block;
                  padding: 0 6px;
                  border-radius: 6px;
                  background: #f3f4f6;
                  color: #111827;
                }
              </style>
            </head>
            <body>
              <main class="notice">
                <h1>%s</h1>
                <p>%s</p>
                <div class="actions">
                  <a class="primary" href="%s">%s</a>
                  <a class="secondary" href="/uc/profile">%s</a>
                </div>
                <div class="hint">如果邮箱已经验证完成，请回到原登录页重新发起授权。</div>
              </main>
            </body>
            </html>
            """
            .formatted(
                HtmlUtils.htmlEscape(title),
                HtmlUtils.htmlEscape(headline),
                HtmlUtils.htmlEscape(message),
                safeReturnTo,
                HtmlUtils.htmlEscape(primaryAction),
                HtmlUtils.htmlEscape(secondaryAction)
            );
    }

    private static String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/login";
        }
        var trimmed = returnTo.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return "/login";
        }
        return trimmed;
    }

}
