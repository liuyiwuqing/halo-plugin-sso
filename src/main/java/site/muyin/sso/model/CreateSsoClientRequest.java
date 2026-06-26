package site.muyin.sso.model;

import java.util.List;
import lombok.Data;

@Data
public class CreateSsoClientRequest {

    private String displayName;

    private String siteUrl;

    private List<String> redirectUris;

    private Boolean enabled = true;
}
