package site.muyin.sso.model.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SsoAuditLogCleanupRequest {

    @Schema(description = "保留天数，小于该天数的旧日志会被清理", defaultValue = "90")
    private Integer retentionDays = 90;

    @Schema(description = "是否只预览，不执行删除", defaultValue = "true")
    private Boolean dryRun = true;
}
