package site.muyin.sso.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import site.muyin.sso.rolemapping.SsoRoleMappingName;
import site.muyin.sso.scheme.SsoRoleMapping;
import site.muyin.sso.service.SsoRoleMappingService;

@Service
public class SsoRoleMappingServiceImpl implements SsoRoleMappingService {

    private final ReactiveExtensionClient reactiveExtensionClient;
    private final ObjectMapper objectMapper = Unstructured.OBJECT_MAPPER;

    public SsoRoleMappingServiceImpl(ReactiveExtensionClient reactiveExtensionClient) {
        this.reactiveExtensionClient = reactiveExtensionClient;
    }

    @Override
    public Flux<SsoRoleMapping> listAllWithRX() {
        return reactiveExtensionClient.listAll(
            SsoRoleMapping.class,
            ListOptions.builder().fieldQuery(ExtensionUtil.notDeleting()).build(),
            roleMappingSort()
        );
    }

    @Override
    public Mono<SsoRoleMapping> getByCenterRoleWithRX(String centerRole) {
        return reactiveExtensionClient.fetch(
            SsoRoleMapping.class,
            SsoRoleMappingName.fromCenterRole(centerRole)
        );
    }

    @Override
    public Mono<SsoRoleMapping> createWithRX(SsoRoleMapping mapping) {
        return create(buildCreateMapping(mapping));
    }

    @Override
    public Mono<SsoRoleMapping> updateWithRX(SsoRoleMapping mapping) {
        requireText(mapping.getCenterRole(), "centerRole");
        return getByCenterRoleWithRX(mapping.getCenterRole())
            .flatMap(existing -> update(buildUpdateMapping(mapping, existing)));
    }

    @Override
    public Mono<Set<String>> resolveLocalRoles(Set<String> centerRoles, String defaultRole) {
        var normalizedCenterRoles = normalizeRoles(centerRoles);
        var fallbackRole = normalizeRole(defaultRole);
        return listAllWithRX()
            .filter(mapping -> Boolean.TRUE.equals(mapping.getEnabled()))
            .filter(mapping -> {
                var centerRole = normalizeRole(mapping.getCenterRole());
                return centerRole != null && normalizedCenterRoles.contains(centerRole);
            })
            .map(mapping -> normalizeRole(mapping.getLocalRole()))
            .filter(role -> role != null)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new))
            .map(mappedRoles -> {
                if (mappedRoles.isEmpty() && fallbackRole != null) {
                    mappedRoles.add(fallbackRole);
                }
                return java.util.Collections.unmodifiableSet(mappedRoles);
            });
    }

    private SsoRoleMapping buildCreateMapping(SsoRoleMapping mapping) {
        requireText(mapping.getCenterRole(), "centerRole");
        requireText(mapping.getLocalRole(), "localRole");

        var normalizedCenterRole = mapping.getCenterRole().trim();
        var entity = new SsoRoleMapping()
            .setCenterRole(normalizedCenterRole)
            .setLocalRole(mapping.getLocalRole().trim())
            .setEnabled(!Boolean.FALSE.equals(mapping.getEnabled()))
            .setSort(mapping.getSort() == null ? 0 : mapping.getSort());

        entity.setMetadata(new Metadata());
        entity.getMetadata().setName(SsoRoleMappingName.fromCenterRole(normalizedCenterRole));
        entity.getMetadata().setGenerateName(SsoRoleMapping.NAME_PREFIX);
        entity.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(entity));
        entity.getMetadata().setLabels(MetadataUtil.nullSafeLabels(entity));
        return entity;
    }

    private SsoRoleMapping buildUpdateMapping(SsoRoleMapping newMapping,
        SsoRoleMapping oldMapping) {
        if (oldMapping.getMetadata() == null) {
            oldMapping.setMetadata(new Metadata());
        }
        if (oldMapping.getMetadata().getName() == null) {
            oldMapping.getMetadata()
                .setName(SsoRoleMappingName.fromCenterRole(newMapping.getCenterRole()));
        }
        oldMapping.setCenterRole(newMapping.getCenterRole().trim());
        if (newMapping.getLocalRole() != null) {
            requireText(newMapping.getLocalRole(), "localRole");
            oldMapping.setLocalRole(newMapping.getLocalRole().trim());
        }
        if (newMapping.getEnabled() != null) {
            oldMapping.setEnabled(newMapping.getEnabled());
        }
        if (newMapping.getSort() != null) {
            oldMapping.setSort(newMapping.getSort());
        }
        oldMapping.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(oldMapping));
        oldMapping.getMetadata().setLabels(MetadataUtil.nullSafeLabels(oldMapping));
        return oldMapping;
    }

    private Mono<SsoRoleMapping> create(SsoRoleMapping mapping) {
        Map<?, ?> extensionMap = objectMapper.convertValue(mapping, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.create(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoRoleMapping.class));
    }

    private Mono<SsoRoleMapping> update(SsoRoleMapping mapping) {
        Map<?, ?> extensionMap = objectMapper.convertValue(mapping, Map.class);
        var extension = new Unstructured(extensionMap);
        return reactiveExtensionClient.update(extension)
            .map(unstructured -> objectMapper.convertValue(unstructured, SsoRoleMapping.class));
    }

    private static Sort roleMappingSort() {
        return Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("metadata.name"));
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream()
            .filter(role -> role != null && !role.isBlank())
            .map(String::trim)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeRole(String role) {
        return role == null || role.isBlank() ? null : role.trim();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
