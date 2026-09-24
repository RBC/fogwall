#!/usr/bin/env python3
"""Prints the image tags a release should move, deciding from the version label on each tag's current image.

Every image carries org.opencontainers.image.version, set at build time from build.gradle. A
release always gets its exact version tag. Each convenience tag (X.Y, X, latest) moves only when
the release is at least as new as the version on the image it points at now, so a patch on an older
line never pulls a tag backwards, and a tag rolled back by hand stays put until something newer
than the rollback target ships. A tag that does not exist yet, or whose image has no semver label,
moves. A pre-release gets its exact version tag only.

Before deciding anything, the image being promoted must be labelled with exactly the release's
version; a build of any other version (a -SNAPSHOT, a different release) is refused.

Registry reads go through skopeo. A missing tag ("manifest unknown") is expected; any other
registry error aborts rather than being mistaken for a missing tag.

Usage: ./scripts/release_image_tags.py <image> <digest> <release tag>
   e.g. ./scripts/release_image_tags.py ghcr.io/rbc/fogwall sha256:... v1.4.4
Prints space-separated tags. Standard library only; unit tests live in scripts/test_release_image_tags.py.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from typing import NamedTuple

LABEL = "org.opencontainers.image.version"
VERSION = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?$")


class Version(NamedTuple):
    major: int
    minor: int
    patch: int
    pre: str | None

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}{self.pre or ''}"

    def order(self) -> tuple[int, int, int, int]:
        """A pre-release sorts before the release it precedes."""
        return (self.major, self.minor, self.patch, 0 if self.pre else 1)


def parse(value: str | None) -> Version | None:
    m = VERSION.match(value or "")
    if not m:
        return None
    return Version(int(m[1]), int(m[2]), int(m[3]), m[4])


class RegistryError(Exception):
    pass


MISSING = object()


def skopeo_label(ref: str):
    """The version label on ref's linux/amd64 image, None when unlabelled, MISSING when the tag does not exist."""
    result = subprocess.run(
        ["skopeo", "inspect", "--override-os", "linux", "--override-arch", "amd64", f"docker://{ref}"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        if "manifest unknown" in result.stderr:
            return MISSING
        raise RegistryError(f"inspecting {ref}: {result.stderr.strip()}")
    return (json.loads(result.stdout).get("Labels") or {}).get(LABEL)


def image_tags(image: str, digest: str, release: str, read_label=skopeo_label) -> list[str]:
    version = parse(release)
    if version is None or not release.startswith("v"):
        raise ValueError(f"not a release tag: {release!r} (expected vX.Y.Z or vX.Y.Z-suffix)")

    built = read_label(f"{image}@{digest}")
    if built is MISSING:
        raise RegistryError(f"{image}@{digest} does not exist")
    if built != str(version):
        raise ValueError(f"{image}@{digest} is labelled {LABEL}={built!r}, not {str(version)!r}; refusing to publish it as {release}")

    tags = [str(version)]
    if version.pre:
        return tags
    for tag in (f"{version.major}.{version.minor}", f"{version.major}", "latest"):
        current = read_label(f"{image}:{tag}")
        on_tag = None if current is MISSING else parse(current)
        if on_tag is None or on_tag.order() <= version.order():
            tags.append(tag)
    return tags


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        print(" ".join(image_tags(argv[1], argv[2], argv[3])))
    except (ValueError, RegistryError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
