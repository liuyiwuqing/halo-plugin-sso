package site.muyin.sso.service.impl;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemSetting;
import site.muyin.sso.core.SsoConstants;
import site.muyin.sso.model.SsoAuthProviderMetadata;
import site.muyin.sso.service.SsoCenterMetadataService;

@Service
public class HaloSsoCenterMetadataService implements SsoCenterMetadataService {

    private final ReactiveExtensionClient reactiveExtensionClient;

    public HaloSsoCenterMetadataService(ReactiveExtensionClient reactiveExtensionClient) {
        this.reactiveExtensionClient = reactiveExtensionClient;
    }

    @Override
    public Mono<SsoAuthProviderMetadata> getMetadata(String fallbackBaseUrl) {
        return systemConfigData()
            .mapNotNull(data -> SystemSetting.get(data, SystemSetting.Basic.GROUP,
                SystemSetting.Basic.class))
            .defaultIfEmpty(new SystemSetting.Basic())
            .map(basic -> toMetadata(basic, fallbackBaseUrl));
    }

    private Mono<Map<String, String>> systemConfigData() {
        return Mono.zip(
            configData(SystemSetting.SYSTEM_CONFIG_DEFAULT),
            configData(SystemSetting.SYSTEM_CONFIG),
            (defaultData, overrideData) -> {
                var merged = new LinkedHashMap<>(defaultData);
                merged.putAll(overrideData);
                return Map.copyOf(merged);
            }
        );
    }

    private Mono<Map<String, String>> configData(String name) {
        return Mono.defer(() -> reactiveExtensionClient.fetch(ConfigMap.class, name))
            .map(HaloSsoCenterMetadataService::dataOrEmpty)
            .defaultIfEmpty(Map.of());
    }

    private static SsoAuthProviderMetadata toMetadata(SystemSetting.Basic basic,
        String fallbackBaseUrl) {
        var website = firstText(basic.getExternalUrl(), fallbackBaseUrl);
        var logo = absoluteUrl(firstText(basic.getLogo(), basic.getFavicon(),
            SsoConstants.AUTH_PROVIDER_LOGO), website);
        var title = firstText(basic.getTitle(), SsoConstants.AUTH_PROVIDER_DISPLAY_NAME);
        var description = SsoConstants.AUTH_PROVIDER_DESCRIPTION.replace("身份中心", title);
        return new SsoAuthProviderMetadata(
            title,
            description,
            logo,
            website
        );
    }

    private static Map<String, String> dataOrEmpty(ConfigMap configMap) {
        return configMap == null || configMap.getData() == null ? Map.of() : configMap.getData();
    }

    private static String absoluteUrl(String url, String baseUrl) {
        if (!hasText(url) || isAbsoluteUrl(url) || !hasText(baseUrl)) {
            return url;
        }
        try {
            return URI.create(baseUrl.trim()).resolve(url.trim()).toString();
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static boolean isAbsoluteUrl(String url) {
        var trimmed = url.trim();
        return trimmed.startsWith("http://")
            || trimmed.startsWith("https://")
            || trimmed.startsWith("//")
            || trimmed.startsWith("data:");
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
