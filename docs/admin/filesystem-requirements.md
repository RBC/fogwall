# JGit filesystem requirements

JGit requires write access to two locations at runtime. Failures here produce cryptic errors that look like git
transport problems but are actually filesystem permission issues.

## Home directory

JGit reads `~/.gitconfig` and writes lock files in `$HOME`. In a container, `HOME` must point to a writable directory.

The Docker image sets `ENV HOME=/app/home` and creates `/app/home` with correct permissions. If you override the image's
entrypoint or run under a different UID, verify that `$HOME` is writable:

```bash
# Inside the container:
ls -la $HOME
touch $HOME/.test && rm $HOME/.test   # must succeed
```

**OpenShift / arbitrary UID:** OpenShift runs containers as a random UID by default. The image is built with GID 0
group-write on `/app/home`, `/app/.data`, and `/app/logs` (`chmod g+rwX`) so that any UID in group 0 can write to them.
If you see `Permission denied` errors on startup, check whether your security context is overriding the GID.

## `/tmp` for scratch repos and gitleaks

JGit creates temporary bare repositories in `java.io.tmpdir` (defaults to `/tmp`) for server mode pushes and for
transparent proxy diff inspection. Gitleaks also writes temporary files there.

If `/tmp` is not writable (e.g. `noexec` mount, read-only root filesystem), override the JVM temp dir:

```yaml
environment:
  JAVA_TOOL_OPTIONS: -Djava.io.tmpdir=/app/.data/tmp
```

And create the directory in your deployment:

```bash
mkdir -p /app/.data/tmp
chmod 700 /app/.data/tmp
```

For Kubernetes with a `readOnlyRootFilesystem: true` security context, mount an `emptyDir` at `/tmp`:

```yaml
volumes:
  - name: tmp
    emptyDir: {}
volumeMounts:
  - name: tmp
    mountPath: /tmp
```

## Gitleaks binary permissions

When `secret-scan.enabled: true`, the proxy needs to execute the gitleaks binary. The bundled binary (inside the JAR) is
extracted to `java.io.tmpdir` at startup — that directory must allow executable files (`noexec` prevents this).

If the temp dir is `noexec`, point gitleaks at a writable, exec-allowed path:

```yaml
commit:
  secret-scan:
    enabled: true
    scanner-path: /app/.data/gitleaks # explicit path bypasses auto-extraction
```

Or pre-install gitleaks and put it on `PATH` — the proxy will find it via system path lookup before falling back to the
bundled binary.
