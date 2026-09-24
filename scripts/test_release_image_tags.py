"""Unit tests for scripts/release_image_tags.py. Run: python3 -m unittest scripts/test_release_image_tags.py"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import release_image_tags as rit  # noqa: E402

IMAGE = "ghcr.io/rbc/fogwall"
DIGEST = "sha256:" + "a" * 64


def registry(release_label, **tags):
    """A fake label reader: the image being promoted carries release_label; tags maps tag -> label.
    A tag absent from the mapping does not exist; a tag mapped to None has no version label."""
    labels = {f"{IMAGE}@{DIGEST}": release_label}
    labels.update({f"{IMAGE}:{tag.replace('_', '.')}": label for tag, label in tags.items()})
    reads = []

    def read(ref):
        reads.append(ref)
        return labels.get(ref, rit.MISSING)

    read.reads = reads
    return read


def tags(release, read):
    return rit.image_tags(IMAGE, DIGEST, release, read_label=read)


# Convenience tag names as kwargs: _1_4 -> "1.4", _1 -> "1".
def on(**kw):
    return {k.lstrip("_"): v for k, v in kw.items()}


class ImageTagsTest(unittest.TestCase):
    def test_newest_release_moves_every_convenience_tag(self):
        read = registry("1.4.4", **on(_1_4="1.4.3", _1="1.4.3", latest="1.4.3"))
        self.assertEqual(tags("v1.4.4", read), ["1.4.4", "1.4", "1", "latest"])

    def test_new_minor_creates_its_line_and_moves_major_and_latest(self):
        read = registry("1.5.0", **on(_1_4="1.4.3", _1="1.4.3", latest="1.4.3"))
        self.assertEqual(tags("v1.5.0", read), ["1.5.0", "1.5", "1", "latest"])

    def test_patch_on_older_line_after_newer_minor_moves_only_its_minor(self):
        read = registry("1.4.4", **on(_1_4="1.4.3", _1_5="1.5.0", _1="1.5.0", latest="1.5.0"))
        self.assertEqual(tags("v1.4.4", read), ["1.4.4", "1.4"])

    def test_patch_on_older_major_after_newer_major_moves_its_minor_and_major(self):
        read = registry("1.5.1", **on(_1_5="1.5.0", _1="1.5.0", _2="2.0.0", latest="2.0.0"))
        self.assertEqual(tags("v1.5.1", read), ["1.5.1", "1.5", "1"])

    def test_patch_after_rollback_takes_latest_back(self):
        # 1.5.0 was bad; latest and 1 were retagged by hand back to 1.4.3.
        read = registry("1.4.4", **on(_1_4="1.4.3", _1_5="1.5.0", _1="1.4.3", latest="1.4.3"))
        self.assertEqual(tags("v1.4.4", read), ["1.4.4", "1.4", "1", "latest"])

    def test_republishing_the_same_release_is_idempotent(self):
        read = registry("1.4.3", **on(_1_4="1.4.3", _1="1.4.3", latest="1.4.3"))
        self.assertEqual(tags("v1.4.3", read), ["1.4.3", "1.4", "1", "latest"])

    def test_older_patch_moves_nothing_but_its_version(self):
        read = registry("1.4.2", **on(_1_4="1.4.3", _1="1.4.3", latest="1.4.3"))
        self.assertEqual(tags("v1.4.2", read), ["1.4.2"])

    def test_tags_whose_image_has_no_semver_label_move(self):
        read = registry("1.5.0", **on(_1_4="build-9244818", _1="build-9244818", latest=None))
        self.assertEqual(tags("v1.5.0", read), ["1.5.0", "1.5", "1", "latest"])

    def test_first_release_creates_every_tag(self):
        self.assertEqual(tags("v1.0.0", registry("1.0.0")), ["1.0.0", "1.0", "1", "latest"])

    def test_versions_compare_numerically(self):
        read = registry("1.10.0", **on(_1="1.9.3", latest="1.9.3"))
        self.assertEqual(tags("v1.10.0", read), ["1.10.0", "1.10", "1", "latest"])
        read = registry("1.9.4", **on(_1_9="1.9.3", _1="1.10.0", latest="1.10.0"))
        self.assertEqual(tags("v1.9.4", read), ["1.9.4", "1.9"])

    def test_release_beats_its_own_pre_release_on_a_tag(self):
        read = registry("1.5.0", **on(_1_5="1.5.0-rc.1", _1="1.5.0-rc.1", latest="1.5.0-rc.1"))
        self.assertEqual(tags("v1.5.0", read), ["1.5.0", "1.5", "1", "latest"])

    def test_snapshot_on_a_tag_holds_back_an_older_release(self):
        read = registry("1.4.4", **on(_1_4="1.4.3", _1="1.5.0-SNAPSHOT", latest="1.5.0-SNAPSHOT"))
        self.assertEqual(tags("v1.4.4", read), ["1.4.4", "1.4"])

    def test_pre_release_gets_only_its_version_and_reads_no_tags(self):
        read = registry("1.5.0-rc.2", **on(latest="1.4.3"))
        self.assertEqual(tags("v1.5.0-rc.2", read), ["1.5.0-rc.2"])
        self.assertEqual(read.reads, [f"{IMAGE}@{DIGEST}"])

    def test_image_labelled_with_another_version_is_refused(self):
        for label in ["1.4.4-SNAPSHOT", "1.4.3", "build-9244818", None]:
            with self.assertRaises(ValueError, msg=label):
                tags("v1.4.4", registry(label, latest="1.4.3"))

    def test_missing_release_image_is_an_error(self):
        with self.assertRaises(rit.RegistryError):
            tags("v1.4.4", registry(rit.MISSING))

    def test_malformed_release_is_rejected(self):
        for bad in ["1.4.4", "v1.4", "v1.4.4.1", "release-1.4.4", ""]:
            with self.assertRaises(ValueError, msg=bad):
                tags(bad, registry("1.4.4"))

    def test_registry_error_is_not_mistaken_for_a_missing_tag(self):
        def read(ref):
            if ref.endswith(":latest"):
                raise rit.RegistryError("403 Forbidden")
            return "1.4.4" if "@" in ref else "1.4.3"

        with self.assertRaises(rit.RegistryError):
            tags("v1.4.4", read)


if __name__ == "__main__":
    unittest.main()
