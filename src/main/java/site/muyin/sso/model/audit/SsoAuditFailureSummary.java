package site.muyin.sso.model.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SsoAuditFailureSummary {

    @Schema(description = "失败原因")
    private String message;

    @Schema(description = "出现次数")
    private long count;

    @Schema(description = "最近发生时间")
    private Instant lastOccurredAt;

    @Schema(description = "涉及的接入站 Client ID")
    private List<String> clientIds;
}
