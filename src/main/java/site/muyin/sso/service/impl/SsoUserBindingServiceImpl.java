package site.muyin.sso.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.sso.model.oauth.OAuthUserInfoResponse;
import site.muyin.sso.scheme.SsoUserBinding;
import site.muyin.sso.service.SsoUserBindingService;
import site.muyin.sso.userbinding.SsoLocalUsername;
import site.muyin.sso.userbinding.SsoUserBindingName;

@Service
@RequiredArgsConstructor
public class SsoUserBindingServiceImpl implements SsoUserBindingService {

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final ObjectMapper objectMapper = Unstructured.OBJECT_MAPPER;

    @Override
    public Flux<SsoUserBinding> listAllWithRX() {
        return reactiveExtensionClient.listAll(
            SsoUserBinding.class,
            ListOptions.builder().fieldQuery(ExtensionUtil.notDeleting()).build(),
            Sort.by(Sort.Order.desc("lastLoginAt"), Sort.Order.desc("boundAt"))
        );
    }

    @Override
    public Mono<SsoUserBinding> bindOrUpdateWithRX(OAuthUserInfoResponse userInfo) {
        requireText(userInfo.getSub(), "sub");
        requireText(userInfo.getEmail(), "email");

        var bindingName = SsoUserBindingName.fromSubject(userInfo.getSub());
        return reactiveExtensionClient.fetch(SsoUserBinding.class, bindingName)
            .flatMap(existing -> update(buildUpdateBinding(existing, userInfo)))
            .switchIfEmpty(Mono.defer(() -> create(buildCreateBinding(bindingName, userInfo))));
    }

    private SsoUserBinding buildCreateBinding(String bindingName, OAuthUserInfoResponse userInfo) {
        var now = Instant.now();
        var binding = new SsoUserBinding()
            .setSubject(userInfo.getSub())
            .setEmail(userInfo.getEmail())
            .setLocalUsername(SsoLocalUsername.fromSubject(userInfo.getSub()))
            .setDisplayName(userInfo.getName())
            .setAvatar(userInfo.getPicture())
            .setBoundAt(now)
            .setLastLoginAt(now);

        binding.setMetadata(new Metadata());
        binding.getMetadata().setName(bindingName);
        binding.getMetadata().setGenerateName(SsoUserBinding.NAME_PREFIX);
        binding.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(binding));
        binding.getMetadata().setLabels(MetadataUtil.nullSafeLabels(binding));
        return binding;
    }

    private SsoUserBinding buildUpdateBinding(SsoUserBinding binding,
        OAuthUserInfoResponse userInfo) {
        if (binding.getMetadata() == null) {
            binding.setMetadata(new Metadata());
        }
        binding.setEmail(userInfo.getEmail());
        binding.setDisplayName(userInfo.getName());
        binding.setAvatar(userInfo.getPicture());
        binding.setLastLoginAt(Instant.now());
        binding.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(binding));
        binding.getMetadata().setLabels(MetadataUtil.nullSafeLabels(binding));
        return binding;
    }

    private Mono<SsoUserBinding> create(SsoUserBinding binding) {
        Map<?, ?> extensionMap = objectMapper.convertValue(binding, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.create(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoUserBinding.class));
    }

    private Mono<SsoUserBinding> update(SsoUserBinding binding) {
        Map<?, ?> extensionMap = objectMapper.convertValue(binding, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.update(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoUserBinding.class));
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
