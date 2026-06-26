package site.muyin.sso.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
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
    kind = "SsoRoleMapping", plural = "ssoRoleMappings", singular = "ssoRoleMapping")
public class SsoRoleMapping extends AbstractExtension {

    public static final String NAME_PREFIX = "sso-role-mapping-";

    @Schema(description = "中心身份站标准角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String centerRole;

    @Schema(description = "接入站本地 Halo 角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String localRole;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sort;
}
