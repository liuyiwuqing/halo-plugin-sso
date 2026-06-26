package site.muyin.sso.oauth;

import site.muyin.sso.core.SsoConstants;

public final class OAuthEndpointPaths {

    public static final String PUBLIC_API_BASE = "/apis/" + SsoConstants.PUBLIC_API_GROUP + "/"
        + SsoConstants.EXTENSION_VERSION;

    public static final String AUTHORIZE = PUBLIC_API_BASE + "/oauth/authorize";

    public static final String NOTICE = PUBLIC_API_BASE + "/oauth/notice";

    public static final String TOKEN = PUBLIC_API_BASE + "/oauth/token";

    public static final String USERINFO = PUBLIC_API_BASE + "/oauth/userinfo";

    public static final String ROLES_LIST = PUBLIC_API_BASE + "/roles/list";

    public static final String CLIENT_LOGIN = PUBLIC_API_BASE + "/client/login";

    public static final String CLIENT_CALLBACK = PUBLIC_API_BASE + "/client/callback";

    private OAuthEndpointPaths() {
    }
}
