package com.rbc.fogwall.jetty.reload;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.FogwallConfigLoader;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.ReloadConfig;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLAnchorReplayingFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Manages hot-reloading of {@link ConfigHolder} at runtime, without restarting the server.
 *
 * <p>Two reload sources are supported (one or both may be active):
 *
 * <ol>
 *   <li><b>File watch</b> — a {@link WatchService} monitors a local filesystem path. When the file changes the proxy
 *       immediately reloads the overlay.
 *   <li><b>Git source</b> — a background thread clones (on first run) or pulls the configured git repository and reads
 *       a YAML file from the working tree on a fixed interval.
 * </ol>
 *
 * <p>Each reload can target a specific {@link Section} or {@link Section#ALL}. Either way it applies only the sections
 * the source document actually declares — a section the document is silent about keeps its current live value rather
 * than reverting to the base default. Provider, server, and database changes log a WARNING — those require a restart.
 *
 * <p>A concurrent reload guard prevents overlapping reloads.
 */
@Slf4j
public class LiveConfigLoader {

    /**
     * Config sections that support hot-reload. Pass to {@link #reload(Section)} to reload only the specified section,
     * or use {@link #ALL} to reload every section the source document declares.
     */
    public enum Section {
        COMMIT,
        DIFF_SCAN,
        SECRET_SCAN,
        BINARY_BLOB,
        RULES,
        PERMISSIONS,
        ATTESTATIONS,
        ALL;

        /** Parse a section name (case-insensitive, hyphens treated as underscores). */
        public static Section fromString(String value) {
            if (value == null) return ALL;
            return switch (value.trim().toLowerCase().replace('-', '_')) {
                case "commit" -> COMMIT;
                case "diff_scan" -> DIFF_SCAN;
                case "secret_scan" -> SECRET_SCAN;
                case "binary_blob" -> BINARY_BLOB;
                case "rules" -> RULES;
                case "permissions" -> PERMISSIONS;
                case "attestations" -> ATTESTATIONS;
                default -> ALL;
            };
        }
    }

    private static final YAMLMapper YAML =
            YAMLMapper.builder(new YAMLAnchorReplayingFactory()).build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** Top-level YAML key to the reloadable section it configures. Keys absent here are not hot-reloadable sections. */
    private static final Map<String, Section> SECTION_KEYS = Map.of(
            "commit", Section.COMMIT,
            "diff-scan", Section.DIFF_SCAN,
            "secret-scan", Section.SECRET_SCAN,
            "binary-blob", Section.BINARY_BLOB,
            "rules", Section.RULES,
            "permissions", Section.PERMISSIONS,
            "attestations", Section.ATTESTATIONS);

    private final ConfigHolder configHolder;
    private final FogwallConfig startupConfig;
    private final ReloadConfig reloadConfig;
    private final UrlRuleRegistry urlRuleRegistry;
    private final RepoPermissionService repoPermissionService;

    /**
     * Serializes reloads. An explicit {@link #reload(Section)} blocks on this and always applies; the background
     * file-watch and git-poll sources {@code tryLock} and skip when a reload is already running, since a dropped poll
     * is caught by the next one.
     */
    private final ReentrantLock reloadLock = new ReentrantLock();

    private Thread fileWatchThread;
    private ScheduledExecutorService gitPollScheduler;
    private ScheduledFuture<?> gitPollFuture;

    public LiveConfigLoader(
            ConfigHolder configHolder,
            FogwallConfig startupConfig,
            ReloadConfig reloadConfig,
            UrlRuleRegistry urlRuleRegistry,
            RepoPermissionService repoPermissionService) {
        this.configHolder = configHolder;
        this.startupConfig = startupConfig;
        this.reloadConfig = reloadConfig;
        this.urlRuleRegistry = urlRuleRegistry;
        this.repoPermissionService = repoPermissionService;
    }

    /** Starts file-watch and/or git-poll threads based on {@link ReloadConfig}. */
    public void start() {
        if (reloadConfig.getFile().isEnabled()) {
            String path = reloadConfig.getFile().getPath();
            if (path == null || path.isBlank()) {
                log.warn("reload.file.enabled=true but reload.file.path is not set — file-watch disabled");
            } else {
                startFileWatch(Path.of(path).toAbsolutePath());
            }
        }

        if (reloadConfig.getGit().isEnabled()) {
            String url = reloadConfig.getGit().getUrl();
            if (url == null || url.isBlank()) {
                log.warn("reload.git.enabled=true but reload.git.url is not set — git-source disabled");
            } else {
                startGitPoller();
            }
        }
    }

    /** Stops all background threads cleanly. */
    public void stop() {
        if (fileWatchThread != null) {
            fileWatchThread.interrupt();
        }
        if (gitPollScheduler != null) {
            gitPollScheduler.shutdownNow();
        }
    }

    /**
     * Manually triggers a reload of all sections from configured sources. Equivalent to {@code reload(Section.ALL)}.
     *
     * @return a brief human-readable description of what was reloaded
     */
    public String reload() {
        return reload(Section.ALL);
    }

    /**
     * Manually triggers a reload of the specified section from configured sources. Called by the REST endpoint
     * ({@code POST /api/config/reload?section=<section>}).
     *
     * @return a brief human-readable description of what was reloaded
     */
    public String reload(Section section) {
        ReloadConfig.FileSourceConfig fileCfg = reloadConfig.getFile();
        ReloadConfig.GitSourceConfig gitCfg = reloadConfig.getGit();
        String label = section.name().toLowerCase().replace('_', '-');

        if (fileCfg.isEnabled() && !fileCfg.getPath().isBlank()) {
            boolean applied = reloadFromFile(Path.of(fileCfg.getPath()).toAbsolutePath(), section, true);
            return applied
                    ? "Reloaded " + label + " from file: " + fileCfg.getPath()
                    : "Reload of " + label + " from file failed — see server logs";
        }

        if (gitCfg.isEnabled() && !gitCfg.getUrl().isBlank()) {
            boolean applied = reloadFromGit(section, true);
            return applied
                    ? "Reloaded " + label + " from git: " + gitCfg.getUrl() + " (" + gitCfg.getBranch() + ")"
                    : "Reload of " + label + " from git failed — see server logs";
        }

        return "No external reload sources configured — config unchanged";
    }

    // -----------------------------------------------------------------------
    // File watch
    // -----------------------------------------------------------------------

    private void startFileWatch(Path watchPath) {
        if (!Files.exists(watchPath)) {
            log.warn(
                    "reload.file.path={} does not exist — file-watch will not start until the file is created",
                    watchPath);
        }
        log.info("Starting file-watch on {}", watchPath);
        fileWatchThread = Thread.ofVirtual().name("config-file-watcher").start(() -> runFileWatch(watchPath));
    }

    private void runFileWatch(Path watchPath) {
        Path dir = watchPath.getParent();
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            log.info("File-watch active on directory {} for {}", dir, watchPath.getFileName());
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = dir.resolve((Path) event.context());
                    if (changed.equals(watchPath)) {
                        log.info("Config file changed: {} — triggering reload of the sections it declares", changed);
                        reloadFromFile(watchPath, Section.ALL, false);
                    }
                }
                if (!key.reset()) {
                    log.warn("File-watch key invalidated — directory may have been deleted");
                    break;
                }
            }
        } catch (IOException e) {
            log.error("File-watch failed to start on {}: {}", dir, e.getMessage(), e);
        }
    }

    /**
     * @param blocking wait for the reload guard (an explicit reload) rather than skip when busy (a background poll)
     * @return whether the reload was applied
     */
    private boolean reloadFromFile(Path overrideFile, Section section, boolean blocking) {
        if (blocking) {
            reloadLock.lock();
        } else if (!reloadLock.tryLock()) {
            log.debug("Reload already in progress — skipping");
            return false;
        }
        try {
            FogwallConfig newConfig = FogwallConfigLoader.loadWithOverride(overrideFile);
            applyReload(newConfig, sectionsToApply(section, overrideFile));
            return true;
        } catch (Exception e) {
            log.error("Config reload from file {} failed: {}", overrideFile, e.getMessage(), e);
            return false;
        } finally {
            reloadLock.unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Git source
    // -----------------------------------------------------------------------

    private void startGitPoller() {
        int intervalSeconds = reloadConfig.getGit().getIntervalSeconds();
        if (intervalSeconds <= 0) {
            log.info(
                    "reload.git.interval-seconds=0 — git-source will only reload on POST /api/config/reload; no polling");
            gitPollScheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("config-git-poller").factory());
            gitPollScheduler.submit(() -> reloadFromGit(Section.ALL, false));
            return;
        }
        log.info(
                "Starting git-source poller: url={} branch={} interval={}s",
                reloadConfig.getGit().getUrl(),
                reloadConfig.getGit().getBranch(),
                intervalSeconds);
        gitPollScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("config-git-poller").factory());
        gitPollFuture = gitPollScheduler.scheduleAtFixedRate(
                () -> reloadFromGit(Section.ALL, false), 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * @param blocking wait for the reload guard (an explicit reload) rather than skip when busy (a background poll)
     * @return whether the reload was applied
     */
    private boolean reloadFromGit(Section section, boolean blocking) {
        if (blocking) {
            reloadLock.lock();
        } else if (!reloadLock.tryLock()) {
            log.debug("Reload already in progress — skipping git poll");
            return false;
        }
        Path cloneDir = null;
        try {
            cloneDir = Files.createTempDirectory("fogwall-config-git-");
            Path yamlFile = fetchGitConfig(cloneDir);
            if (yamlFile != null) {
                FogwallConfig newConfig = FogwallConfigLoader.loadWithOverride(yamlFile);
                applyReload(newConfig, sectionsToApply(section, yamlFile));
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Config reload from git failed: {}", e.getMessage(), e);
            return false;
        } finally {
            reloadLock.unlock();
            deleteQuietly(cloneDir);
        }
    }

    /**
     * Clones the configured git repo into {@code cloneDir}, reads the requested YAML file, and returns its path.
     * Returns {@code null} if the file doesn't exist in the repo.
     *
     * <p>A fresh clone is performed on every call — no local state is retained between reloads. This avoids pull
     * failures caused by force-pushes on the remote branch.
     *
     * <p>Authentication is opt-in via environment variables — no config fields required:
     *
     * <ul>
     *   <li>{@code FOGWALL_RELOAD_GIT_AUTH_USERNAME} — git username (or a token username placeholder)
     *   <li>{@code FOGWALL_RELOAD_GIT_AUTH_PASSWORD} — personal access token or password
     * </ul>
     *
     * If neither variable is set the clone proceeds without credentials (suitable for public repos or SSH-based URLs
     * where the JVM's SSH agent handles auth).
     */
    private Path fetchGitConfig(Path cloneDir) throws GitAPIException, IOException {
        ReloadConfig.GitSourceConfig gitCfg = reloadConfig.getGit();
        UsernamePasswordCredentialsProvider creds = gitCredentials();

        log.info(
                "Cloning config repo {} branch={} into {} (auth={})",
                gitCfg.getUrl(),
                gitCfg.getBranch(),
                cloneDir,
                creds != null ? "yes" : "no");
        var clone = Git.cloneRepository()
                .setURI(gitCfg.getUrl())
                .setBranch(gitCfg.getBranch())
                .setDirectory(cloneDir.toFile())
                .setDepth(1);
        if (creds != null) clone.setCredentialsProvider(creds);
        clone.call().close();

        Path configFile = cloneDir.resolve(gitCfg.getFilePath());
        if (!Files.exists(configFile)) {
            log.warn("Config file {} not found in cloned repo at {}", gitCfg.getFilePath(), cloneDir);
            return null;
        }
        return configFile;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            log.debug("Failed to delete temp clone dir {}: {}", dir, e.getMessage());
        }
    }

    /**
     * Reads {@code FOGWALL_RELOAD_GIT_AUTH_USERNAME} and {@code FOGWALL_RELOAD_GIT_AUTH_PASSWORD} from the environment.
     * Returns a credentials provider if both are set, or {@code null} if either is absent.
     */
    private static UsernamePasswordCredentialsProvider gitCredentials() {
        String username = System.getenv("FOGWALL_RELOAD_GIT_AUTH_USERNAME");
        String password = System.getenv("FOGWALL_RELOAD_GIT_AUTH_PASSWORD");
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            return new UsernamePasswordCredentialsProvider(username, password);
        }
        if ((username != null) != (password != null)) {
            log.warn("FOGWALL_RELOAD_GIT_AUTH_USERNAME and FOGWALL_RELOAD_GIT_AUTH_PASSWORD must both be set "
                    + "for git auth to work — proceeding without credentials");
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Apply reload
    // -----------------------------------------------------------------------

    /**
     * The reloadable sections a source document actually declares, keyed by their top-level YAML key. A reload applies
     * only these — a section the document is silent about keeps its current live value rather than reverting to the
     * base default, so a partial reload file patches the sections it names and leaves the rest alone.
     */
    private Set<Section> declaredSections(Path source) throws IOException {
        Map<String, Object> tree;
        try (var in = Files.newInputStream(source)) {
            tree = YAML.readValue(in, MAP_TYPE);
        }
        if (tree == null) return EnumSet.noneOf(Section.class);
        Set<Section> declared = EnumSet.noneOf(Section.class);
        for (String key : tree.keySet()) {
            Section section = SECTION_KEYS.get(key);
            if (section != null) declared.add(section);
        }
        return declared;
    }

    /**
     * The concrete sections to apply for a reload: those declared in {@code source}, narrowed to {@code requested} when
     * a specific section was asked for. {@link Section#ALL} means every declared section.
     */
    private Set<Section> sectionsToApply(Section requested, Path source) throws IOException {
        Set<Section> declared = declaredSections(source);
        if (requested == Section.ALL) return declared;
        declared.retainAll(EnumSet.of(requested));
        return declared;
    }

    private void applyReload(FogwallConfig newConfig, Set<Section> sections) {
        var builder = new JettyConfigurationBuilder(newConfig);
        // Validate all provider cross-references before applying any changes. If the new config has
        // a bad reference, this throws and the reload is aborted — the live config is not modified.
        builder.validateProviderReferences();

        for (Section section : sections) {
            switch (section) {
                case COMMIT -> reloadCommit(builder);
                case DIFF_SCAN -> reloadDiffScan(builder);
                case SECRET_SCAN -> reloadSecretScanning(builder);
                case BINARY_BLOB -> reloadBinaryBlob(builder);
                case RULES -> reloadRules(builder, newConfig);
                case PERMISSIONS -> reloadPermissions(builder, newConfig);
                case ATTESTATIONS -> reloadAttestations(builder, newConfig);
                case ALL -> throw new IllegalArgumentException("ALL must be resolved to concrete sections first");
            }
        }

        warnOnRestartRequired(newConfig);
        log.info("Config reload complete — sections: {}", sections);
    }

    private void reloadCommit(JettyConfigurationBuilder builder) {
        configHolder.update(builder.buildCommitConfig());
    }

    private void reloadDiffScan(JettyConfigurationBuilder builder) {
        configHolder.update(builder.buildDiffScanConfig());
    }

    private void reloadSecretScanning(JettyConfigurationBuilder builder) {
        configHolder.update(builder.buildSecretScanConfig());
    }

    private void reloadBinaryBlob(JettyConfigurationBuilder builder) {
        configHolder.update(builder.buildBinaryBlobConfig());
    }

    private void reloadRules(JettyConfigurationBuilder builder, FogwallConfig newConfig) {
        if (urlRuleRegistry == null) {
            log.warn("repoRegistry not available — rules reload skipped");
            return;
        }
        urlRuleRegistry.seedFromConfig(builder.buildConfigRules(newConfig));
        log.info("Rules reloaded from config");
    }

    private void reloadPermissions(JettyConfigurationBuilder builder, FogwallConfig newConfig) {
        if (repoPermissionService == null) {
            log.warn("repoPermissionService not available — permissions reload skipped");
            return;
        }
        var configPerms = builder.buildConfigPermissions(newConfig);
        var userStore = builder.buildUserStore();
        configPerms.stream().map(RepoPermission::getUsername).distinct().forEach(userStore::upsertUser);
        repoPermissionService.seedFromConfig(configPerms);
        log.info("Permissions reloaded from config");
    }

    private void reloadAttestations(JettyConfigurationBuilder builder, FogwallConfig newConfig) {
        configHolder.update(builder.buildAttestations(newConfig));
    }

    /**
     * Compares the new config against the startup config and warns about sections that changed but require a restart to
     * take effect.
     */
    private void warnOnRestartRequired(FogwallConfig newConfig) {
        if (!newConfig
                .getProviders()
                .keySet()
                .equals(startupConfig.getProviders().keySet())) {
            log.warn(
                    "Config reload: providers section changed — restart required for the new providers to take effect");
        }
        if (newConfig.getServer().getPort() != startupConfig.getServer().getPort()) {
            log.warn("Config reload: server.port changed — restart required");
        }
        if (!newConfig
                .getDatabase()
                .getType()
                .equals(startupConfig.getDatabase().getType())) {
            log.warn("Config reload: database.type changed — restart required");
        }
    }
}
