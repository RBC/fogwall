#!/usr/bin/env python3
"""Reclaims GHCR space by deleting package versions that no release, edge or latest image needs.

A multi-arch push creates several package versions: the tagged image index, its platform and
attestation manifests (untagged), and an attestation referrer index tagged sha256-<index digest>
with its own child. Only the manifests reachable from a protected tag are worth keeping.

How it decides:
  1. Every tag that is not build-<sha> and not sha256-<digest> is a protected root (semver, latest,
     edge, anything unrecognised - unknown tags are protected, never deleted).
  2. The protected set is the closure of those roots over the registry: each root's manifest is
     fetched from ghcr.io, its children are added, and any sha256-<digest> referrer of a protected
     digest (plus that referrer's children) is added too. A fetch failure aborts before any delete.
  3. Everything not in the protected set is a deletion candidate: build-* only indexes, their
     children and referrers, and manifests orphaned by earlier deletions.

Guards, each independent of the others:
  - protected-set membership is checked immediately before every delete;
  - a version carrying any tag other than build-*/sha256-* is never deleted - if such a version is
    not in the protected set, the run aborts as inconsistent instead of continuing;
  - the registry tag list is cross-checked against the API version list, so a truncated API
    listing that hides a release tag aborts the run;
  - versions younger than MIN_AGE_HOURS are kept, so a push in flight is never swept;
  - after deleting, every protected tag and every protected digest is re-resolved against the
    registry and the run fails if any is missing.

Runs via .github/workflows/cleanup-interim-images.yml (weekly + workflow_dispatch), using the
default GITHUB_TOKEN with the packages:write job permission - confirmed working for org-owned
packages once the /orgs/ (not /users/) endpoint is used. For local runs against an org-owned
package, GitHub Apps cannot delete package versions at all (confirmed with GitHub support) and
this org blocks classic PATs, so a token from an account GitHub does permit here is required -
GITHUB_TOKEN only works from within an Actions run.

Usage: GH_TOKEN=<token> [DRY_RUN=1] [MIN_AGE_HOURS=2] [DELETE_WORKERS=8] ./scripts/cleanup_interim_images.py [owner] [package ...]
Defaults: owner/packages from GITHUB_REPOSITORY or `gh repo view` (dashboard + "-server" image).
Standard library only; unit tests live in scripts/test_cleanup_interim_images.py.
"""

from __future__ import annotations

import base64
import json
import os
import re
import subprocess
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Callable, Iterable

REGISTRY = "ghcr.io"
API = "https://api.github.com"
MANIFEST_ACCEPT = ", ".join(
    [
        "application/vnd.oci.image.index.v1+json",
        "application/vnd.docker.distribution.manifest.list.v2+json",
        "application/vnd.oci.image.manifest.v1+json",
        "application/vnd.docker.distribution.manifest.v2+json",
    ]
)

EPHEMERAL_TAG = re.compile(r"^build-[0-9a-f]+$")
REFERRER_TAG = re.compile(r"^sha256-[0-9a-f]{64}$")
# Deny-list written separately from the allow-list above: semver (with or without v), major/minor
# shorthands, pre-release suffixes, latest and edge.
RELEASE_TAG = re.compile(r"^v?[0-9]+(\.[0-9]+){0,2}(-.+)?$")


class Abort(Exception):
    """Stops the run before (or during) deletion; nothing further is deleted."""


@dataclass(frozen=True)
class Version:
    id: int
    digest: str
    created_at: datetime
    tags: tuple[str, ...]

    @staticmethod
    def from_api(item: dict) -> "Version":
        tags = tuple(item.get("metadata", {}).get("container", {}).get("tags") or [])
        created = datetime.fromisoformat(item["created_at"].replace("Z", "+00:00"))
        return Version(int(item["id"]), item["name"], created, tags)

    @property
    def label(self) -> str:
        return " ".join(self.tags) if self.tags else "(untagged)"


@dataclass
class Plan:
    protected: set[str]
    root_tags: list[str]
    keep: list[tuple[Version, str]] = field(default_factory=list)
    delete: list[Version] = field(default_factory=list)


def is_ephemeral_tag(tag: str) -> bool:
    return bool(EPHEMERAL_TAG.match(tag))


def is_referrer_tag(tag: str) -> bool:
    return bool(REFERRER_TAG.match(tag))


def is_release_tag(tag: str) -> bool:
    return bool(RELEASE_TAG.match(tag)) or tag in ("latest", "edge")


def is_protected_tag(tag: str) -> bool:
    return not is_ephemeral_tag(tag) and not is_referrer_tag(tag)


def referrer_target(tag: str) -> str:
    return "sha256:" + tag[len("sha256-") :]


def compute_protected(versions: Iterable[Version], fetch_manifest: Callable[[str], dict]) -> tuple[set[str], list[str]]:
    """Closure of every protected root over the registry. Raises Abort if any manifest can't be read."""
    versions = list(versions)
    referrer_of = {referrer_target(t): v.digest for v in versions for t in v.tags if is_referrer_tag(t)}

    protected: set[str] = set()
    root_tags: list[str] = []
    worklist: deque[str] = deque()
    for v in versions:
        for tag in v.tags:
            if is_protected_tag(tag):
                root_tags.append(tag)
                if v.digest not in protected:
                    protected.add(v.digest)
                    worklist.append(v.digest)
    if not root_tags:
        raise Abort("no protected tags found; refusing to treat everything as deletable")

    while worklist:
        digest = worklist.popleft()
        try:
            manifest = fetch_manifest(digest)
        except Exception as e:  # noqa: BLE001 - any failure here must fail closed
            raise Abort(f"could not fetch protected manifest {digest}; refusing to delete anything ({e})") from e
        children = [m.get("digest") for m in manifest.get("manifests", []) if m.get("digest")]
        referrer = referrer_of.get(digest)
        for d in children + ([referrer] if referrer else []):
            if d not in protected:
                protected.add(d)
                worklist.append(d)
    return protected, sorted(set(root_tags), key=version_sort_key)


def version_sort_key(tag: str) -> tuple:
    parts = tag.split(".")
    return tuple((0, int(p)) if p.isdigit() else (1, p) for p in parts)


def check_listing_complete(registry_tags: Iterable[str], versions: Iterable[Version]) -> None:
    """Every protected tag the registry knows must appear in the API listing. build-*/sha256-* tags
    are ignored: a push in flight can land those between the two calls, and a version absent from
    the listing cannot be deleted anyway."""
    api_tags = {t for v in versions for t in v.tags}
    missing = sorted(t for t in set(registry_tags) - api_tags if is_protected_tag(t))
    if missing:
        raise Abort(f"registry tags absent from the API listing (truncated listing?): {' '.join(missing)}")


def build_plan(
    versions: Iterable[Version], protected: set[str], root_tags: list[str], now: datetime, min_age: timedelta
) -> Plan:
    plan = Plan(protected=protected, root_tags=root_tags)
    for v in sorted(versions, key=lambda x: x.created_at, reverse=True):
        if v.digest in protected:
            plan.keep.append((v, ""))
            continue
        # Independent deny-list guard: a release-looking or unrecognised tag outside the protected set
        # means the closure is wrong; stop rather than reason about it.
        for tag in v.tags:
            if is_release_tag(tag) or is_protected_tag(tag):
                raise Abort(f"version {v.id} ({v.digest}) carries tag '{tag}' but is not in the protected set")
        if now - v.created_at < min_age:
            plan.keep.append((v, f"younger than {min_age}"))
            continue
        plan.delete.append(v)
    return plan


# --- HTTP clients -------------------------------------------------------------------------------


class RateLimited(Exception):
    pass


def _request(url: str, headers: dict[str, str], method: str = "GET", retries: int = 3) -> tuple[int, dict[str, str], bytes]:
    last: Exception | None = None
    for _ in range(retries):
        req = urllib.request.Request(url, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                return resp.status, {k.lower(): v for k, v in resp.headers.items()}, resp.read()
        except urllib.error.HTTPError as e:
            if e.code < 500:
                return e.code, {k.lower(): v for k, v in e.headers.items()}, e.read()
            last = e
        except (urllib.error.URLError, TimeoutError) as e:
            last = e
    raise Abort(f"{method} {url} failed after {retries} attempts: {last}")


class GitHubApi:
    def __init__(self, token: str):
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }

    def list_versions(self, owner: str, package: str) -> list[Version]:
        url = f"{API}/orgs/{owner}/packages/container/{urllib.parse.quote(package, safe='')}/versions?per_page=100"
        items: list[dict] = []
        while url:
            status, headers, body = _request(url, self.headers)
            if status != 200:
                raise Abort(f"listing versions for {package} returned HTTP {status}: {body.decode(errors='replace')[:300]}")
            items.extend(json.loads(body))
            url = _next_link(headers.get("link", ""))
        return [Version.from_api(i) for i in items]

    def delete_version(self, owner: str, package: str, version_id: int) -> str:
        """Returns 'deleted', 'gone' (already absent) or raises RateLimited / Abort."""
        url = f"{API}/orgs/{owner}/packages/container/{urllib.parse.quote(package, safe='')}/versions/{version_id}"
        status, headers, body = _request(url, self.headers, method="DELETE")
        if status == 204:
            return "deleted"
        if status == 404:
            return "gone"
        text = body.decode(errors="replace")
        if status == 429 or (status == 403 and ("rate limit" in text.lower() or headers.get("x-ratelimit-remaining") == "0")):
            raise RateLimited(text[:200])
        raise Abort(f"delete of version {version_id} returned HTTP {status}: {text[:300]}")


def _next_link(link_header: str) -> str | None:
    for part in link_header.split(","):
        m = re.match(r'\s*<([^>]+)>;\s*rel="next"', part)
        if m:
            return m.group(1)
    return None


class Registry:
    def __init__(self, repo_path: str, gh_token: str):
        self.repo_path = repo_path
        basic = base64.b64encode(f"x:{gh_token}".encode()).decode()
        status, _, body = _request(
            f"https://{REGISTRY}/token?scope=repository:{repo_path}:pull", {"Authorization": f"Basic {basic}"}
        )
        token = json.loads(body).get("token") if status == 200 else None
        if not token:
            raise Abort(f"could not obtain a registry token for {repo_path} (HTTP {status})")
        self.headers = {"Authorization": f"Bearer {token}", "Accept": MANIFEST_ACCEPT}

    def manifest(self, ref: str) -> dict:
        status, _, body = _request(f"https://{REGISTRY}/v2/{self.repo_path}/manifests/{ref}", self.headers)
        if status != 200:
            raise Abort(f"manifest {ref} returned HTTP {status}")
        return json.loads(body)

    def exists(self, ref: str) -> bool:
        status, _, _ = _request(f"https://{REGISTRY}/v2/{self.repo_path}/manifests/{ref}", self.headers, method="HEAD")
        return status == 200

    def tags(self) -> list[str]:
        status, _, body = _request(f"https://{REGISTRY}/v2/{self.repo_path}/tags/list?n=100000", self.headers)
        if status != 200:
            raise Abort(f"listing registry tags for {self.repo_path} returned HTTP {status}")
        return json.loads(body).get("tags") or []


# --- Driver -------------------------------------------------------------------------------------


def resolve_defaults(argv: list[str]) -> tuple[str, list[str]]:
    if argv:
        owner, packages = argv[0], argv[1:]
    else:
        owner, packages = "", []
    if not owner or not packages:
        repo = os.environ.get("GITHUB_REPOSITORY")
        if not repo:
            repo = subprocess.run(
                ["gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"],
                check=True, capture_output=True, text=True,
            ).stdout.strip()
        repo_owner, repo_name = repo.split("/", 1)
        owner = owner or repo_owner
        packages = packages or [repo_name, f"{repo_name}-server"]
    return owner, packages


def delete_all(
    candidates: list[Version],
    protected: set[str],
    dry_run: bool,
    workers: int,
    out: Callable[[str], None],
    delete: Callable[[Version], str],
) -> tuple[int, int]:
    """Deletes candidates concurrently (I/O bound). A rate-limit hit stops further deletes; the
    versions left over are reported as skipped and picked up by the next run. Returns (deleted, skipped)."""
    stop = threading.Event()

    def one(v: Version) -> str:
        # Final positive re-check immediately before the delete.
        if v.digest in protected:
            raise Abort(f"protected digest {v.digest} reached the delete path")
        if stop.is_set():
            return "skipped"
        if dry_run:
            return "would-delete"
        try:
            return delete(v)
        except RateLimited:
            stop.set()
            return "rate-limited"

    deleted = skipped = 0
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        for v, result in zip(candidates, pool.map(one, candidates)):
            if result == "would-delete":
                out(f"WOULD DELETE [{v.id}] {v.label}")
                deleted += 1
            elif result == "deleted":
                out(f"DELETE [{v.id}] {v.label}")
                deleted += 1
            elif result == "gone":
                out(f"DELETE [{v.id}] {v.label} (already deleted)")
                deleted += 1
            elif result == "rate-limited":
                out(f"SKIP   [{v.id}] {v.label} (API rate limit reached; next run continues)")
                skipped += 1
            else:
                out(f"SKIP   [{v.id}] {v.label} (rate limited; next run continues)")
                skipped += 1
    return deleted, skipped


def sweep(
    owner: str, package: str, gh_token: str, dry_run: bool, min_age: timedelta, workers: int = 8, out=print
) -> tuple[int, int]:
    repo_path = f"{owner.lower()}/{package.lower()}"
    out(f"Fetching package versions for {owner}/{package}...")
    api = GitHubApi(gh_token)
    versions = api.list_versions(owner, package)
    out(f"Found {len(versions)} total package versions.")

    registry = Registry(repo_path, gh_token)
    check_listing_complete(registry.tags(), versions)
    protected, root_tags = compute_protected(versions, registry.manifest)
    out(f"Protected roots: {' '.join(root_tags)}")
    out(f"Protected set: {len(protected)} digests.")
    out("")

    plan = build_plan(versions, protected, root_tags, datetime.now(timezone.utc), min_age)
    for v, reason in plan.keep:
        out(f"KEEP   [{v.id}] {v.label}" + (f" ({reason})" if reason else ""))

    deleted, skipped = delete_all(
        plan.delete, protected, dry_run, workers, out, lambda v: api.delete_version(owner, package, v.id)
    )

    if not dry_run:
        out("")
        out("Verifying protected tags and digests still resolve...")
        for tag in root_tags:
            if not registry.exists(tag):
                raise Abort(f"protected tag '{tag}' no longer resolves in {repo_path}")
        for digest in sorted(protected):
            if not registry.exists(digest):
                raise Abort(f"protected digest {digest} no longer resolves in {repo_path}")
        out(f"All {len(root_tags)} protected tags and {len(protected)} protected digests verified.")

    kept = len(plan.keep) + skipped
    out("")
    out(f"{package}: {'would delete' if dry_run else 'deleted'} {deleted}, {'keep' if dry_run else 'kept'} {kept}.")
    out("")
    return deleted, kept


def main(argv: list[str]) -> int:
    gh_token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not gh_token:
        print("GH_TOKEN must be set to a token with org package delete rights", file=sys.stderr)
        return 2
    dry_run = os.environ.get("DRY_RUN", "0") == "1"
    min_age = timedelta(hours=float(os.environ.get("MIN_AGE_HOURS", "2")))
    workers = int(os.environ.get("DELETE_WORKERS", "8"))
    owner, packages = resolve_defaults(argv)

    total_deleted = total_kept = 0
    try:
        for package in packages:
            d, k = sweep(owner, package, gh_token, dry_run, min_age, workers)
            total_deleted += d
            total_kept += k
    except Abort as e:
        print(f"ABORT: {e}", file=sys.stderr)
        return 1
    verb = "Dry run complete. Would delete" if dry_run else "Cleanup complete. Deleted"
    print(f"{verb} {total_deleted}, {'keep' if dry_run else 'kept'} {total_kept} across all packages.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
