package site.muyin.sso.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.client.ClientIdGenerator;
import site.muyin.sso.client.ClientSecretGenerator;
import site.muyin.sso.client.ClientSecretHasher;
import site.muyin.sso.client.ClientSecretVerificationBusyException;
import site.muyin.sso.client.RedirectUriPolicy;
import site.muyin.sso.client.SsoClientException;
import site.muyin.sso.client.SsoClientName;
import site.muyin.sso.model.CreateSsoClientRequest;
import site.muyin.sso.model.CreateSsoClientResponse;
import site.muyin.sso.model.SsoClientView;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.service.SsoClientService;
import site.muyin.sso.setting.SsoGeneralSetting;

@Service
public class SsoClientServiceImpl implements SsoClientService {

    private static final int DEFAULT_MAX_CONCURRENT_SECRET_VERIFICATIONS = 4;

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final ClientIdGenerator clientIdGenerator;
    private final ClientSecretGenerator clientSecretGenerator;
    private final ClientSecretHasher clientSecretHasher;
    private final ReactiveSettingFetcher settingFetcher;
    private final Semaphore secretVerificationPermits;
    private final ObjectMapper objectMapper = Unstructured.OBJECT_MAPPER;

    @Autowired
    public SsoClientServiceImpl(ReactiveExtensionClient reactiveExtensionClient,
        ClientSecretGenerator clientSecretGenerator, ClientSecretHasher clientSecretHasher,
        ReactiveSettingFetcher settingFetcher, ClientIdGenerator clientIdGenerator) {
        this(reactiveExtensionClient, clientSecretGenerator, clientSecretHasher, settingFetcher,
            clientIdGenerator, DEFAULT_MAX_CONCURRENT_SECRET_VERIFICATIONS);
    }

    SsoClientServiceImpl(ReactiveExtensionClient reactiveExtensionClient,
        ClientSecretGenerator clientSecretGenerator, ClientSecretHasher clientSecretHasher,
        ReactiveSettingFetcher settingFetcher, ClientIdGenerator clientIdGenerator,
        int maxConcurrentSecretVerifications) {
        if (maxConcurrentSecretVerifications < 1) {
            throw new IllegalArgumentException("maxConcurrentSecretVerifications must be positive");
        }
        this.reactiveExtensionClient = reactiveExtensionClient;
        this.clientSecretGenerator = clientSecretGenerator;
        this.clientSecretHasher = clientSecretHasher;
        this.settingFetcher = settingFetcher;
        this.clientIdGenerator = clientIdGenerator;
        this.secretVerificationPermits = new Semaphore(maxConcurrentSecretVerifications);
    }

    @Override
    public Mono<CreateSsoClientResponse> createWithRX(CreateSsoClientRequest request) {
        var clientId = clientIdGenerator.generate();
        var clientSecret = clientSecretGenerator.generate();
        return allowHttpForLocalhost()
            .map(allowHttpForLocalhost -> buildCreateClient(request, clientId, clientSecret,
                allowHttpForLocalhost))
            .flatMap(this::create)
            .map(created -> CreateSsoClientResponse.builder()
                .client(SsoClientView.from(created))
                .clientSecret(clientSecret)
                .build());
    }

    @Override
    public Mono<SsoClient> updateWithRX(SsoClient client) {
        return getByClientIdWithRX(client.getClientId())
            .zipWith(allowHttpForLocalhost())
            .flatMap(tuple -> update(buildUpdateClient(client, tuple.getT1(), tuple.getT2())));
    }

    @Override
    public Mono<SsoClient> deleteWithRX(String clientId) {
        return getByClientIdWithRX(clientId)
            .flatMap(reactiveExtensionClient::delete);
    }

    @Override
    public Mono<SsoClient> getByClientIdWithRX(String clientId) {
        return reactiveExtensionClient.fetch(SsoClient.class, SsoClientName.fromClientId(clientId));
    }

    @Override
    public Flux<SsoClient> listAllWithRX() {
        return reactiveExtensionClient.listAll(
            SsoClient.class,
            ListOptions.builder().fieldQuery(ExtensionUtil.notDeleting()).build(),
            Sort.by(Sort.Order.desc("metadata.creationTimestamp"))
        );
    }

    @Override
    public Mono<Boolean> verifySecretWithRX(String clientId, String clientSecret) {
        return getByClientIdWithRX(clientId)
            .filter(client -> Boolean.TRUE.equals(client.getEnabled()))
            .flatMap(client -> verifySecret(clientSecret, client.getClientSecretHash()))
            .defaultIfEmpty(false);
    }

    private Mono<Boolean> verifySecret(String clientSecret, String clientSecretHash) {
        return Mono.using(
            () -> {
                if (!secretVerificationPermits.tryAcquire()) {
                    throw new ClientSecretVerificationBusyException();
                }
                return secretVerificationPermits;
            },
            permits -> Mono.fromCallable(() -> clientSecretHasher.matches(
                    clientSecret, clientSecretHash))
                .subscribeOn(Schedulers.boundedElastic()),
            Semaphore::release,
            true
        );
    }

    @Override
    public Mono<SsoClient> requireAuthorizedClientWithRX(String clientId, String redirectUri) {
        return getByClientIdWithRX(clientId)
            .switchIfEmpty(Mono.error(new SsoClientException("SSO client not found")))
            .zipWith(allowHttpForLocalhost())
            .flatMap(tuple -> {
                var client = tuple.getT1();
                var allowHttpForLocalhost = tuple.getT2();
                if (!Boolean.TRUE.equals(client.getEnabled())) {
                    return Mono.error(new SsoClientException("SSO client is disabled"));
                }
                if (!RedirectUriPolicy.isAllowed(client.getRedirectUris(), redirectUri,
                    allowHttpForLocalhost)) {
                    return Mono.error(new SsoClientException("redirect_uri is not allowed"));
                }
                return Mono.just(client);
            });
    }

    private SsoClient buildCreateClient(CreateSsoClientRequest request, String clientId,
        String clientSecret,
        boolean allowHttpForLocalhost) {
        requireText(clientId, "clientId");
        requireText(request.getDisplayName(), "displayName");
        requireText(request.getSiteUrl(), "siteUrl");

        var now = Instant.now();
        var client = new SsoClient()
            .setClientId(clientId)
            .setClientSecretHash(clientSecretHasher.hash(clientSecret))
            .setDisplayName(request.getDisplayName())
            .setSiteUrl(request.getSiteUrl())
            .setRedirectUris(RedirectUriPolicy.normalize(request.getRedirectUris(),
                allowHttpForLocalhost))
            .setEnabled(!Boolean.FALSE.equals(request.getEnabled()))
            .setCreatedAt(now)
            .setUpdatedAt(now);

        client.setMetadata(new Metadata());
        client.getMetadata().setName(SsoClientName.fromClientId(client.getClientId()));
        client.getMetadata().setGenerateName(SsoClient.NAME_PREFIX);
        client.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(client));
        client.getMetadata().setLabels(MetadataUtil.nullSafeLabels(client));
        return client;
    }

    private SsoClient buildUpdateClient(SsoClient newClient, SsoClient oldClient,
        boolean allowHttpForLocalhost) {
        requireText(newClient.getClientId(), "clientId");
        if (oldClient.getMetadata() == null) {
            oldClient.setMetadata(new Metadata());
        }
        if (newClient.getDisplayName() != null) {
            requireText(newClient.getDisplayName(), "displayName");
            oldClient.setDisplayName(newClient.getDisplayName().trim());
        }
        if (newClient.getSiteUrl() != null) {
            requireText(newClient.getSiteUrl(), "siteUrl");
            oldClient.setSiteUrl(newClient.getSiteUrl().trim());
        }
        if (newClient.getRedirectUris() != null && !newClient.getRedirectUris().isEmpty()) {
            oldClient.setRedirectUris(RedirectUriPolicy.normalize(newClient.getRedirectUris(),
                allowHttpForLocalhost));
        }
        if (newClient.getEnabled() != null) {
            oldClient.setEnabled(newClient.getEnabled());
        }
        oldClient.setUpdatedAt(Instant.now());
        oldClient.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(oldClient));
        oldClient.getMetadata().setLabels(MetadataUtil.nullSafeLabels(oldClient));
        return oldClient;
    }

    private Mono<SsoClient> create(SsoClient client) {
        Map<?, ?> extensionMap = objectMapper.convertValue(client, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.create(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoClient.class));
    }

    private Mono<SsoClient> update(SsoClient client) {
        Map<?, ?> extensionMap = objectMapper.convertValue(client, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.update(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoClient.class));
    }

    private Mono<Boolean> allowHttpForLocalhost() {
        return settingFetcher.fetch("general", SsoGeneralSetting.class)
            .defaultIfEmpty(new SsoGeneralSetting())
            .map(setting -> !Boolean.FALSE.equals(setting.getAllowHttpForLocalhost()));
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
