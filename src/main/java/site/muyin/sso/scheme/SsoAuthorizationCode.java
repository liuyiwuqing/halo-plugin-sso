package site.muyin.sso.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import site.muyin.sso.core.SsoConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@GVK(group = SsoConstants.EXTENSION_GROUP, version = SsoConstants.EXTENSION_VERSION,
    kind = "SsoAuthorizationCode", plural = "ssoAuthorizationCodes",
    singular = "ssoAuthorizationCode")
public class SsoAuthorizationCode extends AbstractExtension {

    public static final String NAME_PREFIX = "sso-code-";

    @Schema(description = "授权码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "接入站客户端 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @Schema(description = "OAuth 回调地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String redirectUri;

    @Schema(description = "中心站用户唯一主体", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subject;

    @Schema(description = "中心站用户邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "PKCE S256 challenge", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codeChallenge;

    @Schema(description = "授权范围")
    private Set<String> scopes;

    @Schema(description = "签发时间")
    private Instant issuedAt;

    @Schema(description = "过期时间")
    private Instant expiresAt;

    @Schema(description = "是否已消费")
    private Boolean consumed;
}
