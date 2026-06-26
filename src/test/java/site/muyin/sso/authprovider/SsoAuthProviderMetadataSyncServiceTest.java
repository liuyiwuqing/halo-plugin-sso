package site.muyin.sso.authprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.AuthProvider;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.center.CenterMetadataClient;
import site.muyin.sso.model.SsoAuthProviderMetadata;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.setting.SsoGeneralSetting;

class SsoAuthProviderMetadataSyncServiceTest {

    @Test
    void syncsClientAuthProviderMetadataFromCenterSite() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerMetadataClient = mock(CenterMetadataClient.class);
        var extensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuthProviderMetadataSyncService(settingFetcher, centerMetadataClient,
            extensionClient);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        setting.setCenterUrl("https://auth.muyin.site/");
        var provider = authProvider();

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        when(centerMetadataClient.getMetadata("https://auth.muyin.site/"))
            .thenReturn(Mono.just(new SsoAuthProviderMetadata(
                "木因身份中心",
                "使用木因账号登录",
                "https://auth.muyin.site/upload/auth-logo.png",
                "https://auth.muyin.site"
            )));
        when(extensionClient.fetch(AuthProvider.class, "muyin-sso"))
            .thenReturn(Mono.just(provider));
        when(extensionClient.update(any(AuthProvider.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, AuthProvider.class)));

        var synced = service.syncOnceWithRX().block();

        assertThat(synced).isNotNull();
        assertThat(synced.getSpec().getDisplayName()).isEqualTo("木因身份中心");
        assertThat(synced.getSpec().getDescription()).isEqualTo("使用木因账号登录");
        assertThat(synced.getSpec().getLogo())
            .isEqualTo("https://auth.muyin.site/upload/auth-logo.png");
        assertThat(synced.getSpec().getWebsite()).isEqualTo("https://auth.muyin.site");
        assertThat(synced.getSpec().getAuthenticationUrl())
            .isEqualTo(OAuthEndpointPaths.CLIENT_LOGIN);
        assertThat(synced.getSpec().getAuthType()).isEqualTo(AuthProvider.AuthType.OAUTH2);
        assertThat(synced.getMetadata().getLabels())
            .containsEntry(AuthProvider.AUTH_BINDING_LABEL, "false");
    }

    @Test
    void skipsSyncOutsideClientMode() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerMetadataClient = mock(CenterMetadataClient.class);
        var extensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuthProviderMetadataSyncService(settingFetcher, centerMetadataClient,
            extensionClient);
        var setting = new SsoGeneralSetting();
        setting.setMode("center");

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));

        var result = service.syncOnceWithRX().blockOptional();

        assertThat(result).isEmpty();
        verifyNoInteractions(centerMetadataClient, extensionClient);
    }

    @Test
    void keepsLocalAuthProviderWhenCenterMetadataRequestFails() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var centerMetadataClient = mock(CenterMetadataClient.class);
        var extensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoAuthProviderMetadataSyncService(settingFetcher, centerMetadataClient,
            extensionClient);
        var setting = new SsoGeneralSetting();
        setting.setMode("client");
        setting.setCenterUrl("https://auth.muyin.site");

        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        when(centerMetadataClient.getMetadata("https://auth.muyin.site"))
            .thenReturn(Mono.error(new IllegalStateException("center unavailable")));

        var result = service.syncOnceWithRX().blockOptional();

        assertThat(result).isEmpty();
        verifyNoInteractions(extensionClient);
    }

    private static AuthProvider authProvider() {
        var provider = new AuthProvider();
        var metadata = new Metadata();
        metadata.setName("muyin-sso");
        metadata.setLabels(Map.of());
        provider.setMetadata(metadata);
        var spec = new AuthProvider.AuthProviderSpec();
        spec.setDisplayName("统一身份认证");
        spec.setDescription("使用身份中心账号登录当前站点。");
        spec.setLogo("/plugins/sso/assets/static/logo.svg");
        spec.setWebsite("https://blog.muyin.site");
        spec.setAuthenticationUrl(OAuthEndpointPaths.CLIENT_LOGIN);
        provider.setSpec(spec);
        return provider;
    }
}
