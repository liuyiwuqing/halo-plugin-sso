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
    kind = "SsoAuditLogCleanupRecord", plural = "ssoAuditLogCleanupRecords",
    singular = "ssoAuditLogCleanupRecord")
public class SsoAuditLogCleanupRecord extends AbstractExtension {

    public static final String NAME_PREFIX = "sso-audit-cleanup-";

    @Schema(description = "触发来源，manual 或 auto")
    private String trigger;

    @Schema(description = "是否执行成功")
    private Boolean success;

    @Schema(description = "是否只预览，未执行删除")
    private Boolean dryRun;

    @Schema(description = "开始时间")
    private Instant startedAt;

    @Schema(description = "结束时间")
    private Instant finishedAt;

    @Schema(description = "错误信息")
    private String message;

    @Schema(description = "保留天数")
    private Integer retentionDays;

    @Schema(description = "清理截止时间")
    private Instant cutoffAt;

    @Schema(description = "扫描日志数量")
    private Long scanned;

    @Schema(description = "匹配清理条件的日志数量")
    private Long matched;

    @Schema(description = "实际删除日志数量")
    private Long deleted;

    @Schema(description = "保留日志数量")
    private Long retained;
}
