package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.support.GenericApplicationContext;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.sso.client.ClientIdGenerator;
import site.muyin.sso.client.ClientSecretGenerator;
import site.muyin.sso.client.ClientSecretHasher;
import site.muyin.sso.client.ClientSecretVerificationBusyException;
import site.muyin.sso.client.SsoClientName;
import site.muyin.sso.model.CreateSsoClientRequest;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.setting.SsoGeneralSetting;

class SsoClientServiceImplTest {

    private GenericApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void springCanCreateServiceWithClientIdGeneratorBean() {
        context = new GenericApplicationContext();
        context.registerBean(ReactiveExtensionClient.class, () -> mock(ReactiveExtensionClient.class));
        context.registerBean(ClientIdGenerator.class, ClientIdGenerator::new);
        context.registerBean(ClientSecretGenerator.class, ClientSecretGenerator::new);
        context.registerBean(ClientSecretHasher.class, ClientSecretHasher::new);
        context.registerBean(ReactiveSettingFetcher.class, () -> mock(ReactiveSettingFetcher.class));
        context.registerBean(SsoClientServiceImpl.class);

        context.refresh();

        assertThat(context.getBean(SsoClientServiceImpl.class)).isNotNull();
        assertThat(context.getBeanProvider(ClientIdGenerator.class).getIfAvailable()).isNotNull();
    }

    @Test
    void publicSpringConstructorIsExplicitlyAutowired() throws NoSuchMethodException {
        var constructor = SsoClientServiceImpl.class.getConstructor(
            ReactiveExtensionClient.class,
            ClientSecretGenerator.class,
            ClientSecretHasher.class,
            ReactiveSettingFetcher.class,
            ClientIdGenerator.class
        );

        assertThat(constructor.getAnnotation(Autowired.class)).isNotNull();
    }

    @Test
    void clientIdGeneratorIsRegisteredAsSpringComponent() {
        assertThat(ClientIdGenerator.class.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    void createsClientWithGeneratedClientIdAndReturnsClientCredentials() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = service(reactiveExtensionClient, true,
            new FixedClientIdGenerator("sso-generated-client"));
        var request = new CreateSsoClientRequest();
        request.setDisplayName("B 站");
        request.setSiteUrl("https://b.example.com");
        request.setRedirectUris(List.of("https://b.example.com/apis/public.sso.muyin.site/"
            + "v1alpha1/client/callback"));

        when(reactiveExtensionClient.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var response = service.createWithRX(request).block();

        assertThat(response.getClient().getClientId()).isEqualTo("sso-generated-client");
        assertThat(response.getClient().getDisplayName()).isEqualTo("B 站");
        assertThat(response.getClient().getRedirectUris())
            .containsExactly("https://b.example.com/apis/public.sso.muyin.site/"
                + "v1alpha1/client/callback");
        assertThat(response.getClientSecret()).isNotBlank();
    }

    @Test
    void updatesClientWithoutRegeneratingSecretOrClearingExistingFields() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = service(reactiveExtensionClient, true);
        var clientName = SsoClientName.fromClientId("site-b");
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        var oldUpdatedAt = Instant.parse("2026-01-02T00:00:00Z");
        var existing = new SsoClient()
            .setClientId("site-b")
            .setClientSecretHash("existing-secret-hash")
            .setDisplayName("B 站")
            .setSiteUrl("https://b.example.com")
            .setRedirectUris(List.of("https://b.example.com/plugins/sso/callback"))
            .setEnabled(true)
            .setCreatedAt(createdAt)
            .setUpdatedAt(oldUpdatedAt);
        existing.setMetadata(new Metadata());
        existing.getMetadata().setName(clientName);

        when(reactiveExtensionClient.fetch(SsoClient.class, clientName))
            .thenReturn(Mono.just(existing));
        when(reactiveExtensionClient.update(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var updated = service.updateWithRX(new SsoClient()
            .setClientId("site-b")
            .setEnabled(false)).block();

        assertThat(updated.getMetadata().getName()).isEqualTo(clientName);
        assertThat(updated.getClientSecretHash()).isEqualTo("existing-secret-hash");
        assertThat(updated.getDisplayName()).isEqualTo("B 站");
        assertThat(updated.getSiteUrl()).isEqualTo("https://b.example.com");
        assertThat(updated.getRedirectUris())
            .containsExactly("https://b.example.com/plugins/sso/callback");
        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(oldUpdatedAt);
    }

    @Test
    void updatesClientEditableFieldsAndNormalizesRedirectUris() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = service(reactiveExtensionClient, true);
        var clientName = SsoClientName.fromClientId("site-b");
        var existing = new SsoClient()
            .setClientId("site-b")
            .setClientSecretHash("existing-secret-hash")
            .setDisplayName("B 站")
            .setSiteUrl("https://b.example.com")
            .setRedirectUris(List.of("https://b.example.com/plugins/sso/callback"))
            .setEnabled(false)
            .setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        existing.setMetadata(new Metadata());
        existing.getMetadata().setName(clientName);

        when(reactiveExtensionClient.fetch(SsoClient.class, clientName))
            .thenReturn(Mono.just(existing));
        when(reactiveExtensionClient.update(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var updated = service.updateWithRX(new SsoClient()
            .setClientId("site-b")
            .setDisplayName("B 站新版")
            .setSiteUrl("https://new-b.example.com")
            .setRedirectUris(List.of(
                "https://new-b.example.com/plugins/sso/callback",
                "https://new-b.example.com/plugins/sso/callback"
            ))
            .setEnabled(true)).block();

        assertThat(updated.getDisplayName()).isEqualTo("B 站新版");
        assertThat(updated.getSiteUrl()).isEqualTo("https://new-b.example.com");
        assertThat(updated.getRedirectUris())
            .containsExactly("https://new-b.example.com/plugins/sso/callback");
        assertThat(updated.getEnabled()).isTrue();
        assertThat(updated.getClientSecretHash()).isEqualTo("existing-secret-hash");
    }

    @Test
    void deletesClientByClientId() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = service(reactiveExtensionClient, true);
        var clientName = SsoClientName.fromClientId("site-b");
        var existing = new SsoClient()
            .setClientId("site-b")
            .setClientSecretHash("existing-secret-hash")
            .setDisplayName("B 站")
            .setSiteUrl("https://b.example.com")
            .setRedirectUris(List.of("https://b.example.com/plugins/sso/callback"))
            .setEnabled(true);
        existing.setMetadata(new Metadata());
        existing.getMetadata().setName(clientName);

        when(reactiveExtensionClient.fetch(SsoClient.class, clientName))
            .thenReturn(Mono.just(existing));
        when(reactiveExtensionClient.delete(any(SsoClient.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, SsoClient.class)));

        var deleted = service.deleteWithRX("site-b").block();

        assertThat(deleted).isSameAs(existing);
        verify(reactiveExtensionClient).delete(eq(existing));
    }

    @Test
    void rejectsLocalhostHttpRedirectWhenSettingDisallowsIt() {
        var service = service(mock(ReactiveExtensionClient.class), false);

        var request = new CreateSsoClientRequest();
        request.setDisplayName("B 站");
        request.setSiteUrl("http://localhost:8091");
        request.setRedirectUris(List.of("http://localhost:8091/apis/public.sso.muyin.site/"
            + "v1alpha1/client/callback"));

        assertThatThrownBy(() -> service.createWithRX(request).block())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTPS");
    }

    @Test
    void verifiesClientSecretOutsideTheCallingThread() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var clientSecretHasher = mock(ClientSecretHasher.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var service = new SsoClientServiceImpl(
            reactiveExtensionClient,
            new ClientSecretGenerator(),
            clientSecretHasher,
            settingFetcher,
            new ClientIdGenerator()
        );
        var clientId = "site-b";
        var client = new SsoClient()
            .setClientId(clientId)
            .setClientSecretHash("stored-hash")
            .setEnabled(true);
        var verificationThread = new AtomicReference<Thread>();

        when(reactiveExtensionClient.fetch(SsoClient.class,
            SsoClientName.fromClientId(clientId))).thenReturn(Mono.just(client));
        when(clientSecretHasher.matches("candidate-secret", "stored-hash"))
            .thenAnswer(invocation -> {
                verificationThread.set(Thread.currentThread());
                return true;
            });

        var callingThread = Thread.currentThread();
        var valid = service.verifySecretWithRX(clientId, "candidate-secret").block();

        assertThat(valid).isTrue();
        assertThat(verificationThread.get()).isNotSameAs(callingThread);
    }

    @Test
    void rejectsConcurrentSecretVerificationAndReleasesPermit() throws Exception {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var clientSecretHasher = mock(ClientSecretHasher.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var service = new SsoClientServiceImpl(
            reactiveExtensionClient,
            new ClientSecretGenerator(),
            clientSecretHasher,
            settingFetcher,
            new ClientIdGenerator(),
            1
        );
        var clientId = "site-b";
        var client = new SsoClient()
            .setClientId(clientId)
            .setClientSecretHash("stored-hash")
            .setEnabled(true);
        var verificationStarted = new CountDownLatch(1);
        var allowVerificationToFinish = new CountDownLatch(1);
        var invocationCount = new AtomicInteger();

        when(reactiveExtensionClient.fetch(SsoClient.class,
            SsoClientName.fromClientId(clientId))).thenReturn(Mono.just(client));
        when(clientSecretHasher.matches("candidate-secret", "stored-hash"))
            .thenAnswer(invocation -> {
                if (invocationCount.incrementAndGet() == 1) {
                    verificationStarted.countDown();
                    if (!allowVerificationToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release secret verification");
                    }
                }
                return true;
            });

        var firstVerification = service.verifySecretWithRX(clientId, "candidate-secret")
            .toFuture();
        assertThat(verificationStarted.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> service.verifySecretWithRX(clientId, "candidate-secret")
                .block())
                .isInstanceOf(ClientSecretVerificationBusyException.class);
        } finally {
            allowVerificationToFinish.countDown();
        }

        assertThat(firstVerification.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.verifySecretWithRX(clientId, "candidate-secret").block()).isTrue();
        assertThat(invocationCount).hasValue(2);
    }

    private static SsoClientServiceImpl service(ReactiveExtensionClient reactiveExtensionClient,
        boolean allowHttpForLocalhost) {
        return service(reactiveExtensionClient, allowHttpForLocalhost, new ClientIdGenerator());
    }

    private static SsoClientServiceImpl service(ReactiveExtensionClient reactiveExtensionClient,
        boolean allowHttpForLocalhost, ClientIdGenerator clientIdGenerator) {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var setting = new SsoGeneralSetting();
        setting.setAllowHttpForLocalhost(allowHttpForLocalhost);
        when(settingFetcher.fetch("general", SsoGeneralSetting.class))
            .thenReturn(Mono.just(setting));
        return new SsoClientServiceImpl(
            reactiveExtensionClient,
            new ClientSecretGenerator(),
            new ClientSecretHasher(),
            settingFetcher,
            clientIdGenerator
        );
    }

    private static class FixedClientIdGenerator extends ClientIdGenerator {

        private final String clientId;

        FixedClientIdGenerator(String clientId) {
            this.clientId = clientId;
        }

        @Override
        public String generate() {
            return clientId;
        }
    }
}
