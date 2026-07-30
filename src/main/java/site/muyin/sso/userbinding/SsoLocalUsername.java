package site.muyin.sso.userbinding;

public final class SsoLocalUsername {

    public static final String PREFIX = "sso-";

    private SsoLocalUsername() {
    }

    public static String fromSubject(String subject) {
        return PREFIX + SsoUserBindingName.suffixFromSubject(subject);
    }

    public static boolean belongsToSubject(String username, String subject) {
        return fromSubject(subject).equals(username);
    }
}
