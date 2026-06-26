package site.muyin.sso.model.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SsoAuditLogCleanupStatus {

    @Schema(description = "触发来源，manual 或 auto")
    private String trigger;

    @Schema(description = "是否执行成功")
    private boolean success;

    @Schema(description = "开始时间")
    private Instant startedAt;

    @Schema(description = "结束时间")
    private Instant finishedAt;

    @Schema(description = "错误信息")
    private String message;

    @Schema(description = "清理结果")
    private SsoAuditLogCleanupResult result;
}
