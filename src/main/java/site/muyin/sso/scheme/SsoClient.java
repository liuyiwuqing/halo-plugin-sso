package site.muyin.sso.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
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
    kind = "SsoClient", plural = "ssoClients", singular = "ssoClient")
public class SsoClient extends AbstractExtension {

    public static final String NAME_PREFIX = "sso-client-";

    @Schema(description = "接入站客户端 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @Schema(description = "接入站客户端密钥哈希", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientSecretHash;

    @Schema(description = "接入站名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(description = "接入站首页地址")
    private String siteUrl;

    @Schema(description = "允许的 OAuth 回调地址白名单")
    private List<String> redirectUris;

    @Schema(description = "是否启用该接入站")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private Instant createdAt;

    @Schema(description = "更新时间")
    private Instant updatedAt;
}
