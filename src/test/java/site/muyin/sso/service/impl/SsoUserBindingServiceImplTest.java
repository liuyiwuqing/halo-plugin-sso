package site.muyin.sso.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.userbinding.SsoUserBindingName;

class SsoUserBindingServiceImplTest {

    @Test
    void listsBindingsForConsole() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoUserBindingServiceImpl(reactiveExtensionClient);
        var binding = new SsoUserBinding()
            .setSubject("user-001")
            .setEmail("lywq@example.com")
            .setLocalUsername("lywq");

        when(reactiveExtensionClient.listAll(eq(SsoUserBinding.class), any(ListOptions.class),
            any(Sort.class)))
            .thenReturn(Flux.just(binding));

        var bindings = service.listAllWithRX().collectList().block();

        assertThat(bindings).containsExactly(binding);
    }

    @Test
    void createsBindingForFirstCenterLogin() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoUserBindingServiceImpl(reactiveExtensionClient);
        var bindingName = SsoUserBindingName.fromSubject("user-001");

        when(reactiveExtensionClient.fetch(SsoUserBinding.class, bindingName))
            .thenReturn(Mono.empty());
        when(reactiveExtensionClient.create(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var binding = service.bindOrUpdateWithRX(OAuthUserInfoResponse.builder()
            .sub("user-001")
            .preferredUsername("lywq")
            .email("lywq@example.com")
            .name("Lywq")
            .picture("https://example.com/avatar.png")
            .build()).block();

        assertThat(binding.getMetadata().getName()).isEqualTo(bindingName);
        assertThat(binding.getSubject()).isEqualTo("user-001");
        assertThat(binding.getEmail()).isEqualTo("lywq@example.com");
        assertThat(binding.getLocalUsername())
            .startsWith("sso-")
            .isNotEqualTo("lywq");
        assertThat(binding.getDisplayName()).isEqualTo("Lywq");
        assertThat(binding.getAvatar()).isEqualTo("https://example.com/avatar.png");
        assertThat(binding.getBoundAt()).isNotNull();
        assertThat(binding.getLastLoginAt()).isNotNull();
    }

    @Test
    void keepsBoundLocalUsernameWhenCenterUsernameChanges() {
        var reactiveExtensionClient = mock(ReactiveExtensionClient.class);
        var service = new SsoUserBindingServiceImpl(reactiveExtensionClient);
        var bindingName = SsoUserBindingName.fromSubject("user-001");
        var existing = new SsoUserBinding()
            .setSubject("user-001")
            .setEmail("old@example.com")
            .setLocalUsername("existing-local-user");

        when(reactiveExtensionClient.fetch(SsoUserBinding.class, bindingName))
            .thenReturn(Mono.just(existing));
        when(reactiveExtensionClient.update(any(Unstructured.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Unstructured.class)));

        var binding = service.bindOrUpdateWithRX(OAuthUserInfoResponse.builder()
            .sub("user-001")
            .preferredUsername("renamed-center-user")
            .email("new@example.com")
            .build()).block();

        assertThat(binding.getLocalUsername()).isEqualTo("existing-local-user");
        assertThat(binding.getEmail()).isEqualTo("new@example.com");
    }
}
