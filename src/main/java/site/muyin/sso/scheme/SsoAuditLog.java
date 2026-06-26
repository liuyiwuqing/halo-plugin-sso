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
    kind = "SsoAuditLog", plural = "ssoAuditLogs", singular = "ssoAuditLog")
public class SsoAuditLog extends AbstractExtension {

    public static final String NAME_PREFIX = "sso-audit-";

    @Schema(description = "事件类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String eventType;

    @Schema(description = "处理结果", requiredMode = Schema.RequiredMode.REQUIRED)
    private String outcome;

    @Schema(description = "接入站客户端 ID")
    private String clientId;

    @Schema(description = "中心站用户唯一主体")
    private String subject;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "说明")
    private String message;

    @Schema(description = "请求 IP")
    private String ipAddress;

    @Schema(description = "创建时间")
    private Instant createdAt;
}
