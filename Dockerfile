# syntax=docker/dockerfile:1@sha256:2780b5c3bab67f1f76c781860de469442999ed1a0d7992a5efdf2cffc0e3d769

# ── Build stage ──────────────────────────────────────────────────────────────
FROM docker.io/eclipse-temurin:25-jdk-noble@sha256:3eb81ed94d8c1a34422f19f8188548bdf02cae69c91d0328afdbb7abed90f617 AS builder

# Install Node.js directly from the official distribution with SHA256 verification.
# To update: download the new tarball, verify against nodejs.org/dist/vX.Y.Z/SHASUMS256.txt,
# and update both NODE_VERSION and NODE_SHA256 below.
RUN apt-get update && apt-get install -y --no-install-recommends curl libatomic1 && rm -rf /var/lib/apt/lists/*

ARG NODE_VERSION=26.3.1
ARG NODE_SHA256_AMD64=e892cd615e637edebcf22f9653d80fba63167ad6754d20881fd52cc37be81441
ARG NODE_SHA256_ARM64=2f0829b201e9db20996ae15bce62138df1e3d317775b005778b05cf7b19714f1
ARG TARGETARCH
RUN case "${TARGETARCH}" in \
      arm64) NODE_ARCH=linux-arm64; NODE_SHA256="${NODE_SHA256_ARM64}" ;; \
      *)     NODE_ARCH=linux-x64;   NODE_SHA256="${NODE_SHA256_AMD64}" ;; \
    esac \
    && curl -fsSL https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-${NODE_ARCH}.tar.gz \
       -o /tmp/node.tar.gz \
    && echo "${NODE_SHA256}  /tmp/node.tar.gz" | sha256sum --check \
    && tar -xzf /tmp/node.tar.gz -C /usr/local --strip-components=1 \
    && rm /tmp/node.tar.gz \
    && node --version \
    && npm --version

WORKDIR /workspace

# Download the Gradle wrapper JARs in a dedicated layer so they are cached
# independently of source changes.
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./
RUN ./gradlew --version --no-daemon -q

COPY . .

# Build the distribution (all deps bundled in lib/).
# Node.js is installed above; the node-gradle plugin uses it from PATH (download=false).
# BuildKit cache mounts persist Gradle/npm downloads across builds.
# gitleaksTargets is derived from TARGETARCH so only the matching binary is bundled,
# keeping each arch-specific image lean (amd64 image carries only linux_x64, etc.).
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    --mount=type=cache,target=/root/.npm \
    case "${TARGETARCH}" in \
      arm64) GITLEAKS_TARGET=linux_arm64 ;; \
      *)     GITLEAKS_TARGET=linux_x64   ;; \
    esac \
    && ./gradlew clean :fogwall-dashboard:installDist \
       -PgitleaksTargets=${GITLEAKS_TARGET} --no-daemon -q

# Prepend a conf/ directory to the classpath so that a mounted fogwall-local.yml
# is picked up by JettyConfigurationLoader at runtime.
RUN sed -i \
    's|^CLASSPATH=\$APP_HOME/lib|CLASSPATH=$APP_HOME/conf:$APP_HOME/lib|' \
    fogwall-dashboard/build/install/fogwall-dashboard/bin/fogwall-dashboard

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM docker.io/eclipse-temurin:25-jre-noble@sha256:2f1da100788559b397bcf48c736169ea5b070bde84e55f203bbee8e83d87a175

# Packages to upgrade beyond what the base image ships, space-separated.
# Used to patch CVEs that are fixed in Ubuntu's repos but not yet picked up by
# the upstream temurin image rebuild. Clear once the base image catches up.
ARG SECURITY_UPGRADE_PKGS="libssl3t64 openssl"

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && if [ -n "${SECURITY_UPGRADE_PKGS}" ]; then \
         apt-get install -y --only-upgrade ${SECURITY_UPGRADE_PKGS}; \
       fi \
    && rm -rf /var/lib/apt/lists/*

# Copy the built distribution
COPY --from=builder \
    /workspace/fogwall-dashboard/build/install/fogwall-dashboard/ /app/

# Create the conf directory; mount a fogwall-{profile}.yml here to override config.
# Example: -v ./docker/fogwall-local.yml:/app/conf/fogwall-local.yml:ro
# docker run -e FOGWALL_CONFIG_PROFILES=local -v ./docker/fogwall-local.yml:/app/conf/fogwall-local.yml:ro ...
RUN mkdir -p /app/conf

# Data directory for file-based databases (h2-file, sqlite), log output, and
# JGit home (used for lock files and system config). Owned by GID 0 with
# group-write so the image works under restricted security contexts that run
# containers as an arbitrary non-root UID.
RUN bash -c 'mkdir -p /app/{.data,logs,home} \
    && chown -R 1000:0 /app/{.data,logs,home} \
    && chmod g+rwX /app/{.data,logs,home}'

ENV HOME=/app/home

EXPOSE 8080

USER 1000

ENTRYPOINT ["/app/bin/fogwall-dashboard"]
