package site.muyin.sso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import site.muyin.sso.scheme.SsoAuditLog;
import site.muyin.sso.scheme.SsoAuditLogCleanupRecord;
import site.muyin.sso.scheme.SsoAuthorizationCode;
import site.muyin.sso.scheme.SsoClient;
import site.muyin.sso.scheme.SsoRoleMapping;
import site.muyin.sso.scheme.SsoUserBinding;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author Lywq
 * @since 1.0.0
 */
@Slf4j
@Component
public class SsoPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public SsoPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        registerSchemes();
        log.info("SSO plugin started.");
    }

    @Override
    public void stop() {
        unregisterSchemes();
        log.info("SSO plugin stopped.");
    }

    private void registerSchemes() {
        schemeManager.register(SsoClient.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SsoClient, String>single("clientId", String.class)
                .unique(true)
                .indexFunc(SsoClient::getClientId));
            indexSpecs.add(IndexSpecs.<SsoClient, Boolean>single("enabled", Boolean.class)
                .indexFunc(SsoClient::getEnabled));
            indexSpecs.add(IndexSpecs.<SsoClient, String>single("displayName", String.class)
                .indexFunc(SsoClient::getDisplayName));
        });

        schemeManager.register(SsoAuthorizationCode.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SsoAuthorizationCode, String>single("code", String.class)
                .unique(true)
                .indexFunc(SsoAuthorizationCode::getCode));
            indexSpecs.add(IndexSpecs.<SsoAuthorizationCode, String>single("clientId", String.class)
                .indexFunc(SsoAuthorizationCode::getClientId));
            indexSpecs.add(IndexSpecs.<SsoAuthorizationCode, String>single("subject", String.class)
                .indexFunc(SsoAuthorizationCode::getSubject));
            indexSpecs.add(IndexSpecs.<SsoAuthorizationCode, Boolean>single("consumed", Boolean.class)
                .indexFunc(SsoAuthorizationCode::getConsumed));
            indexSpecs.add(IndexSpecs.<SsoAuthorizationCode, java.time.Instant>single("expiresAt",
                    java.time.Instant.class)
                .indexFunc(SsoAuthorizationCode::getExpiresAt));
        });

        schemeManager.register(SsoUserBinding.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SsoUserBinding, String>single("subject", String.class)
                .unique(true)
                .indexFunc(SsoUserBinding::getSubject));
            indexSpecs.add(IndexSpecs.<SsoUserBinding, String>single("email", String.class)
                .indexFunc(SsoUserBinding::getEmail));
            indexSpecs.add(IndexSpecs.<SsoUserBinding, String>single("localUsername", String.class)
                .indexFunc(SsoUserBinding::getLocalUsername));
            indexSpecs.add(IndexSpecs.<SsoUserBinding, java.time.Instant>single("boundAt",
                    java.time.Instant.class)
                .indexFunc(SsoUserBinding::getBoundAt));
            indexSpecs.add(IndexSpecs.<SsoUserBinding, java.time.Instant>single("lastLoginAt",
                    java.time.Instant.class)
                .indexFunc(SsoUserBinding::getLastLoginAt));
        });

        schemeManager.register(SsoRoleMapping.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SsoRoleMapping, String>single("centerRole", String.class)
                .indexFunc(SsoRoleMapping::getCenterRole));
            indexSpecs.add(IndexSpecs.<SsoRoleMapping, String>single("localRole", String.class)
                .indexFunc(SsoRoleMapping::getLocalRole));
            indexSpecs.add(IndexSpecs.<SsoRoleMapping, Boolean>single("enabled", Boolean.class)
                .indexFunc(SsoRoleMapping::getEnabled));
            indexSpecs.add(IndexSpecs.<SsoRoleMapping, Integer>single("sort", Integer.class)
                .indexFunc(SsoRoleMapping::getSort));
        });

        schemeManager.register(SsoAuditLog.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SsoAuditLog, String>single("eventType", String.class)
                .indexFunc(SsoAuditLog::getEventType));
            indexSpecs.add(IndexSpecs.<SsoAuditLog, String>single("outcome", String.class)
                .indexFunc(SsoAuditLog::getOutcome));
            indexSpecs.add(IndexSpecs.<SsoAuditLog, String>single("clientId", String.class)
                .indexFunc(SsoAuditLog::getClientId));
            indexSpecs.add(IndexSpecs.<SsoAuditLog, String>single("subject", String.class)
                .indexFunc(SsoAuditLog::getSubject));
            indexSpecs.add(IndexSpecs.<SsoAuditLog, java.time.Instant>single("createdAt",
                    java.time.Instant.class)
                .indexFunc(SsoAuditLog::getCreatedAt));
        });

        schemeManager.register(SsoAuditLogCleanupRecord.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SsoAuditLogCleanupRecord, String>single("trigger",
                    String.class)
                .indexFunc(SsoAuditLogCleanupRecord::getTrigger));
            indexSpecs.add(IndexSpecs.<SsoAuditLogCleanupRecord, Boolean>single("success",
                    Boolean.class)
                .indexFunc(SsoAuditLogCleanupRecord::getSuccess));
            indexSpecs.add(IndexSpecs.<SsoAuditLogCleanupRecord, java.time.Instant>single(
                    "finishedAt", java.time.Instant.class)
                .indexFunc(SsoAuditLogCleanupRecord::getFinishedAt));
        });
    }

    private void unregisterSchemes() {
        schemeManager.unregister(Scheme.buildFromType(SsoClient.class));
        schemeManager.unregister(Scheme.buildFromType(SsoAuthorizationCode.class));
        schemeManager.unregister(Scheme.buildFromType(SsoUserBinding.class));
        schemeManager.unregister(Scheme.buildFromType(SsoRoleMapping.class));
        schemeManager.unregister(Scheme.buildFromType(SsoAuditLog.class));
        schemeManager.unregister(Scheme.buildFromType(SsoAuditLogCleanupRecord.class));
    }
}
