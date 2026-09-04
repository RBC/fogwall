package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.ZERO_OID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rbc.fogwall.config.SecretScanConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the gitleaks secret scanner against a unified diff and returns structured findings.
 *
 * <p>Binary resolution order (first match wins):
 *
 * <ol>
 *   <li>{@code SecretScanningConfig.scannerPath} - explicit path, bypasses all other resolution
 *   <li>Version-pinned download - if {@code version} is set and {@code autoInstall} is {@code true}, downloads that
 *       specific gitleaks release from GitHub and caches it in {@code installDir}; use this to pin a version different
 *       from the one bundled in the JAR
 *   <li>Bundled JAR binary - the gitleaks binary packaged into the JAR at build time ({@link #DEFAULT_VERSION}); always
 *       present in standard builds
 *   <li>System PATH - falls back to a {@code gitleaks} binary already installed on the host/container
 * </ol>
 *
 * <p>If no binary is found or the scan errors, an empty {@link Optional} is returned. Whether the push is blocked is
 * determined by the caller — callers with {@code secret-scan: enabled: true} block the push (fail-closed).
 *
 * <p>The bundled binary extracted from the JAR is deleted on JVM shutdown. Binaries downloaded to {@code installDir}
 * persist across restarts.
 */
@Slf4j
public class GitleaksRunner {

    /**
     * Default gitleaks version used for auto-install. Keep in sync with {@code gitleaksVersion} in
     * {@code fogwall-core/build.gradle}.
     */
    public static final String DEFAULT_VERSION = "8.30.1";

    /** Classpath resource prefix for bundled gitleaks binaries; full path is {@code gitleaks/<os>_<arch>}. */
    private static final String BUNDLED_BINARY_RESOURCE_PREFIX = "gitleaks/";

    /** Base URL for gitleaks release assets; the version tag is appended (e.g. {@code ...download/v8.30.1}). */
    private static final String RELEASE_DOWNLOAD_BASE = "https://github.com/gitleaks/gitleaks/releases/download/v";

    /** Exit code gitleaks returns when findings are present (distinct from error exit codes). */
    private static final int FINDINGS_EXIT_CODE = 2;

    private static final String DEFAULT_INSTALL_DIR = System.getProperty("user.home") + "/.cache/fogwall/gitleaks";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Lazily extracted classpath binary - shared across all instances (deleted on JVM exit). */
    private static volatile Path extractedBinaryPath;

    private static final Object EXTRACT_LOCK = new Object();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans the provided unified diff for secrets using gitleaks.
     *
     * @param diff unified diff text to scan (may be empty)
     * @param config secret scanning configuration
     * @return {@link Optional#empty()} if the scanner is unavailable or errored (fail-open); otherwise an optional
     *     containing the (possibly empty) list of findings
     */
    public Optional<List<Finding>> scan(String diff, SecretScanConfig config) {
        if (diff == null || diff.isBlank()) {
            return Optional.of(Collections.emptyList());
        }

        Path binaryPath = resolveBinaryPath(config);
        if (binaryPath == null) {
            log.warn("gitleaks binary not available - secret scanning skipped (fail-open). "
                    + "Set commit.secretScanning.auto-install: true or provide scanner-path.");
            return Optional.empty();
        }

        Path reportFile = null;
        Path inlineConfigFile = null;
        try {
            reportFile = Files.createTempFile("gitleaks-report-", ".json");
            inlineConfigFile = writeInlineConfigIfNeeded(config);
            List<String> cmd = buildCommand(binaryPath, reportFile, resolveConfigFile(config, inlineConfigFile));
            log.debug("Running gitleaks: {}", cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            // Run in /tmp so gitleaks doesn't walk the server's working directory
            pb.directory(reportFile.getParent().toFile());
            Process process = pb.start();

            // Write diff to stdin in a daemon thread; closing stdin signals EOF to gitleaks
            final byte[] diffBytes = diff.getBytes(StandardCharsets.UTF_8);
            Thread stdinWriter = new Thread(() -> {
                try (var stdin = process.getOutputStream()) {
                    stdin.write(diffBytes);
                } catch (IOException e) {
                    log.debug("stdin write error (process may have exited early)", e);
                }
            });
            stdinWriter.setDaemon(true);
            stdinWriter.start();

            boolean completed = process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                stdinWriter.interrupt();
                log.warn(
                        "gitleaks timed out after {}s - secret scanning skipped (fail-open)",
                        config.getTimeoutSeconds());
                return Optional.empty();
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.debug("gitleaks: no findings");
                return Optional.of(Collections.emptyList());
            } else if (exitCode == FINDINGS_EXIT_CODE) {
                List<Finding> findings = readFindings(reportFile);
                enrichFindings(findings, diff);
                log.debug("gitleaks: {} finding(s)", findings.size());
                return Optional.of(findings);
            } else {
                log.warn("gitleaks exited with code {} - treat as scanner error", exitCode);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.warn("Failed to run gitleaks - secret scanning skipped (fail-open): {}", e.getMessage(), e);
            return Optional.empty();
        } finally {
            deleteQuietly(reportFile);
            deleteQuietly(inlineConfigFile);
        }
    }

    /**
     * Scans a commit range in a local git repository for secrets using {@code gitleaks git}.
     *
     * <p>Unlike {@link #scan(String, SecretScanConfig)}, this mode runs gitleaks natively against the git object graph,
     * so path-based allowlists and per-file context in gitleaks rules are applied correctly. No post-hoc diff
     * enrichment is needed - gitleaks populates {@code File}, {@code StartLine}, and {@code Commit} in each finding
     * directly.
     *
     * <p>For a new-branch push ({@code commitFrom} equals {@link GitClientUtils#ZERO_OID }), the scan covers all
     * commits reachable from {@code commitTo} that are not yet reachable from any existing ref ({@code --not --all}).
     * For a branch update, the scan covers {@code commitFrom..commitTo}.
     *
     * @param repoDir path to the git repository root (bare or non-bare)
     * @param commitFrom old-tip OID; use {@link GitClientUtils#ZERO_OID} for a new-branch push
     * @param commitTo new-tip OID
     * @param config secret scanning configuration
     * @return {@link Optional#empty()} if the scanner is unavailable or errored (fail-open); otherwise the (possibly
     *     empty) list of findings
     */
    public Optional<List<Finding>> scanGit(Path repoDir, String commitFrom, String commitTo, SecretScanConfig config) {
        return scanGit(repoDir, null, commitFrom, commitTo, config);
    }

    /**
     * As {@link #scanGit(Path, String, String, SecretScanConfig)}, but also exposes {@code alternateObjectDir} to the
     * {@code git} process gitleaks spawns via {@code GIT_ALTERNATE_OBJECT_DIRECTORIES}.
     *
     * <p>Server mode receives a push into a {@link QuarantineObjectStore}: the repository shares the mirror's git
     * directory (so {@code repoDir} is the mirror and existing refs resolve) but the pushed objects land in a separate
     * quarantine object directory. The in-process JGit hooks read those objects fine, but gitleaks shells out to
     * {@code git}, which — running against the mirror — cannot see the quarantine and so scans nothing, silently
     * reporting a clean pass. Passing the quarantine's object directory as an alternate makes the pushed commits
     * resolvable to that external git exactly as intended. When {@code alternateObjectDir} is {@code null} (no
     * quarantine; objects already in the mirror) this behaves identically to the four-argument overload.
     *
     * @param alternateObjectDir extra git object directory to expose to the scan, or {@code null} for none
     */
    public Optional<List<Finding>> scanGit(
            Path repoDir, Path alternateObjectDir, String commitFrom, String commitTo, SecretScanConfig config) {
        Path binaryPath = resolveBinaryPath(config);
        if (binaryPath == null) {
            log.warn("gitleaks binary not available - secret scanning skipped (fail-open). "
                    + "Set commit.secretScanning.auto-install: true or provide scanner-path.");
            return Optional.empty();
        }

        // Fail closed if the pushed tip is not resolvable in the environment the scan will actually use. This is the
        // exact failure that made server-mode secret scanning a silent no-op once pushes were received into a
        // quarantine object store: the pushed commits lived in the quarantine, invisible to the child git gitleaks
        // spawns against the mirror, so git log found nothing, gitleaks exited 0, and the scan reported a clean pass.
        // Verifying visibility up front turns any recurrence into a blocked push rather than a false all-clear.
        if (!commitResolvable(repoDir, alternateObjectDir, commitTo, config)) {
            log.error(
                    "gitleaks git: pushed tip {} is not resolvable in {} (alternate objects: {}) - treating as a "
                            + "scanner error so the push fails closed instead of passing unscanned",
                    commitTo,
                    repoDir,
                    alternateObjectDir);
            return Optional.empty();
        }

        // New-branch push: scan only commits not reachable from any existing ref.
        // Branch update: scan only the new commits introduced by this push.
        String logOpts = ZERO_OID.equals(commitFrom) ? commitTo + " --not --all" : commitFrom + ".." + commitTo;

        Path reportFile = null;
        Path inlineConfigFile = null;
        try {
            reportFile = Files.createTempFile("gitleaks-report-", ".json");
            inlineConfigFile = writeInlineConfigIfNeeded(config);
            List<String> cmd =
                    buildGitCommand(binaryPath, logOpts, reportFile, resolveConfigFile(config, inlineConfigFile));
            log.debug("Running gitleaks git: {}", cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            // Run inside the repo so gitleaks can traverse the git object graph
            pb.directory(repoDir.toFile());
            // Expose the quarantine's objects to the git gitleaks spawns; inherited by that child process.
            if (alternateObjectDir != null) {
                pb.environment().put("GIT_ALTERNATE_OBJECT_DIRECTORIES", alternateObjectDir.toString());
            }
            Process process = pb.start();

            // Gitleaks git writes findings to the JSON report file; drain output to avoid blocking
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            process.getErrorStream().transferTo(OutputStream.nullOutputStream());

            boolean completed = process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn(
                        "gitleaks timed out after {}s - secret scanning skipped (fail-open)",
                        config.getTimeoutSeconds());
                return Optional.empty();
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.debug("gitleaks git: no findings");
                return Optional.of(Collections.emptyList());
            } else if (exitCode == FINDINGS_EXIT_CODE) {
                List<Finding> findings = readFindings(reportFile);
                log.debug("gitleaks git: {} finding(s)", findings.size());
                return Optional.of(findings);
            } else {
                log.warn("gitleaks git exited with code {} - treat as scanner error", exitCode);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.warn("Failed to run gitleaks git - secret scanning skipped (fail-open): {}", e.getMessage(), e);
            return Optional.empty();
        } finally {
            deleteQuietly(reportFile);
            deleteQuietly(inlineConfigFile);
        }
    }

    /**
     * Confirms the pushed tip is resolvable in the exact directory and object environment the scan will use, by running
     * {@code git cat-file -e <commitTo>} with the same {@code GIT_ALTERNATE_OBJECT_DIRECTORIES} that {@link #scanGit}
     * sets. Returns {@code false} on a missing object, a git failure, or git being unavailable — each of which must
     * block the push rather than let a scan that could see nothing report a clean pass.
     *
     * <p>{@code gitleaks git} already shells out to {@code git}, so requiring {@code git} on the path here adds no new
     * dependency; if it were absent the scan itself would fail too.
     */
    private static boolean commitResolvable(
            Path repoDir, Path alternateObjectDir, String commitTo, SecretScanConfig config) {
        if (commitTo == null || commitTo.isBlank()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "cat-file", "-e", commitTo);
            pb.directory(repoDir.toFile());
            pb.redirectErrorStream(true);
            if (alternateObjectDir != null) {
                pb.environment().put("GIT_ALTERNATE_OBJECT_DIRECTORIES", alternateObjectDir.toString());
            }
            Process process = pb.start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (!process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("gitleaks git: object-visibility preflight for {} timed out - failing closed", commitTo);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("gitleaks git: could not verify {} is resolvable - failing closed: {}", commitTo, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Binary resolution
    // -------------------------------------------------------------------------

    private Path resolveBinaryPath(SecretScanConfig config) {
        // 1. Explicit scanner-path - always wins
        if (config.getScannerPath() != null && !config.getScannerPath().isBlank()) {
            log.debug("gitleaks: using configured scanner-path={}", config.getScannerPath());
            return Path.of(config.getScannerPath());
        }

        // 2. Version-pinned download - only when caller requests a specific version via auto-install
        if (config.isAutoInstall()
                && config.getVersion() != null
                && !config.getVersion().isBlank()) {
            Path installDir = resolveInstallDir(config);
            Path cached = installDir.resolve("gitleaks-" + config.getVersion());
            if (Files.isExecutable(cached)) {
                log.debug("gitleaks: using cached version {} at {}", config.getVersion(), cached);
                return cached;
            }
            Path downloaded = autoInstall(config, installDir);
            if (downloaded != null) return downloaded;
        }

        // 3. Bundled JAR binary (DEFAULT_VERSION, always present in standard builds)
        try {
            Path bundled = extractBundledBinary();
            if (bundled != null) return bundled;
        } catch (IOException e) {
            log.debug("Failed to extract bundled gitleaks binary: {}", e.getMessage());
        }

        // 4. System PATH
        Path onPath = findOnPath();
        if (onPath != null) {
            log.debug("gitleaks: found on system PATH");
            return onPath;
        }

        return null;
    }

    /** Returns {@code Path.of("gitleaks")} if gitleaks is available on the system PATH, else null. */
    private static Path findOnPath() {
        try {
            Process p = new ProcessBuilder("gitleaks", "version")
                    .redirectErrorStream(true)
                    .start();
            // Drain output to avoid blocking
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return Path.of("gitleaks");
            }
        } catch (Exception ignored) {
            // Not on PATH
        }
        return null;
    }

    private static Path resolveInstallDir(SecretScanConfig config) {
        String dir = config.getInstallDir();
        if (dir != null && !dir.isBlank()) {
            return Path.of(dir.replace("~", System.getProperty("user.home")));
        }
        return Path.of(DEFAULT_INSTALL_DIR);
    }

    private Path autoInstall(SecretScanConfig config, Path installDir) {
        String version =
                (config.getVersion() != null && !config.getVersion().isBlank()) ? config.getVersion() : DEFAULT_VERSION;

        String tarSuffix = detectTarSuffix();
        if (tarSuffix == null) {
            log.warn(
                    "Cannot auto-install gitleaks: unsupported platform (os={}, arch={}). "
                            + "Install gitleaks manually and set commit.secretScanning.scanner-path.",
                    System.getProperty("os.name"),
                    System.getProperty("os.arch"));
            return null;
        }

        String tarName = "gitleaks_" + version + "_" + tarSuffix + ".tar.gz";
        String downloadUrl = RELEASE_DOWNLOAD_BASE + version + "/" + tarName;
        // Name the binary with the version so different versions can coexist in the cache dir
        Path binary = installDir.resolve("gitleaks-" + version);

        try {
            Files.createDirectories(installDir);
            Path tarFile = installDir.resolve(tarName);

            log.info("Auto-installing gitleaks {} to {} ...", version, installDir);
            try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
                Files.copy(in, tarFile, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!verifyReleaseChecksum(tarFile, tarName, version)) {
                Files.deleteIfExists(tarFile);
                return null;
            }

            // Extract with Ant-style untar via the JVM's built-in zip/tar support isn't available,
            // so shell out to tar which is present on all Unix systems
            Process tar = new ProcessBuilder("tar", "-xzf", tarFile.toString(), "-C", installDir.toString(), "gitleaks")
                    .redirectErrorStream(true)
                    .start();
            String tarOutput = new String(tar.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!tar.waitFor(60, TimeUnit.SECONDS) || tar.exitValue() != 0) {
                log.warn("Failed to extract gitleaks tarball: {}", tarOutput);
                return null;
            }

            Files.deleteIfExists(tarFile);

            // tar extracts the binary as plain "gitleaks"; rename to versioned name
            Path extracted = installDir.resolve("gitleaks");
            if (!binary.equals(extracted) && Files.exists(extracted)) {
                Files.move(extracted, binary, StandardCopyOption.REPLACE_EXISTING);
            }

            makeExecutable(binary);
            log.info("gitleaks {} installed at {}", version, binary);
            return binary;

        } catch (Exception e) {
            log.warn(
                    "Failed to auto-install gitleaks {} - secret scanning skipped (fail-open): {}",
                    version,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Verifies the downloaded tarball against the SHA-256 published in the release's {@code checksums.txt}. Anything
     * that cannot be positively verified — unreachable checksum file, missing entry, digest mismatch — is refused; the
     * tarball must never be extracted or executed unverified.
     */
    private static boolean verifyReleaseChecksum(Path tarFile, String tarName, String version) {
        String checksumsUrl = RELEASE_DOWNLOAD_BASE + version + "/gitleaks_" + version + "_checksums.txt";
        try {
            String checksums;
            try (InputStream in = URI.create(checksumsUrl).toURL().openStream()) {
                checksums = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String expected = findChecksumEntry(checksums, tarName);
            if (expected == null) {
                log.error("gitleaks auto-install: no entry for {} in {} - refusing to install", tarName, checksumsUrl);
                return false;
            }
            String actual = sha256Hex(tarFile);
            if (!expected.equalsIgnoreCase(actual)) {
                log.error(
                        "gitleaks auto-install: checksum mismatch for {} (expected {}, actual {}) - refusing to install",
                        tarName,
                        expected,
                        actual);
                return false;
            }
            log.debug("gitleaks auto-install: verified {} against release checksums.txt", tarName);
            return true;
        } catch (Exception e) {
            log.error(
                    "gitleaks auto-install: could not verify checksum for {} - refusing to install: {}",
                    tarName,
                    e.getMessage());
            return false;
        }
    }

    /** Returns the hex digest listed for {@code fileName} in goreleaser-style checksum content, or null if absent. */
    static String findChecksumEntry(String checksumsContent, String fileName) {
        for (String line : checksumsContent.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[1].equals(fileName)) {
                return parts[0];
            }
        }
        return null;
    }

    static String sha256Hex(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is a mandatory JCA algorithm
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Detects the current platform and returns the gitleaks tarball suffix, or null if unsupported. */
    static String detectTarSuffix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String osKey = os.contains("linux") ? "linux" : (os.contains("mac") || os.contains("darwin")) ? "darwin" : null;
        String archKey = (arch.contains("amd64") || arch.contains("x86_64"))
                ? "x64"
                : (arch.contains("aarch64") || arch.contains("arm64")) ? "arm64" : null;

        return (osKey != null && archKey != null) ? osKey + "_" + archKey : null;
    }

    private static void makeExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            path.toFile().setExecutable(true);
        }
    }

    private Path extractBundledBinary() throws IOException {
        if (extractedBinaryPath != null) return extractedBinaryPath;
        synchronized (EXTRACT_LOCK) {
            if (extractedBinaryPath != null) return extractedBinaryPath;

            String suffix = detectTarSuffix();
            if (suffix == null) {
                log.debug(
                        "gitleaks: no bundled binary for platform ({} / {})",
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"));
                return null;
            }
            String resourceName = BUNDLED_BINARY_RESOURCE_PREFIX + suffix;
            InputStream resource = GitleaksRunner.class.getClassLoader().getResourceAsStream(resourceName);
            if (resource == null) {
                log.debug("gitleaks bundled resource not found: {}", resourceName);
                return null;
            }

            Path tempDir = Files.createTempDirectory("fogwall-gitleaks-");
            Path binaryPath = tempDir.resolve("gitleaks");
            try (resource) {
                Files.copy(resource, binaryPath);
            }
            makeExecutable(binaryPath);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(binaryPath);
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {
                }
            }));

            extractedBinaryPath = binaryPath;
            log.info("Extracted bundled gitleaks binary ({}) to {}", suffix, binaryPath);
            return extractedBinaryPath;
        }
    }

    // -------------------------------------------------------------------------
    // Command building
    // -------------------------------------------------------------------------

    private static List<String> buildCommand(Path binaryPath, Path reportFile, Path configFilePath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binaryPath.toString());
        cmd.add("detect");
        cmd.add("--pipe");
        cmd.add("--report-format");
        cmd.add("json");
        cmd.add("--report-path");
        cmd.add(reportFile.toString());
        cmd.add("--exit-code");
        cmd.add(String.valueOf(FINDINGS_EXIT_CODE));

        if (configFilePath != null) {
            cmd.add("--config");
            cmd.add(configFilePath.toString());
        }

        return cmd;
    }

    private static List<String> buildGitCommand(Path binaryPath, String logOpts, Path reportFile, Path configFilePath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binaryPath.toString());
        cmd.add("git");
        cmd.add("--log-opts=" + logOpts);
        cmd.add("--report-format");
        cmd.add("json");
        cmd.add("--report-path");
        cmd.add(reportFile.toString());
        cmd.add("--no-banner");
        cmd.add("--exit-code");
        cmd.add(String.valueOf(FINDINGS_EXIT_CODE));

        if (configFilePath != null) {
            cmd.add("--config");
            cmd.add(configFilePath.toString());
        }

        return cmd;
    }

    /**
     * Writes {@code config.inlineConfig} to a temporary TOML file and returns its path. Returns {@code null} if
     * {@code inlineConfig} is blank. The caller is responsible for deleting the file (via {@link #deleteQuietly}).
     */
    private static Path writeInlineConfigIfNeeded(SecretScanConfig config) throws IOException {
        String inline = config.getInlineConfig();
        if (inline == null || inline.isBlank()) return null;
        Path tmp = Files.createTempFile("gitleaks-config-", ".toml");
        Files.writeString(tmp, inline);
        return tmp;
    }

    /**
     * Resolves the effective gitleaks config file path. {@code inlineConfigFile} (already written) takes precedence
     * over {@code config.configFile}.
     */
    private static Path resolveConfigFile(SecretScanConfig config, Path inlineConfigFile) {
        if (inlineConfigFile != null) return inlineConfigFile;
        String cf = config.getConfigFile();
        if (cf != null && !cf.isBlank()) return Path.of(cf);
        return null;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    private static List<Finding> readFindings(Path reportFile) {
        try {
            if (!Files.exists(reportFile) || Files.size(reportFile) == 0) {
                return Collections.emptyList();
            }
            String json = Files.readString(reportFile);
            if (json.isBlank() || json.trim().equals("null")) {
                return Collections.emptyList();
            }
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse gitleaks JSON report: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Diff-line enrichment
    // -------------------------------------------------------------------------

    /**
     * Gitleaks {@code --pipe} mode reports {@code StartLine} as the 0-indexed line number within the raw diff text and
     * leaves {@code File} empty. This method parses the diff to map each finding back to the actual file path and the
     * file-relative (1-indexed) line number.
     */
    private static void enrichFindings(List<Finding> findings, String diff) {
        if (findings.isEmpty()) return;

        String[] lines = diff.split("\n", -1);
        String[] fileAtLine = new String[lines.length];
        int[] fileLineAtLine = new int[lines.length];

        String currentFile = null;
        int nextFileLine = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("+++ b/")) {
                currentFile = line.substring("+++ b/".length());
            } else if (line.startsWith("+++ /dev/null")) {
                currentFile = null;
            } else if (line.startsWith("@@")) {
                nextFileLine = parseHunkNewStart(line);
            } else if (line.startsWith("+")) {
                fileAtLine[i] = currentFile;
                fileLineAtLine[i] = nextFileLine;
                nextFileLine++;
            } else if (line.startsWith(" ")) {
                nextFileLine++;
            }
        }

        for (Finding f : findings) {
            int idx = f.getStartLine(); // 0-indexed line in diff
            if (idx >= 0 && idx < lines.length && fileAtLine[idx] != null) {
                f.setFile(fileAtLine[idx]);
                f.setStartLine(fileLineAtLine[idx]);
            }
        }
    }

    private static int parseHunkNewStart(String hunkHeader) {
        // "@@ -old_start[,old_count] +new_start[,new_count] @@"
        int plusIdx = hunkHeader.indexOf(" +");
        if (plusIdx < 0) return 1;
        int start = plusIdx + 2;
        int end = hunkHeader.indexOf(',', start);
        if (end < 0) end = hunkHeader.indexOf(' ', start);
        if (end < 0) return 1;
        try {
            return Integer.parseInt(hunkHeader.substring(start, end));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // -------------------------------------------------------------------------
    // Finding model
    // -------------------------------------------------------------------------

    /** A single secret detected by gitleaks. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Finding {

        @JsonProperty("Description")
        private String description;

        @JsonProperty("RuleID")
        private String ruleId;

        @JsonProperty("File")
        private String file;

        @JsonProperty("StartLine")
        private int startLine;

        @JsonProperty("Commit")
        private String commit;

        /**
         * The full matched text, verbatim from gitleaks (no {@code --redact} flag is used - see {@link #secret}). Never
         * append this directly to a user-facing message or log line; use {@link #maskedMatch()} instead.
         */
        @JsonProperty("Match")
        private String match;

        /**
         * The raw secret value gitleaks matched. Use only for redacting stored diff content (see
         * {@code SecretRedactor}) - never log it or include it in a {@link Violation} or any user-facing message.
         */
        @JsonProperty("Secret")
        private String secret;

        /**
         * {@link #match} with the raw {@link #secret} value blanked out - safe to log or show to the pushing user.
         * Mirrors what gitleaks' own {@code --redact} flag used to do, but computed here since we always request raw
         * output now (needed to redact the stored diff).
         */
        private String maskedMatch() {
            if (match == null || match.isBlank()) {
                return match;
            }
            return secret != null && !secret.isBlank() ? match.replace(secret, "[REDACTED]") : "[REDACTED]";
        }

        /**
         * Multi-line summary suitable for a push error message. Each line is kept short to fit the git sideband 80-char
         * width limit (git prefixes each newline with "remote: ").
         */
        public String toMessage() {
            StringBuilder sb = new StringBuilder();

            // Line 1: rule ID + location
            sb.append("[").append(ruleId != null ? ruleId : "unknown").append("]");
            if (file != null && !file.isBlank()) {
                sb.append("  ").append(file);
                if (startLine > 0) {
                    sb.append(":").append(startLine);
                }
            }

            // Line 2: commit hash (short)
            if (commit != null && commit.length() >= 7) {
                sb.append("\n  commit: ").append(commit, 0, 7);
            }

            // Line 3: redacted match snippet
            String masked = maskedMatch();
            if (masked != null && !masked.isBlank()) {
                sb.append("\n  match:  ").append(masked);
            }

            return sb.toString();
        }
    }
}
