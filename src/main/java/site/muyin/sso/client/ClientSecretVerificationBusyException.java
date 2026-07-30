package site.muyin.sso.client;

public class ClientSecretVerificationBusyException extends SsoClientException {

    public ClientSecretVerificationBusyException() {
        super("client_secret 校验请求过多，请稍后重试");
    }
}
