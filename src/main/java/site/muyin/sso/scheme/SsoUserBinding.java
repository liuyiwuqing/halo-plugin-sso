package site.muyin.sso.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
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
    kind = "SsoUserBinding", plural = "ssoUserBindings", singular = "ssoUserBinding")
public class SsoUserBinding extends AbstractExtension {

    public static final String NAME_PREFIX = "sso-user-binding-";

    @Schema(description = "中心站用户唯一主体", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subject;

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "接入站本地用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String localUsername;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "绑定时间")
    private Instant boundAt;

    @Schema(description = "最后登录时间")
    private Instant lastLoginAt;
}
