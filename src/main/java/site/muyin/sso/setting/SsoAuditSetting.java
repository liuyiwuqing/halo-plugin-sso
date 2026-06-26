package site.muyin.sso.setting;

import lombok.Data;

@Data
public class SsoAuditSetting {

    private Boolean autoCleanupEnabled = false;

    private Integer retentionDays = 90;
}
