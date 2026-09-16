"""Unit tests for scripts/cleanup_interim_images.py. Run: python3 -m unittest scripts/test_cleanup_interim_images.py"""

import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import cleanup_interim_images as cleanup  # noqa: E402

NOW = datetime(2026, 9, 16, 12, 0, tzinfo=timezone.utc)
OLD = NOW - timedelta(days=30)
MIN_AGE = timedelta(hours=2)


def digest(n: int) -> str:
    return "sha256:" + f"{n:064x}"


class Fixture:
    """Builds a package listing and a fake registry in the shape docker/build-push-action produces:
    index -> {amd64, arm64, attestation x2}; referrer index sha256-<index digest> -> {sigstore bundle}."""

    def __init__(self):
        self.versions = []
        self.manifests = {}
        self._next = 1

    def _d(self):
        d = digest(self._next)
        self._next += 1
        return d

    def add(self, digest_: str, tags=(), created=OLD):
        self.versions.append(cleanup.Version(len(self.versions) + 1000, digest_, created, tuple(tags)))
        return digest_

    def image(self, tags, created=OLD):
        index = self._d()
        children = [self._d() for _ in range(4)]
        referrer = self._d()
        referrer_child = self._d()
        self.manifests[index] = {"manifests": [{"digest": c} for c in children]}
        self.manifests[referrer] = {"manifests": [{"digest": referrer_child}]}
        for c in children + [referrer_child]:
            self.manifests[c] = {"layers": []}
        self.add(index, tags, created)
        for c in children:
            self.add(c, (), created)
        self.add(referrer, (f"sha256-{index[len('sha256:'):]}",), created)
        self.add(referrer_child, (), created)
        return index

    def orphan(self, created=OLD):
        return self.add(self._d(), (), created)

    def fetch(self, d):
        if d not in self.manifests:
            raise KeyError(d)
        return self.manifests[d]

    def plan(self):
        protected, roots = cleanup.compute_protected(self.versions, self.fetch)
        return cleanup.build_plan(self.versions, protected, roots, NOW, MIN_AGE)


class TagClassification(unittest.TestCase):
    def test_release_like_tags_are_protected_roots(self):
        for tag in ["1", "1.4", "1.4.2", "v1.4.2", "1.5.0-rc1", "latest", "edge", "foo", "build-xyz"]:
            self.assertTrue(cleanup.is_protected_tag(tag), tag)

    def test_release_deny_list(self):
        for tag in ["1", "1.4", "1.4.2", "v1.4.2", "1.5.0-rc1", "latest", "edge"]:
            self.assertTrue(cleanup.is_release_tag(tag), tag)
        for tag in ["build-abc123", "sha256-" + "a" * 64, "foo"]:
            self.assertFalse(cleanup.is_release_tag(tag), tag)

    def test_ephemeral_and_referrer_tags_are_not_roots(self):
        self.assertFalse(cleanup.is_protected_tag("build-7ca7afc"))
        self.assertFalse(cleanup.is_protected_tag("sha256-" + "0" * 64))
        self.assertTrue(cleanup.is_protected_tag("sha256-" + "0" * 63))  # malformed -> protected


class Closure(unittest.TestCase):
    def test_release_closure_is_kept_and_build_only_closure_is_deleted(self):
        fx = Fixture()
        release = fx.image(["1.4.2", "1.4", "1", "latest", "build-d1ce426"])
        fx.image(["build-9d425a4"])
        fx.orphan()
        fx.orphan()
        plan = fx.plan()
        self.assertEqual(len(plan.protected), 7)
        self.assertIn(release, plan.protected)
        self.assertEqual(len(plan.keep), 7)
        self.assertEqual(len(plan.delete), 7 + 2)
        for v in plan.delete:
            self.assertNotIn(v.digest, plan.protected)
            self.assertTrue(all(not cleanup.is_release_tag(t) for t in v.tags))

    def test_edge_shares_digest_with_build_tag(self):
        fx = Fixture()
        fx.image(["build-8e6c463", "edge"])
        plan = fx.plan()
        self.assertEqual(len(plan.delete), 0)
        self.assertEqual(plan.root_tags, ["edge"])

    def test_unknown_tag_is_protected(self):
        fx = Fixture()
        fx.image(["1.4.2"])
        fx.image(["something-new"])
        plan = fx.plan()
        self.assertEqual(len(plan.delete), 0)
        self.assertIn("something-new", plan.root_tags)

    def test_fetch_failure_of_protected_manifest_aborts(self):
        fx = Fixture()
        fx.image(["1.4.2"])
        fx.add(digest(999), ["1.4.3"])  # no manifest in the registry
        with self.assertRaises(cleanup.Abort):
            fx.plan()

    def test_no_protected_tags_aborts(self):
        fx = Fixture()
        fx.image(["build-abc"])
        with self.assertRaises(cleanup.Abort):
            fx.plan()

    def test_orphaned_referrer_of_deleted_build_is_deleted(self):
        fx = Fixture()
        fx.image(["1.4.2"])
        stale = fx.add(fx._d(), [f"sha256-{'b' * 64}"])
        plan = fx.plan()
        self.assertEqual([v.digest for v in plan.delete], [stale])


class Guards(unittest.TestCase):
    def test_release_tag_outside_protected_set_aborts(self):
        fx = Fixture()
        fx.image(["1.4.2"])
        protected, roots = cleanup.compute_protected(fx.versions, fx.fetch)
        with self.assertRaises(cleanup.Abort):
            cleanup.build_plan(fx.versions, set(), roots, NOW, MIN_AGE)

    def test_young_versions_are_kept(self):
        fx = Fixture()
        fx.image(["1.4.2"])
        fx.image(["build-abc1234"], created=NOW - timedelta(minutes=30))
        fx.orphan(created=NOW - timedelta(minutes=1))
        plan = fx.plan()
        self.assertEqual(len(plan.delete), 0)
        self.assertEqual(sum(1 for _, reason in plan.keep if reason), 8)

    def test_registry_tag_missing_from_listing_aborts(self):
        fx = Fixture()
        index = fx.image(["1.4.2"])
        cleanup.check_listing_complete(["1.4.2", f"sha256-{index[len('sha256:'):]}"], fx.versions)
        # A build in flight between the two calls is not a truncated listing.
        cleanup.check_listing_complete(["1.4.2", "build-0123abc", "sha256-" + "f" * 64], fx.versions)
        with self.assertRaises(cleanup.Abort):
            cleanup.check_listing_complete(["1.4.2", "1.4.1"], fx.versions)
        with self.assertRaises(cleanup.Abort):
            cleanup.check_listing_complete(["1.4.2", "unexpected"], fx.versions)


class Deleting(unittest.TestCase):
    def _candidates(self, n):
        return [cleanup.Version(i, digest(100 + i), OLD, ()) for i in range(n)]

    def test_deletes_concurrently_and_counts_gone_as_deleted(self):
        seen = []
        lock = __import__("threading").Lock()

        def delete(v):
            with lock:
                seen.append(v.id)
            return "gone" if v.id % 2 else "deleted"

        deleted, skipped = cleanup.delete_all(self._candidates(20), set(), False, 4, lambda _: None, delete)
        self.assertEqual((deleted, skipped), (20, 0))
        self.assertEqual(sorted(seen), list(range(20)))

    def test_rate_limit_stops_further_deletes(self):
        calls = []

        def delete(v):
            calls.append(v.id)
            if len(calls) >= 3:
                raise cleanup.RateLimited("secondary rate limit")
            return "deleted"

        deleted, skipped = cleanup.delete_all(self._candidates(50), set(), False, 1, lambda _: None, delete)
        self.assertEqual(deleted, 2)
        self.assertEqual(skipped, 48)
        self.assertEqual(len(calls), 3)

    def test_dry_run_calls_nothing(self):
        deleted, skipped = cleanup.delete_all(
            self._candidates(5), set(), True, 4, lambda _: None, lambda v: self.fail("must not delete")
        )
        self.assertEqual((deleted, skipped), (5, 0))

    def test_protected_digest_in_candidates_aborts(self):
        cands = self._candidates(3)
        with self.assertRaises(cleanup.Abort):
            cleanup.delete_all(cands, {cands[1].digest}, False, 2, lambda _: None, lambda v: "deleted")


class Parsing(unittest.TestCase):
    def test_next_link(self):
        header = '<https://api.github.com/x?page=2>; rel="next", <https://api.github.com/x?page=9>; rel="last"'
        self.assertEqual(cleanup._next_link(header), "https://api.github.com/x?page=2")
        self.assertIsNone(cleanup._next_link('<https://api.github.com/x?page=1>; rel="prev"'))

    def test_version_from_api(self):
        v = cleanup.Version.from_api(
            {"id": 5, "name": digest(1), "created_at": "2026-09-16T08:25:12Z", "metadata": {"container": {"tags": ["1.4.2"]}}}
        )
        self.assertEqual(v.tags, ("1.4.2",))
        self.assertEqual(v.created_at.tzinfo, timezone.utc)
        untagged = cleanup.Version.from_api({"id": 6, "name": digest(2), "created_at": "2026-09-16T08:25:12Z", "metadata": {}})
        self.assertEqual(untagged.label, "(untagged)")

    def test_root_tags_sort_numerically(self):
        fx = Fixture()
        fx.image(["1.10.0"])
        fx.image(["1.9.0", "edge"])
        _, roots = cleanup.compute_protected(fx.versions, fx.fetch)
        self.assertEqual(roots, ["1.9.0", "1.10.0", "edge"])


if __name__ == "__main__":
    unittest.main()
