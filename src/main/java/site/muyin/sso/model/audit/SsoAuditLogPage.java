package site.muyin.sso.model.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import site.muyin.sso.scheme.SsoAuditLog;

@Data
@Accessors(chain = true)
public class SsoAuditLogPage {

    @Schema(description = "当前页数据")
    private List<SsoAuditLog> items;

    @Schema(description = "当前页码，从 1 开始")
    private int page;

    @Schema(description = "每页数量")
    private int size;

    @Schema(description = "总数量")
    private long total;

    @Schema(description = "总页数")
    private int totalPages;

    @Schema(description = "是否有上一页")
    private boolean hasPrevious;

    @Schema(description = "是否有下一页")
    private boolean hasNext;
}
