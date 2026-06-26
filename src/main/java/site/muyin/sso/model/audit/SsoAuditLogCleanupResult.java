package site.muyin.sso.model.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SsoAuditLogCleanupResult {

    @Schema(description = "是否只预览，未执行删除")
    private boolean dryRun;

    @Schema(description = "保留天数")
    private int retentionDays;

    @Schema(description = "清理截止时间，早于该时间的日志会被清理")
    private Instant cutoffAt;

    @Schema(description = "扫描日志数量")
    private long scanned;

    @Schema(description = "匹配清理条件的日志数量")
    private long matched;

    @Schema(description = "实际删除日志数量")
    private long deleted;

    @Schema(description = "保留日志数量")
    private long retained;
}
