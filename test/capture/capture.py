#!/usr/bin/env python3
"""
capture.py — one-shot producer of the Playwright UI-regression fixture database.

Deliberately static and linear: every scenario is written out below, top to bottom, so this file IS the
specification of what the fixture database contains. Standard library only; no third-party packages.

  1. Rewrites fogwall-playwright.yml placeholders to YOUR real identity (mapping.env) into a temp profile.
  2. Creates a private, randomly-suffixed test repo on GitHub, GitLab, Codeberg and gitea.com with your PATs.
  3. Boots the dashboard on an H2 *file* database with that profile (+ OAuth app creds from secrets.env).
  4. Seeds a couple of DB-sourced profile rows via the API, then pauses so you can link GitHub and GitLab via
     OAuth on the profile page.
  5. Runs the scenarios: real pushes through real providers, transparent proxy over HTTP plus one server-mode
     push over SSH. Pauses once more so you can approve / reject / cancel the reviewable ones in the dashboard as
     the right user (reviewer or dev), then re-pushes the approved ones.
  6. Runs the proposals scenarios: gh (GitHub), glab (GitLab), fj (Codeberg) and tea (gitea.com) each open, edit
     and close a pull/merge request and an issue through the provider's proposals listener, over TLS from a
     throwaway CA generated for the run.
  7. Stops the app, deletes secret/session/cache rows, dumps the database to SQL, scrubs every real value back
     to its placeholder, verifies nothing personal is left, deletes the repos, and writes:
       fogwall-dashboard/frontend/tests/fixtures/fogwall.sql      the database
       fogwall-dashboard/frontend/tests/fixtures/manifest.json    scenario name → push id / ref

Prereqs: test/capture/mapping.env (+ optional secrets.env), the PAT files it names, ssh-agent holding the key that
is registered on github.com, git, ssh-keygen, openssl, the four SCM CLIs (gh, glab, tea, fj), and nothing else
listening on :8080 / :2222 / :8443 or the proposals ports (8481-8484). The PATs must be able to create AND delete
repositories: GitHub `repo` + `delete_repo`; GitLab `api`; Codeberg/Gitea `write:user` + `write:repository` +
`read:user` — and, for the proposals scenarios, open issues and pull requests: GitHub `repo` plus `read:org`
(`gh pr edit` queries the org's teams); Gitea `write:issue`. The CLIs are run against throwaway config directories; your real logins are untouched.

Env knobs: KEEP_WORK=1 keeps the temp dir on exit; SKIP_OAUTH=1 skips the manual pause (dev's github/gitlab
identities are then seeded unverified so the run still resolves — never commit such a dump); SKIP_PUSHES=1 runs only
the proposals scenarios; PUSH_TIMEOUT=<s> caps how long a server-mode push may be held open (default 90).
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# Paths, logging
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
HERE = Path(__file__).resolve().parent
ROOT = Path(subprocess.check_output(["git", "-C", str(HERE), "rev-parse", "--show-toplevel"], text=True).strip())
FIXTURES = ROOT / "fogwall-dashboard/frontend/tests/fixtures"
PROFILE = FIXTURES / "fogwall-playwright.yml"
FOGWALL = "http://localhost:8080"
API_KEY = os.environ.get("FOGWALL_API_KEY", "change-me-in-production")
KEEP_WORK = os.environ.get("KEEP_WORK") == "1"
SKIP_OAUTH = os.environ.get("SKIP_OAUTH") == "1"
SKIP_PUSHES = os.environ.get("SKIP_PUSHES") == "1"
PUSH_TIMEOUT = int(os.environ.get("PUSH_TIMEOUT", "90"))
# providers.<name>.proposals.port in fogwall-playwright.yml — the listeners the CLIs are pointed at.
PROPOSAL_PORTS = {"github": 8481, "gitlab": 8482, "codeberg": 8483, "gitea": 8484}


def log(msg: str) -> None:
    print(f"\n\033[1;36m==> {msg}\033[0m", flush=True)


def note(msg: str) -> None:
    print(f"    {msg}", flush=True)


def warn(msg: str) -> None:
    print(f"\033[1;33mWARN: {msg}\033[0m", file=sys.stderr, flush=True)


class Fatal(Exception):
    pass


def indent(text: str) -> None:
    for line in text.rstrip("\n").splitlines():
        print(f"    | {line}", flush=True)


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 0. Inputs — mapping.env / secrets.env are bash files (secrets.env runs jq); source them through bash once.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
def load_env_files() -> dict[str, str]:
    mapping = HERE / "mapping.env"
    if not mapping.is_file():
        raise Fatal(f"missing {mapping} — copy mapping.env.example and fill it in")
    secrets = HERE / "secrets.env"
    if not secrets.is_file():
        warn("no secrets.env — OAuth linking will be unavailable; identity rows won't be provider-verified")
    script = f"set -a; source {mapping}; " + (f"source {secrets}; " if secrets.is_file() else "") + "env -0"
    raw = subprocess.check_output(["bash", "-c", script], cwd=HERE)
    env: dict[str, str] = {}
    for item in raw.split(b"\0"):
        if b"=" in item:
            k, v = item.decode().split("=", 1)
            env[k] = v
    return env


def read_pat(env: dict[str, str], name: str) -> str:
    val = env.get(f"{name}_PAT", "")
    file = Path(os.path.expanduser(env.get(f"{name}_PAT_FILE", "")))
    if not val and file.is_file():
        val = file.read_text().strip()
    if not val:
        raise Fatal(f"{name}_PAT not set and {file} not found")
    return val


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 1. Placeholder ↔ real mapping
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
FIX = {
    "handle": "fixture-dev",
    "name": "Fixture Developer",
    "email": "fixture-dev@example.com",
    "alt_email": "fixture-alt@example.com",
    "domain": "example.org",
    "repo": "fogwall-fixture",
}


def fingerprint(pubkey_file: Path) -> str:
    out = subprocess.check_output(["ssh-keygen", "-lf", str(pubkey_file)], text=True)
    return out.split()[1]


def key_blob(pubkey_file: Path) -> str:
    parts = pubkey_file.read_text().split()
    return f"{parts[0]} {parts[1]}"


@dataclass
class Identity:
    handle: str
    name: str
    email: str
    alt_email: str
    pubkey_file: Path
    repo_name: str  # fogwall-fixture-<random>

    @property
    def domain(self) -> str:
        return self.email.rsplit("@", 1)[1]

    def mapping(self) -> list[tuple[str, str]]:
        """Ordered (placeholder, real) pairs. Longer / more specific values first so the forward scrub can't
        mangle an email that happens to contain the handle, and the suffixed repo name is replaced before the
        bare handle inside it."""
        pairs = [
            (FIX["repo"], self.repo_name),
            (key_blob(FIXTURES / "fixture-dev.pub"), key_blob(self.pubkey_file)),
            (fingerprint(FIXTURES / "fixture-dev.pub"), fingerprint(self.pubkey_file)),
            (FIX["email"], self.email),
        ]
        if self.alt_email:
            pairs.append((FIX["alt_email"], self.alt_email))
        pairs += [
            (FIX["name"], self.name),
            (FIX["handle"], self.handle),
            # The domain-allow regex in the profile is YAML-escaped: example\\.org → coopernetes\\.ca
            (FIX["domain"].replace(".", "\\\\."), self.domain.replace(".", "\\\\.")),
            (FIX["domain"], self.domain),
        ]
        return pairs

    def to_real(self, text: str) -> str:
        for placeholder, real in self.mapping():
            text = text.replace(placeholder, real)
        return text

    def to_placeholder(self, text: str) -> str:
        for placeholder, real in self.mapping():
            text = text.replace(real, placeholder)
        return scrub_unknown_emails(text)


# Emails the fixture legitimately contains. Anything else (typically addresses the OAuth import pulled off your
# provider accounts, which mapping.env cannot know about) is replaced by a numbered placeholder.
KNOWN_EMAIL_DOMAINS = {"example.com", "example.net", "example.org", "anthropic.com", "github.com", "internal.corp.net"}
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def scrub_unknown_emails(text: str) -> str:
    # GitHub's noreply address carries the numeric account id: keep the shape, drop the id.
    text = re.sub(r"\b\d+\+fixture-dev@users\.noreply\.github\.com", "00000000+fixture-dev@users.noreply.github.com", text)
    unknown: dict[str, str] = {}
    for email in sorted(set(EMAIL_RE.findall(text))):
        domain = email.rsplit("@", 1)[1].lower()
        if domain in KNOWN_EMAIL_DOMAINS or domain.endswith(".example.net") or domain == "users.noreply.github.com":
            continue
        unknown.setdefault(email, f"fixture-extra-{len(unknown) + 1}@example.com")
    for real, placeholder in unknown.items():
        text = text.replace(real, placeholder)
    return text


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 2. Provider APIs — ephemeral private repos, one per provider, marked so cleanup never touches a real repo
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
REPO_MARK = "fogwall UI fixture capture — safe to delete"


def http(method: str, url: str, headers: dict[str, str], body: dict | None = None) -> tuple[int, dict | list | None]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={**headers, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw.strip() else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"message": raw.decode(errors="replace")[:300]}


@dataclass
class Provider:
    name: str
    host: str
    api: str
    auth: dict[str, str]
    pat: str
    create_url: str
    create_body: dict
    repo_url: str  # format with owner/name
    created: bool = False

    def _desc(self, owner: str, name: str) -> str | None:
        _, j = http("GET", self.repo_url.format(owner=owner, name=name), self.auth)
        return (j or {}).get("description") if isinstance(j, dict) else None

    def delete(self, owner: str, name: str) -> None:
        if self._desc(owner, name) != REPO_MARK:
            return
        http("DELETE", self.repo_url.format(owner=owner, name=name), self.auth)

    def create(self, owner: str, name: str) -> None:
        self.delete(owner, name)  # leftover from an aborted run
        code, j = http("POST", self.create_url, self.auth, {**self.create_body, "name": name, "description": REPO_MARK})
        if not (isinstance(j, dict) and j.get("id")):
            msg = j.get("message") or j.get("error") or j if isinstance(j, dict) else j
            raise Fatal(f"could not create {name} on {self.name} (HTTP {code}): {str(msg)[:300]}")
        self.created = True
        note(f"created {self.name}: {owner}/{name} (private)")


def providers(pats: dict[str, str], owner: str) -> list[Provider]:
    forge = lambda name, host: Provider(  # noqa: E731 — Gitea / Forgejo / Codeberg share one API
        name=name,
        host=host,
        api=f"https://{host}/api/v1",
        auth={"Authorization": f"token {pats[name]}"},
        pat=pats[name],
        create_url=f"https://{host}/api/v1/user/repos",
        create_body={"private": True, "auto_init": True, "default_branch": "main"},
        repo_url=f"https://{host}/api/v1/repos/{{owner}}/{{name}}",
    )
    return [
        Provider(
            name="github",
            host="github.com",
            api="https://api.github.com",
            auth={"Authorization": f"Bearer {pats['github']}", "Accept": "application/vnd.github+json"},
            pat=pats["github"],
            create_url="https://api.github.com/user/repos",
            create_body={"private": True, "auto_init": True},
            repo_url="https://api.github.com/repos/{owner}/{name}",
        ),
        Provider(
            name="gitlab",
            host="gitlab.com",
            api="https://gitlab.com/api/v4",
            auth={"PRIVATE-TOKEN": pats["gitlab"]},
            pat=pats["gitlab"],
            create_url="https://gitlab.com/api/v4/projects",
            create_body={"visibility": "private", "initialize_with_readme": True},
            repo_url="https://gitlab.com/api/v4/projects/{owner}%2F{name}",
        ),
        forge("codeberg", "codeberg.org"),
        forge("gitea", "gitea.com"),
    ]


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 3. fogwall process + dashboard API
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
def gradle(*args: str, env: dict[str, str] | None = None, **kw) -> subprocess.CompletedProcess:
    return subprocess.run(["./gradlew", "-q", *args], cwd=ROOT, env=env, **kw)


def health() -> bool:
    try:
        with urllib.request.urlopen(f"{FOGWALL}/api/health", timeout=3) as r:
            return r.status == 200
    except Exception:
        return False


def boot(work: Path, conf_dir: Path, db_path: Path, extra_env: dict[str, str]) -> subprocess.Popen:
    if health():
        raise Fatal(f"something is already listening on {FOGWALL}")
    env = {**os.environ, **extra_env}
    env.update(
        FOGWALL_CONFIG_PROFILES="playwright",
        FOGWALL_DATABASE_TYPE="h2-file",
        FOGWALL_DATABASE_PATH=str(db_path),
        FOGWALL_API_KEY=API_KEY,
    )
    logfile = open(work / "fogwall.log", "wb")
    proc = subprocess.Popen(
        ["./gradlew", "-q", ":fogwall-dashboard:run", f"-PconfigDir={conf_dir}"],
        cwd=ROOT, env=env, stdout=logfile, stderr=subprocess.STDOUT,
    )
    for _ in range(150):
        if health():
            note("up")
            return proc
        if proc.poll() is not None:
            break
        time.sleep(2)
    raise Fatal(f"fogwall did not come up; see {work / 'fogwall.log'}")


def stop_app() -> None:
    gradle(":fogwall-dashboard:stop", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(30):
        if not health():
            return
        time.sleep(1)
    warn("fogwall still answering after stop; the H2 file may be locked")


def api(method: str, path: str, body: dict | None = None):
    code, j = http(method, f"{FOGWALL}{path}", {"X-Api-Key": API_KEY}, body)
    return code, j


def push_list(query: str) -> list[dict]:
    _, j = api("GET", f"/api/push?{query}")
    if isinstance(j, dict):
        j = j.get("records", [])
    return j or []


def push_id_for_ref(ref: str, status: str | None = None) -> str | None:
    q = f"limit=30" + (f"&status={status}" if status else "")
    for rec in push_list(q):
        if rec.get("branch") == ref:
            return rec["id"]
    return None


def newest_push_id() -> str | None:
    recs = push_list("limit=1")
    return recs[0]["id"] if recs else None


def cancel(push_id: str, user: str) -> bool:
    """Cancel via the admin API key — only used to release a server-mode push that unexpectedly passed."""
    _, j = api("POST", f"/api/push/{push_id}/cancel", {"reviewerUsername": user})
    if isinstance(j, dict) and j.get("error"):
        warn(f"cancel refused: {j['error']}")
        return False
    return True


def push_status(push_id: str) -> str:
    _, j = api("GET", f"/api/push/{push_id}")
    return (j or {}).get("status", "?") if isinstance(j, dict) else "?"


def wait_for_review(expect: dict[str, str], timeout: int = 1800) -> None:
    """Block until every push id in `expect` has left PENDING; warn about any whose status differs from expected."""
    deadline = time.time() + timeout
    remaining = dict(expect)
    while remaining and time.time() < deadline:
        for pid in list(remaining):
            st = push_status(pid)
            if st != "PENDING":
                if st != remaining[pid]:
                    warn(f"{pid} is {st}, expected {remaining[pid]} — the fixture will carry {st}")
                del remaining[pid]
        if remaining:
            time.sleep(3)
    for pid in remaining:
        warn(f"{pid} still PENDING after {timeout}s — left as is")


def make_tls(work: Path) -> tuple[Path, Path, Path]:
    """A throwaway CA and a localhost leaf signed by it, for the proposals listeners. The CLIs only ever speak HTTPS
    to a custom host, and fj (rustls) refuses a bare self-signed certificate presented as a server certificate, so a
    real (if tiny) chain it is. Returns (certificate chain, key, CA) — the CA is what the CLIs trust via SSL_CERT_FILE."""
    tls = work / "tls"; tls.mkdir()
    ca, ca_key = tls / "ca.pem", tls / "ca-key.pem"
    leaf, key, csr, chain = tls / "leaf.pem", tls / "fogwall-key.pem", tls / "leaf.csr", tls / "fogwall-cert.pem"
    ext = tls / "leaf.ext"
    ext.write_text("subjectAltName=DNS:localhost,IP:127.0.0.1\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n")
    run = lambda *a: subprocess.run(["openssl", *a], cwd=tls, check=True, capture_output=True)  # noqa: E731
    run("req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2", "-keyout", str(ca_key), "-out", str(ca),
        "-subj", "/CN=fogwall capture CA", "-addext", "basicConstraints=critical,CA:TRUE")
    run("req", "-newkey", "rsa:2048", "-nodes", "-keyout", str(key), "-out", str(csr), "-subj", "/CN=localhost")
    run("x509", "-req", "-in", str(csr), "-CA", str(ca), "-CAkey", str(ca_key), "-CAcreateserial", "-days", "2",
        "-out", str(leaf), "-extfile", str(ext))
    chain.write_text(leaf.read_text() + ca.read_text())
    return chain, key, ca


def seed_db_profile_data(handle: str) -> None:
    """DB-sourced (non-config, non-OAuth) profile rows, so the profile/user pages show the 'local' source too."""
    seeds = [
        ("/api/users/reviewer/identities", {"provider": "github", "scmUsername": "fixture-reviewer"}),
        ("/api/users/reviewer/emails", {"email": "reviewer.alt@example.com"}),
    ]
    if SKIP_OAUTH:
        warn("SKIP_OAUTH: seeding dev's github/gitlab identities unverified — for iterating on the script only")
        seeds += [("/api/users/dev/identities", {"provider": prov, "scmUsername": handle}) for prov in ("github", "gitlab")]
    for path, body in seeds:
        code, j = api("POST", path, body)
        if code >= 300:
            warn(f"seed {path} → HTTP {code}: {j}")


def seed_second_github_identity() -> None:
    """A second, hand-typed github identity on dev, AFTER the OAuth link: linking replaces whatever hand-typed identity
    the user held on that provider, so seeding it first leaves nothing. With two accounts on one provider the push
    records can be checked for naming the account the token belongs to rather than the first identity on file."""
    code, j = api("POST", "/api/users/dev/identities", {"provider": "github", "scmUsername": "fixture-dev-alt"})
    if code >= 300:
        warn(f"seed dev's second github identity → HTTP {code}: {j}")


def action_ids(provider: str) -> list[str]:
    _, j = api("GET", f"/api/scm-api-actions?provider={provider}&limit=100")
    return [rec["id"] for rec in (j or [])] if isinstance(j, list) else []


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 4. Git helpers — every scenario is: clone through fogwall, commit, push, (review), record
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
def git(*args: str, cwd: Path, env: dict[str, str] | None = None, timeout: int | None = None) -> tuple[int, str]:
    try:
        p = subprocess.run(["git", *args], cwd=cwd, env=env, text=True, timeout=timeout,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return p.returncode, p.stdout
    except subprocess.TimeoutExpired as e:
        return 124, (e.stdout or b"").decode(errors="replace") if isinstance(e.stdout, bytes) else (e.stdout or "")


@dataclass
class Repo:
    """A fresh clone on a new branch with the developer's real identity configured."""

    dir: Path
    branch: str
    env: dict[str, str] = field(default_factory=lambda: {**os.environ, "GIT_TERMINAL_PROMPT": "0"})

    @property
    def ref(self) -> str:
        return f"refs/heads/{self.branch}"

    def config(self, key: str, value: str) -> None:
        git("config", key, value, cwd=self.dir)

    def commit(self, file: str, message: str, content: str | None = None, *, signoff: bool = True,
               trailers: list[str] = ()) -> None:
        path = self.dir / file
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "a") as f:
            f.write((content if content is not None else f"{message} - {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}") + "\n")
        git("add", file, cwd=self.dir)
        args = ["commit", "-q", "-m", message] + (["-s"] if signoff else [])
        for t in trailers:
            args += ["--trailer", t]
        rc, out = git(*args, cwd=self.dir, env=self.env)
        if rc != 0:
            raise Fatal(f"git commit failed in {self.dir}: {out}")

    def tag(self, name: str, message: str | None = None) -> None:
        args = ["tag", "-a", name, "-m", message] if message else ["tag", name]
        git(*args, cwd=self.dir, env=self.env)

    def push(self, refspec: str | None = None) -> None:
        """Push and print the output. Never raises: rejections are the point. Server mode holds a PASSING push open
        until it is reviewed, so cap the wait and cancel the pending record to release the client."""
        refspec = refspec or self.branch
        rc, out = git("push", "origin", refspec, cwd=self.dir, env=self.env, timeout=PUSH_TIMEOUT)
        indent(out)
        if rc == 124:
            full = refspec if refspec.startswith("refs/") else f"refs/heads/{refspec}"
            pending = push_id_for_ref(full, "PENDING")
            warn(f"push of {refspec} was still held open after {PUSH_TIMEOUT}s — it PASSED where the scenario "
                 f"expected a rejection; canceling {pending or '<unknown>'}")
            if pending:
                cancel(pending, "dev")

    def forward(self, refspec: str | None = None) -> None:
        """Re-push after approval; this time it must reach upstream."""
        rc, out = git("push", "-q", "origin", refspec or self.branch, cwd=self.dir, env=self.env, timeout=300)
        if rc == 0:
            note("forwarded upstream")
        else:
            indent(out)
            warn("re-push after approval did not go through; continuing")

    def upstream_delete(self, direct_url: str, refspec: str | None = None) -> None:
        """Remove what reached the real repo, bypassing fogwall (best effort)."""
        git("push", "-q", direct_url, "--delete", refspec or self.branch, cwd=self.dir, env=self.env, timeout=120)


class Session:
    def __init__(self, work: Path, ident: Identity, pats: dict[str, str]):
        self.work = work
        self.ident = ident
        self.manifest: dict[str, dict[str, str]] = {}
        h = ident.handle
        n = ident.repo_name
        user = "me"  # basic-auth username in the clone URL; identity comes from the PAT, not from this
        self.gh_proxy = f"http://{user}:{pats['github']}@localhost:8080/proxy/github.com/{h}/{n}.git"
        self.gh_server = f"http://{user}:{pats['github']}@localhost:8080/server/github.com/{h}/{n}.git"
        self.gh_direct = f"https://{user}:{pats['github']}@github.com/{h}/{n}.git"
        self.gl_proxy = f"http://{user}:{pats['gitlab']}@localhost:8080/proxy/gitlab.com/{h}/{n}.git"
        self.gl_direct = f"https://oauth2:{pats['gitlab']}@gitlab.com/{h}/{n}.git"
        self.cb_proxy = f"http://{user}:{pats['codeberg']}@localhost:8080/proxy/codeberg.org/{h}/{n}.git"
        self.cb_direct = f"https://{user}:{pats['codeberg']}@codeberg.org/{h}/{n}.git"
        self.gt_proxy = f"http://{user}:{pats['gitea']}@localhost:8080/proxy/gitea.com/{h}/{n}.git"
        self.gt_direct = f"https://{user}:{pats['gitea']}@gitea.com/{h}/{n}.git"
        self.gh_ssh = f"ssh://dev@localhost:2222/github.com/{h}/{n}.git"
        self.pats = pats
        self.slug = f"{h}/{n}"

    def clone(self, url: str, prefix: str, env: dict[str, str] | None = None) -> Repo:
        d = Path(tempfile.mkdtemp(prefix=f"repo-{prefix}-", dir=self.work))
        repo = Repo(dir=d, branch=f"fixture/{prefix}-{int(time.time() * 1000) % 1_000_000:06d}")
        if env:
            repo.env.update(env)
        rc, out = git("clone", "-q", url, str(d), cwd=self.work, env=repo.env, timeout=300)
        if rc != 0:
            indent(out)
            raise Fatal(f"clone failed for {prefix}")
        git("checkout", "-q", "-b", repo.branch, cwd=d)
        repo.config("user.name", self.ident.name)
        repo.config("user.email", self.ident.email)
        return repo

    def record(self, scenario: str, ref: str, push_id: str | None = None) -> str:
        push_id = push_id or push_id_for_ref(ref) or newest_push_id()
        self.manifest[scenario] = {"id": push_id or "", "ref": ref}
        note(f"recorded {scenario} → {push_id or '<none>'}  ({ref})")
        return push_id or ""


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 5. Scenarios
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
def run_scenarios(s: Session) -> None:
    ident = s.ident

    # ── REJECTED, one validator at a time ────────────────────────────────────────────────────────────────────
    # All validation scenarios use the transparent proxy: a rejection there is one buffered response. Server mode
    # would hold the git session open if a check unexpectedly passed; it is used only for the SSH scenario.
    log("reject-author-noreply — author email local part is blocked (noreply@)")
    r = s.clone(s.gh_proxy, "author-noreply")
    r.config("user.email", "noreply@example.com")
    r.commit("notes/noreply.txt", "feat: this commit has a noreply author")
    r.push(); s.record("reject-author-noreply", r.ref)

    log("reject-author-domain — author email domain not in the allow list")
    r = s.clone(s.gh_proxy, "author-domain")
    r.config("user.email", "developer@internal.corp.net")
    r.commit("notes/domain.txt", "feat: this commit comes from an unapproved domain")
    r.push(); s.record("reject-author-domain", r.ref)

    log("reject-message-literal — commit message contains a blocked literal (WIP)")
    r = s.clone(s.gh_proxy, "message-wip")
    r.commit("notes/wip.txt", "WIP: still working on this feature")
    r.push(); s.record("reject-message-literal", r.ref)

    log("reject-message-pattern — commit message matches a blocked pattern (token=...)")
    r = s.clone(s.gh_proxy, "message-pattern")
    r.commit("notes/rotate.txt", "chore: rotate token=ghp_abc123def456 in CI config")
    r.push(); s.record("reject-message-pattern", r.ref)

    # gitleaks ≥ 8.30 ignores AWS's documented "EXAMPLE" key and alphabet-sequence tokens as placeholders, so these
    # are shaped like real credentials (random base32 / base62) — they are not real.
    log("reject-secret — gitleaks finds an AWS access key in the diff")
    r = s.clone(s.gh_proxy, "secret-aws")
    r.commit("aws-credentials", "chore: add deployment credentials",
             "[default]\naws_access_key_id = AKIAQ3EGTX6PVZ2NW7HB\n"
             "aws_secret_access_key = 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")
    r.push(); s.record("reject-secret", r.ref)

    log("reject-diff-literal — diff contains a blocked hostname literal")
    r = s.clone(s.gh_proxy, "diff-literal")
    r.commit("config.yml", "chore: add upstream config",
             "upstream:\n  api: https://internal.corp.example.com/api/v1\n  timeout: 30")
    r.push(); s.record("reject-diff-literal", r.ref)

    log("reject-diff-pattern — diff matches a blocked URL pattern")
    r = s.clone(s.gh_proxy, "diff-pattern")
    r.commit("deploy.sh", "chore: add deployment script",
             "#!/bin/bash\ncurl -X POST http://ci.corp.example.com/deploy -d '{\"version\": \"1.2.3\"}'")
    r.push(); s.record("reject-diff-pattern", r.ref)

    # ── REJECTED by the commit-trailer policy (DCO sign-off, co-author allow-list) ────────────────────────────
    log("reject-trailer-no-signoff — commit lacks a Signed-off-by trailer (DCO required)")
    r = s.clone(s.gh_proxy, "no-signoff")
    r.commit("notes/unsigned.txt", "feat: forgot to sign off", signoff=False)
    r.push(); s.record("reject-trailer-no-signoff", r.ref)

    log("reject-trailer-signoff-mismatch — Signed-off-by present but not the author's email")
    r = s.clone(s.gh_proxy, "signoff-mismatch")
    r.commit("notes/mismatch.txt", "feat: signed off by the wrong person", signoff=False,
             trailers=["Signed-off-by: Someone Else <someone.else@example.com>"])
    r.push(); s.record("reject-trailer-signoff-mismatch", r.ref)

    log("reject-trailer-coauthor-denied — Co-authored-by email outside the allow-list")
    r = s.clone(s.gh_proxy, "coauthor-denied")
    r.commit("notes/coauthor.txt", "feat: paired with an outside contractor",
             trailers=["Co-authored-by: Contractor <contractor@outside.example.net>"])
    r.push(); s.record("reject-trailer-coauthor-denied", r.ref)

    log("pending-trailer-coauthor-allowed — allow-listed co-author, sign-off matches: passes and shows both trailers")
    r = s.clone(s.gh_proxy, "coauthor-ok")
    r.commit("notes/pair.txt", "feat: pair-programmed with an allow-listed co-author",
             trailers=["Co-authored-by: Claude <noreply@anthropic.com>", "Co-authored-by: Pair Partner <pair@example.com>"])
    r.push(); s.record("pending-trailer-coauthor-allowed", r.ref)

    # ── REJECTED for several reasons at once, and for identity ───────────────────────────────────────────────
    log("reject-multiple — six commits, each tripping a different validator")
    r = s.clone(s.gh_proxy, "multi-fail")
    r.config("user.email", "noreply@example.com")
    r.commit("multi/1.txt", "test: commit 1 — noreply author email")
    r.config("user.email", ident.email)
    r.commit("multi/2.txt", "WIP: commit 2 — bad commit message")
    r.commit("ci-config.env", "test: commit 3 — github pat in diff", "GITHUB_TOKEN=ghp_9fK2mQx7Lp4vB8nR3tW6yZ1cH5jD0aE2sG4u")
    r.commit("config.yml", "test: commit 4 — blocked hostname in diff", "upstream:\n  api: https://internal.corp.example.com/api/v1")
    r.config("user.email", "unregistered@example.com")
    r.commit("multi/5.txt", "test: commit 5 — unregistered commit email")
    r.config("user.email", ident.email)
    r.commit("multi/6.txt", "test: commit 6 — missing DCO sign-off", signoff=False)
    r.push(); s.record("reject-multiple", r.ref)

    log("reject-unmapped-identity — codeberg PAT resolves to a login no proxy user claims")
    r = s.clone(s.cb_proxy, "unmapped")
    r.commit("notes/codeberg.txt", "test: identity resolution — codeberg unresolved")
    r.push(); s.record("reject-unmapped-identity", r.ref)

    # ── PENDING (left for a reviewer; nothing reaches upstream) ──────────────────────────────────────────────
    log("pending-branch — new branch, fully verified pusher, every check passes")
    r = s.clone(s.gh_proxy, "pending-branch")
    r.commit("docs/release-notes.md", "docs: add release notes stub")
    r.push(); s.record("pending-branch", r.ref)

    # Tags may only reference commits already validated through a branch push → tag the default-branch head.
    log("pending-tag — annotated tag on the upstream default-branch head")
    r = s.clone(s.gh_proxy, "pending-tag")
    tag = f"v9.9.9-fixture-{int(time.time())}"
    r.tag(tag, f"Release {tag}")
    r.push(tag); s.record("pending-tag", f"refs/tags/{tag}")

    log("pending-gitea — second provider, same pusher (gitea.com)")
    r = s.clone(s.gt_proxy, "gitea")
    r.commit("README.md", "docs: touch readme via fogwall")
    r.push(); s.record("pending-gitea", r.ref)

    log("pending-gitlab-email-warning — identity resolved on gitlab but commit email unregistered (warning, not block)")
    r = s.clone(s.gl_proxy, "gitlab-warn")
    r.config("user.email", "unregistered@example.com")
    r.commit("notes/gitlab.txt", "test: identity resolution — gitlab resolved, email unregistered")
    r.push(); s.record("pending-gitlab-email-warning", r.ref)

    # ── Reviewed interactively: FORWARDED, REJECTED by a reviewer, CANCELED, self-certified, SSH ──────────────
    # Review decisions are made by YOU in the dashboard, as the right user, so attestations carry real reviewer
    # identities (not the API key). Everything is pushed first, then one pause with a checklist.
    log("forwarded-multi-commit — three commits (approve as reviewer)")
    multi = s.clone(s.gh_proxy, "multi-commit")
    multi.commit("src/alpha.txt", "feat(alpha): first of three")
    multi.commit("src/beta.txt", "feat(beta): second of three")
    multi.commit("src/gamma.txt", "fix(gamma): third of three")
    multi.push()
    multi_id = s.record("forwarded-multi-commit", multi.ref)

    log("forwarded-lightweight-tag — lightweight tag on the default-branch head (approve as reviewer)")
    ltag = s.clone(s.gh_proxy, "light-tag")
    tag = f"lightweight-fixture-{int(time.time())}"
    ltag.tag(tag)
    ltag.push(tag)
    ltag_id = s.record("forwarded-lightweight-tag", f"refs/tags/{tag}")

    log("rejected-by-reviewer (reject as reviewer, with a reason)")
    rej = s.clone(s.gh_proxy, "reviewer-reject")
    rej.commit("config/feature-flags.yml", "feat: enable experimental flag")
    rej.push()
    rej_id = s.record("rejected-by-reviewer", rej.ref)

    log("canceled — withdrawn by the pusher (cancel as dev)")
    can = s.clone(s.gh_proxy, "canceled")
    can.commit("scratch.txt", "chore: exploratory change, withdrawn")
    can.push()
    can_id = s.record("canceled", can.ref)

    log("self-certified — the pusher approves their own push (approve as dev; SELF_CERTIFY role + grant)")
    selfc = s.clone(s.gh_proxy, "self-certify")
    selfc.commit("docs/faq.md", "docs: answer the most common question")
    selfc.push()
    selfc_id = s.record("self-certified", selfc.ref)

    log("ssh-server-forwarded — server mode over SSH, held open until approved (approve as reviewer)")
    ssh_env = {"GIT_SSH_COMMAND": "ssh -A -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR"}
    ssh = s.clone(s.gh_ssh, "ssh-server", env=ssh_env)
    ssh.commit("docs/ssh-transport.md", "feat: pushed over the SSH transport")
    ssh_proc = subprocess.Popen(["git", "push", "origin", ssh.branch], cwd=ssh.dir, env=ssh.env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    ssh_id = None
    for _ in range(45):
        ssh_id = push_id_for_ref(ssh.ref, "PENDING")
        if ssh_id or ssh_proc.poll() is not None:
            break
        time.sleep(2)
    if not ssh_id:
        warn("no PENDING push appeared over SSH — is the key in ssh-agent and registered on github.com?")
    s.record("ssh-server-forwarded", ssh.ref, ssh_id)

    log("Manual step: review the pushes above")
    url = lambda pid: f"{FOGWALL}/dashboard/push/{pid}"  # noqa: E731
    print(f"""    As  reviewer / password :
      APPROVE  {url(multi_id)}   (three commits)
      APPROVE  {url(ltag_id)}   (lightweight tag)
      REJECT   {url(rej_id)}   (give a reason, e.g. "Needs a ticket reference")
      APPROVE  {url(ssh_id) if ssh_id else '<ssh push did not register>'}   (SSH push, held open)
    As  dev / password :
      CANCEL   {url(can_id)}
      APPROVE  {url(selfc_id)}   (self-certify banner should show)
    The script continues on its own once every push has left PENDING.""", flush=True)
    expected = {multi_id: "APPROVED", ltag_id: "APPROVED", rej_id: "REJECTED", can_id: "CANCELED", selfc_id: "APPROVED"}
    if ssh_id:
        expected[ssh_id] = "FORWARDED"
    wait_for_review({k: v for k, v in expected.items() if k})
    # Record what the reviewer actually typed, so the specs can assert the reason is displayed.
    for scenario_name in ("forwarded-multi-commit", "forwarded-lightweight-tag", "rejected-by-reviewer",
                          "canceled", "self-certified", "ssh-server-forwarded"):
        entry = s.manifest.get(scenario_name)
        if entry and entry.get("id"):
            _, rec = api("GET", f"/api/push/{entry['id']}")
            att = (rec or {}).get("attestation") if isinstance(rec, dict) else None
            if att:
                entry["reviewer"] = att.get("reviewerUsername", "")
                entry["reason"] = att.get("reason") or ""

    # Re-push the approved ones so they are FORWARDED, then remove them upstream.
    if push_status(multi_id) == "APPROVED":
        multi.forward()
    multi.upstream_delete(s.gh_direct)
    if push_status(ltag_id) == "APPROVED":
        ltag.forward(tag)
    ltag.upstream_delete(s.gh_direct, tag)
    if push_status(selfc_id) == "APPROVED":
        selfc.forward()
    selfc.upstream_delete(s.gh_direct)
    try:
        out, _ = ssh_proc.communicate(timeout=120)
    except subprocess.TimeoutExpired:
        ssh_proc.kill(); out, _ = ssh_proc.communicate()
    indent(out)
    ssh.upstream_delete(s.gh_direct)


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 5b. Proposals — each CLI opens, edits and closes a PR/MR and an issue through its provider's proposals listener
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# gh/glab/tea print a URL; fj prints "#1" wrapped in Unicode bidi-isolate characters (U+2068/U+2069).
NUMBER_RE = re.compile(r"/(?:pull|pulls|merge_requests|issues|work_items)/(\d+)\b|#\u2068?(\d+)\u2069?")


def cli(args: list[str], *, cwd: Path, env: dict[str, str], stdin: str | None = None) -> str:
    """Run one CLI command, echo its output, return it. Never raises — a failed step is visible in the transcript and
    the audit record it produced (or didn't) is what the dump carries."""
    note("$ " + " ".join(args))
    p = subprocess.run(args, cwd=cwd, env=env, text=True, input=stdin, timeout=120,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    indent(p.stdout)
    if p.returncode != 0:
        warn(f"{args[0]} exited {p.returncode}")
    return p.stdout


def number_in(out: str, what: str) -> str:
    m = [a or b for a, b in NUMBER_RE.findall(out)]
    if not m:
        raise Fatal(f"could not find the {what} number in the CLI output above")
    return m[-1]


def head_branch(s: Session, direct_url: str, prefix: str) -> Repo:
    """A branch pushed straight to the real repo (not through fogwall) for a PR to be opened from."""
    r = s.clone(direct_url, prefix)
    r.commit(f"proposals/{prefix}.txt", f"feat: change proposed via {prefix}")
    rc, out = git("push", "-q", "origin", r.branch, cwd=r.dir, env=r.env, timeout=120)
    if rc != 0:
        indent(out)
        raise Fatal(f"could not push the {prefix} head branch upstream")
    return r


def run_proposal_scenarios(s: Session, ca: Path) -> None:
    for tool in ("gh", "glab", "tea", "fj"):
        if not shutil.which(tool):
            raise Fatal(f"{tool} is required for the proposals scenarios")
    slug = s.slug
    base = {**os.environ, "SSL_CERT_FILE": str(ca), "NO_COLOR": "1", "TERM": "dumb", "GIT_TERMINAL_PROMPT": "0"}

    def done(scenario: str, provider: str, before: list[str], **numbers: str) -> None:
        new = [i for i in action_ids(provider) if i not in before]
        s.manifest[scenario] = {**numbers, "actions": new}
        note(f"recorded {scenario} → {len(new)} audit records {numbers}")

    # One refused proposal per client, each tripping a different check: nothing reaches the upstream, and the
    # record carries the rule that matched. The AWS key and IBAN are shaped like real ones and are not.
    REJECT = {
        "gh": ("secret scan", "aws_access_key_id = AKIAQ3EGTX6PVZ2NW7HB / aws_secret_access_key = 8kP2mXq9vL4nR7tY1wZ3cB6hJ5dF0aG2sE4uI8oK"),
        "glab": ("blocked literal", "See the runbook on internal.corp.example.com before merging."),
        "fj": ("content pattern (IBAN)", "Wire transfer to IBAN DE89370400440532013000 once released."),
        "tea": ("blocked pattern", "Deploy hits https://ci.corp.example.com/deploy after merge."),
    }

    # ── gh: GraphQL against the github listener ──────────────────────────────────────────────────────────────
    log("proposals-gh — pull request and issue via gh (create, edit, close)")
    host = f"localhost:{PROPOSAL_PORTS['github']}"
    r = head_branch(s, s.gh_direct, "gh")
    env = {**base, "GH_HOST": host, "GH_ENTERPRISE_TOKEN": s.pats["github"], "GH_CONFIG_DIR": str(s.work / "gh"),
           "GH_PROMPT_DISABLED": "1", "GH_NO_UPDATE_NOTIFIER": "1"}
    before = action_ids("github")
    repo = f"{host}/{slug}"
    pr = number_in(cli(["gh", "pr", "create", "-R", repo, "--base", "main", "--head", r.branch,
                        "--title", "Proposed via gh", "--body", "Opened through fogwall with gh."], cwd=r.dir, env=env), "PR")
    cli(["gh", "pr", "edit", pr, "-R", repo, "--title", "Proposed via gh (edited)"], cwd=r.dir, env=env)
    cli(["gh", "pr", "close", pr, "-R", repo], cwd=r.dir, env=env)
    issue = number_in(cli(["gh", "issue", "create", "-R", repo, "--title", "Reported via gh",
                           "--body", "Opened through fogwall with gh."], cwd=r.dir, env=env), "issue")
    cli(["gh", "issue", "edit", issue, "-R", repo, "--body", "Edited through fogwall with gh."], cwd=r.dir, env=env)
    cli(["gh", "issue", "close", issue, "-R", repo], cwd=r.dir, env=env)
    done("proposals-gh", "github", before, pr=pr, issue=issue)
    log(f"proposals-gh-rejected — issue body refused by {REJECT['gh'][0]}")
    before = action_ids("github")
    cli(["gh", "issue", "create", "-R", repo, "--title", "Credentials for the deploy job", "--body", REJECT["gh"][1]], cwd=r.dir, env=env)
    done("proposals-gh-rejected", "github", before)

    # ── glab: REST /api/v4 against the gitlab listener ───────────────────────────────────────────────────────
    # `glab mr create` insists on a git remote that points at GITLAB_HOST; it is never used for git.
    log("proposals-glab — merge request and issue via glab (create, update, close)")
    host = f"localhost:{PROPOSAL_PORTS['gitlab']}"
    r = head_branch(s, s.gl_direct, "glab")
    git("remote", "add", "glab-proxy-do-not-use", f"https://{host}/{slug}.git", cwd=r.dir)
    env = {**base, "GLAB_CONFIG_DIR": str(s.work / "glab"), "GITLAB_HOST": host}
    cli(["glab", "auth", "login", "--hostname", host, "--api-protocol", "https", "--git-protocol", "https",
         "--insecure-storage", "--stdin"], cwd=r.dir, env=env, stdin=s.pats["gitlab"] + "\n")
    before = action_ids("gitlab")
    mr = number_in(cli(["glab", "mr", "create", "-R", slug, "--source-branch", r.branch, "--target-branch", "main",
                        "--title", "Proposed via glab", "--description", "Opened through fogwall with glab.",
                        "--no-editor", "--yes"], cwd=r.dir, env=env), "MR")
    cli(["glab", "mr", "update", mr, "-R", slug, "--title", "Proposed via glab (edited)"], cwd=r.dir, env=env)
    cli(["glab", "mr", "close", mr, "-R", slug], cwd=r.dir, env=env)
    issue = number_in(cli(["glab", "issue", "create", "-R", slug, "--title", "Reported via glab",
                           "--description", "Opened through fogwall with glab.", "--no-editor", "--yes"],
                          cwd=r.dir, env=env), "issue")
    cli(["glab", "issue", "update", issue, "-R", slug, "--description", "Edited through fogwall with glab."], cwd=r.dir, env=env)
    cli(["glab", "issue", "close", issue, "-R", slug], cwd=r.dir, env=env)
    done("proposals-glab", "gitlab", before, mr=mr, issue=issue)
    log(f"proposals-glab-rejected — issue description refused by {REJECT['glab'][0]}")
    before = action_ids("gitlab")
    cli(["glab", "issue", "create", "-R", slug, "--title", "Runbook link", "--description", REJECT["glab"][1],
         "--no-editor", "--yes"], cwd=r.dir, env=env)
    done("proposals-glab-rejected", "gitlab", before)

    # ── fj: REST /api/v1 against the codeberg listener ───────────────────────────────────────────────────────
    # codeberg stays unmapped through the push scenarios (the unmapped-identity rejection needs it), so dev's
    # identity there is added now, hand-typed. fj works from the clone's remote, so origin is repointed at the
    # listener once the head branch is upstream.
    log("proposals-fj — pull request and issue via fj on codeberg (create, edit, close)")
    code, j = api("POST", "/api/users/dev/identities", {"provider": "codeberg", "scmUsername": s.ident.handle})
    if code >= 300:
        warn(f"could not add dev's codeberg identity → HTTP {code}: {j}")
    host = f"https://localhost:{PROPOSAL_PORTS['codeberg']}"
    r = head_branch(s, s.cb_direct, "fj")
    git("remote", "set-url", "origin", f"{host}/{slug}.git", cwd=r.dir)
    env = {**base, "XDG_CONFIG_HOME": str(s.work / "fj"), "XDG_DATA_HOME": str(s.work / "fj")}  # fj keeps logins in DATA
    cli(["fj", "auth", "add-token", "-H", host], cwd=r.dir, env=env, stdin=s.pats["codeberg"] + "\n")
    before = action_ids("codeberg")
    pr = number_in(cli(["fj", "-H", host, "pr", "create", "Proposed via fj", "--body", "Opened through fogwall with fj.",
                        "--head", r.branch, "--base", "main", "--repo", slug], cwd=r.dir, env=env), "PR")
    cli(["fj", "-H", host, "pr", "edit", pr, "title", "Proposed via fj (edited)"], cwd=r.dir, env=env)
    cli(["fj", "-H", host, "pr", "close", pr], cwd=r.dir, env=env)
    issue = number_in(cli(["fj", "-H", host, "issue", "create", "Reported via fj", "--body", "Opened through fogwall with fj."],
                          cwd=r.dir, env=env), "issue")
    cli(["fj", "-H", host, "issue", "edit", issue, "body", "Edited through fogwall with fj."], cwd=r.dir, env=env)
    cli(["fj", "-H", host, "issue", "close", issue], cwd=r.dir, env=env)
    done("proposals-fj", "codeberg", before, pr=pr, issue=issue)
    log(f"proposals-fj-rejected — issue body refused by {REJECT['fj'][0]}")
    before = action_ids("codeberg")
    cli(["fj", "-H", host, "issue", "create", "Refund details", "--body", REJECT["fj"][1]], cwd=r.dir, env=env)
    done("proposals-fj-rejected", "codeberg", before)

    # ── tea: REST /api/v1 against the gitea listener ─────────────────────────────────────────────────────────
    log("proposals-tea — pull request and issue via tea on gitea.com (create, edit, close)")
    host = f"https://localhost:{PROPOSAL_PORTS['gitea']}"
    r = head_branch(s, s.gt_direct, "tea")
    env = {**base, "XDG_CONFIG_HOME": str(s.work / "tea")}
    # The token goes in through tea's own env var so the echoed command line never carries it.
    cli(["tea", "login", "add", "--name", "fogwall", "--url", host], cwd=r.dir,
        env={**env, "GITEA_SERVER_TOKEN": s.pats["gitea"]})
    before = action_ids("gitea")
    at = ["--login", "fogwall", "--repo", slug]
    pr = number_in(cli(["tea", "pr", "create", *at, "--head", r.branch, "--base", "main",
                        "--title", "Proposed via tea", "--description", "Opened through fogwall with tea."], cwd=r.dir, env=env), "PR")
    cli(["tea", "pr", "edit", pr, *at, "--title", "Proposed via tea (edited)"], cwd=r.dir, env=env)
    cli(["tea", "pr", "close", pr, *at], cwd=r.dir, env=env)
    issue = number_in(cli(["tea", "issue", "create", *at, "--title", "Reported via tea",
                           "--description", "Opened through fogwall with tea."], cwd=r.dir, env=env), "issue")
    cli(["tea", "issue", "edit", issue, *at, "--description", "Edited through fogwall with tea."], cwd=r.dir, env=env)
    cli(["tea", "issue", "close", issue, *at], cwd=r.dir, env=env)
    done("proposals-tea", "gitea", before, pr=pr, issue=issue)
    log(f"proposals-tea-rejected — issue description refused by {REJECT['tea'][0]}")
    before = action_ids("gitea")
    cli(["tea", "issue", "create", *at, "--title", "Deploy hook", "--description", REJECT["tea"][1]], cwd=r.dir, env=env)
    done("proposals-tea-rejected", "gitea", before)


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# 6. Dump, scrub, verify
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
def dump_and_scrub(work: Path, db_path: Path, ident: Identity, pats: dict[str, str]) -> tuple[str, list[str]]:
    log("Deleting secret/session/cache rows")
    gradle(":fogwall-dashboard:runH2Script", f"-Ph2Path={db_path}", f"-Ph2Script={HERE / 'scrub.sql'}", check=True)
    log("Dumping to SQL")
    raw_sql = work / "dump.sql"
    gradle(":fogwall-dashboard:dumpH2", f"-Ph2Path={db_path}", f"-Ph2Out={raw_sql}", check=True)

    log("Replacing real values with placeholders")
    sql = ident.to_placeholder(raw_sql.read_text())

    leaks = [real for _, real in ident.mapping() if real in sql]
    leaks += ["<a PAT>" for pat in pats.values() if pat in sql]
    if leaks:
        (work / "fogwall.sql").write_text(sql)
        raise Fatal("scrub incomplete — still present: " + ", ".join(leaks) + f"\n  inspect {work / 'fogwall.sql'}, extend mapping.env, re-run")

    emails = sorted(set(re.findall(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", sql)))
    return sql, emails


# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# main
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════
def main() -> int:
    for tool in ("git", "ssh-keygen", "openssl"):
        if not shutil.which(tool):
            raise Fatal(f"{tool} is required")
    env = load_env_files()
    for key in ("REAL_HANDLE", "REAL_NAME", "REAL_EMAIL"):
        if not env.get(key):
            raise Fatal(f"{key} must be set in mapping.env")
    pubkey = Path(os.path.expanduser(env.get("REAL_SSH_PUBKEY_FILE", "")))
    if not pubkey.is_file():
        raise Fatal(f"REAL_SSH_PUBKEY_FILE not found: {pubkey}")
    pats = {p: read_pat(env, p.upper()) for p in ("github", "gitlab", "codeberg", "gitea")}
    oauth_env = {k: v for k, v in env.items() if k.startswith("FOGWALL_PROVIDERS__")}

    # Random suffix so the repo name can't collide with anything on the account; scrubbed back to the bare
    # placeholder name in the dump so the specs assert on a stable value.
    ident = Identity(
        handle=env["REAL_HANDLE"], name=env["REAL_NAME"], email=env["REAL_EMAIL"],
        alt_email=env.get("REAL_ALT_EMAIL", ""), pubkey_file=pubkey,
        repo_name=f"{FIX['repo']}-{os.urandom(6).hex()}",
    )

    work = Path(tempfile.mkdtemp(prefix="fogwall-capture-"))
    conf = work / "conf"; conf.mkdir()
    db_path = work / "db" / "capture"; db_path.parent.mkdir()
    provs = providers(pats, ident.handle)
    app: subprocess.Popen | None = None

    try:
        log(f"Building capture profile in {conf}")
        real_profile = ident.to_real(PROFILE.read_text())
        if ident.handle not in real_profile:
            raise Fatal("profile rewrite produced no real handle — check mapping.env")
        (conf / "fogwall-playwright.yml").write_text(real_profile)

        log("Creating ephemeral private repos")
        for p in provs:
            p.create(ident.handle, ident.repo_name)
        time.sleep(5)  # providers need a moment before a fresh repo accepts git over HTTP

        cert, key, ca = make_tls(work)
        log(f"Booting fogwall on H2 file {db_path} (log: {work / 'fogwall.log'})")
        app = boot(work, conf, db_path, {**oauth_env, "FOGWALL_SERVER_TLS_CERTIFICATE": str(cert), "FOGWALL_SERVER_TLS_KEY": str(key)})

        # OAuth linking comes BEFORE the pushes: the profile deliberately declares no github/gitlab identity for dev,
        # so linking creates those rows (verified) and the pushes then resolve through them. Codeberg must stay
        # unlinked — the unmapped-identity scenario depends on it. Gitea stays config-declared as the "locked" example.
        log("Seeding DB-sourced profile rows via the API")
        seed_db_profile_data(ident.handle)

        if not SKIP_OAUTH:
            log("Manual step: link your accounts")
            print(f"""    1. Open {FOGWALL}/dashboard/profile and log in as  dev / password
    2. Click "Link with github.com" and "Link with gitlab.com"   (NOT codeberg — it must stay unmapped)
    3. Confirm the github/gitlab identities and your emails show "verified"
    Press ENTER here when done: """, end="", flush=True)
            try:
                input()
            except EOFError:
                pass

        seed_second_github_identity()
        session = Session(work, ident, pats)
        if SKIP_PUSHES:
            warn("SKIP_PUSHES: no push scenarios — for iterating on the proposals block only")
        else:
            run_scenarios(session)
        run_proposal_scenarios(session, ca)
        (work / "manifest.json").write_text(json.dumps(session.manifest, indent=2))

        log("Stopping fogwall")
        stop_app()
        app = None
        sql, emails = dump_and_scrub(work, db_path, ident, pats)

        note("distinct emails left in the dump (anything personal here means mapping.env needs another entry):")
        for e in emails:
            note(f"  {e}")
        (FIXTURES / "fogwall.sql").write_text(sql)
        (FIXTURES / "manifest.json").write_text(json.dumps(session.manifest, indent=2) + "\n")
        log("Done")
        note(f"{sql.count('VALUES')} insert statements → {FIXTURES / 'fogwall.sql'}")
        note(f"{len(session.manifest)} scenarios → {FIXTURES / 'manifest.json'}")
        note("Next: cd fogwall-dashboard/frontend && npx playwright test")
        return 0
    finally:
        if app is not None or health():
            stop_app()
        log("Deleting ephemeral repos")
        for p in provs:
            if p.created:
                p.delete(ident.handle, ident.repo_name)
                note(f"deleted {p.name}: {ident.handle}/{ident.repo_name}")
        if KEEP_WORK:
            warn(f"keeping {work}")
        else:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Fatal as e:
        print(f"\033[1;31mERROR: {e}\033[0m", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print("\ninterrupted", file=sys.stderr)
        sys.exit(130)
