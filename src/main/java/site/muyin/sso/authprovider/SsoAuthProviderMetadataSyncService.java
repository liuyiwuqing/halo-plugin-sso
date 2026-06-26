package site.muyin.sso.authprovider;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.AuthProvider;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.center.CenterMetadataClient;
import site.muyin.sso.core.SsoConstants;
import site.muyin.sso.model.SsoAuthProviderMetadata;
import site.muyin.sso.oauth.OAuthEndpointPaths;
import site.muyin.sso.setting.SsoGeneralSetting;

@Slf4j
@Component
public class SsoAuthProviderMetadataSyncService {

    private static final long INITIAL_DELAY_MILLIS = 60 * 1000L;
    private static final long FIXED_DELAY_MILLIS = 30 * 60 * 1000L;

    private final ReactiveSettingFetcher settingFetcher;
    private final CenterMetadataClient centerMetadataClient;
    private final ReactiveExtensionClient reactiveExtensionClient;

    public SsoAuthProviderMetadataSyncService(ReactiveSettingFetcher settingFetcher,
        CenterMetadataClient centerMetadataClient, ReactiveExtensionClient reactiveExtensionClient) {
        this.settingFetcher = settingFetcher;
        this.centerMetadataClient = centerMetadataClient;
        this.reactiveExtensionClient = reactiveExtensionClient;
    }

    @Scheduled(initialDelay = INITIAL_DELAY_MILLIS, fixedDelay = FIXED_DELAY_MILLIS)
    public void syncScheduled() {
        syncOnceWithRX()
            .doOnNext(provider -> log.info("Synced SSO AuthProvider metadata from center site."))
            .doOnError(error -> log.warn("Failed to sync SSO AuthProvider metadata.", error))
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    public Mono<AuthProvider> syncOnceWithRX() {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .filter(SsoAuthProviderMetadataSyncService::isClientModeWithCenterUrl)
            .flatMap(setting -> centerMetadataClient.getMetadata(setting.getCenterUrl().trim())
                .doOnError(error -> log.warn("Failed to fetch SSO center metadata.", error))
                .onErrorResume(error -> Mono.empty()))
            .flatMap(this::syncAuthProvider);
    }

    private Mono<AuthProvider> syncAuthProvider(SsoAuthProviderMetadata metadata) {
        return reactiveExtensionClient.fetch(AuthProvider.class, SsoConstants.AUTH_PROVIDER_NAME)
            .map(provider -> applyMetadata(provider, metadata))
            .flatMap(reactiveExtensionClient::update);
    }

    private static AuthProvider applyMetadata(AuthProvider provider,
        SsoAuthProviderMetadata metadata) {
        ensureMetadata(provider);
        ensureAuthProviderLabel(provider);

        var spec = provider.getSpec();
        if (spec == null) {
            spec = new AuthProvider.AuthProviderSpec();
            provider.setSpec(spec);
        }
        spec.setDisplayName(firstText(metadata.displayName(),
            SsoConstants.AUTH_PROVIDER_DISPLAY_NAME));
        spec.setDescription(firstText(metadata.description(),
            SsoConstants.AUTH_PROVIDER_DESCRIPTION));
        spec.setLogo(firstText(metadata.logo(), SsoConstants.AUTH_PROVIDER_LOGO));
        spec.setWebsite(firstText(metadata.website(), spec.getWebsite()));
        spec.setAuthenticationUrl(OAuthEndpointPaths.CLIENT_LOGIN);
        spec.setAuthType(AuthProvider.AuthType.OAUTH2);
        return provider;
    }

    private static void ensureMetadata(AuthProvider provider) {
        if (provider.getMetadata() == null) {
            provider.setMetadata(new Metadata());
        }
        if (!hasText(provider.getMetadata().getName())) {
            provider.getMetadata().setName(SsoConstants.AUTH_PROVIDER_NAME);
        }
    }

    private static void ensureAuthProviderLabel(AuthProvider provider) {
        Map<String, String> labels = provider.getMetadata().getLabels();
        var mutableLabels = new LinkedHashMap<>(labels == null ? Map.of() : labels);
        mutableLabels.put(AuthProvider.AUTH_BINDING_LABEL, "false");
        provider.getMetadata().setLabels(mutableLabels);
    }

    private static boolean isClientModeWithCenterUrl(SsoGeneralSetting setting) {
        return setting != null
            && "client".equals(setting.getMode())
            && hasText(setting.getCenterUrl());
    }

    private static String firstText(String... values) {
        for (var value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
