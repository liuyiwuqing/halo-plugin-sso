package site.muyin.sso.setting;

import lombok.Data;

@Data
public class SsoAuditSetting {

    private Boolean autoCleanupEnabled = true;

    private Integer retentionDays = 90;
}
