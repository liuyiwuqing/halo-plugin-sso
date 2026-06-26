package site.muyin.sso;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.extension.index.ValueIndexSpec;
import run.halo.app.plugin.PluginContext;
import site.muyin.sso.scheme.SsoAuditLog;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;
import site.muyin.sso.scheme.SsoAuthorizationCode;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.scheme.SsoRoleMapping;
import site.muyin.sso.scheme.SsoUserBinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SsoPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemeManager;

    @InjectMocks
    SsoPlugin plugin;

    @Test
    void registersAndUnregistersSsoSchemes() {
        plugin.start();
        plugin.stop();

        verify(schemeManager).register(eq(SsoClient.class), any());
        verify(schemeManager).register(eq(SsoAuthorizationCode.class), any());
        verify(schemeManager).register(eq(SsoUserBinding.class), any());
        verify(schemeManager).register(eq(SsoRoleMapping.class), any());
        verify(schemeManager).register(eq(SsoAuditLog.class), any());
        verify(schemeManager).register(eq(SsoAuditLogCleanupRecord.class), any());

        verify(schemeManager, times(6)).unregister(any(Scheme.class));
    }

    @Test
    void registersSortIndexForRoleMappingListOrder() {
        plugin.start();

        @SuppressWarnings("unchecked")
        var captor = (ArgumentCaptor<Consumer<IndexSpecs<SsoRoleMapping>>>) (ArgumentCaptor<?>)
            ArgumentCaptor.forClass(Consumer.class);
        verify(schemeManager).register(eq(SsoRoleMapping.class), captor.capture());

        var indexSpecs = new CapturingIndexSpecs<SsoRoleMapping>();
        captor.getValue().accept(indexSpecs);

        assertThat(indexSpecs.indexNames()).contains("sort");
    }

    private static class CapturingIndexSpecs<E extends Extension> implements IndexSpecs<E> {

        private final List<ValueIndexSpec<E, ?>> indexSpecs = new ArrayList<>();

        @Override
        public <K extends Comparable<K>> void add(ValueIndexSpec<E, K> indexSpec) {
            indexSpecs.add(indexSpec);
        }

        @Override
        public List<ValueIndexSpec<E, ?>> getIndexSpecs() {
            return indexSpecs;
        }

        List<String> indexNames() {
            return indexSpecs.stream()
                .map(ValueIndexSpec::getName)
                .toList();
        }
    }
}
