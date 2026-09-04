import { useEffect, useState, type ReactNode } from 'react'
import { NavLink } from 'react-router'
import { fetchSetup } from '../api'
import type { SetupInfo, SetupProvider } from '../types'

/**
 * In-app developer setup guide (#475). Public page (no login required): shows generated, deployment-specific git
 * config that sends a developer's pushes through fogwall. Content is generated from the running configuration by
 * `/api/setup`, so it cannot drift from what fogwall actually serves.
 *
 * <p>Structure: a two-step quick start up top (clone through fogwall, push), then an always-visible Authentication
 * section, then an "Advanced" area of collapsed per-provider accordions for permanent {@code $HOME/.gitconfig} setup.
 * The advanced config is push-only by default (reroutes pushes, leaves clones/fetches direct), with a global-vs-per-repo
 * choice and an opt-in for routing fetches too. fogwall itself proxies both directions; push-only is just the default
 * this page recommends.
 */
export function Setup() {
  const [setup, setSetup] = useState<SetupInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    fetchSetup()
      .then(setSetup)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  const host = setup ? safeHost(setup.serviceUrl) : ''

  return (
    <div className="max-w-4xl mx-auto px-4 py-6 space-y-4">
      <header className="space-y-2">
        <h2 className="text-xl font-semibold text-gray-800 dark:text-gray-200">
          Connect your git client
        </h2>
        <p className="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
          fogwall is a proxy in front of your upstream git host: it validates pushes against policy
          before they reach upstream, and can proxy and audit fetches too. This page sets up your
          git client to route through it — by default just your{' '}
          <span className="font-medium">pushes</span>, with clones and fetches left going direct.
        </p>
        <p className="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
          The quick start below gets you pushing in two commands. The sections under it cover{' '}
          <span className="font-medium">authentication</span> and pointing your git client at
          fogwall permanently via <code className="font-mono">$HOME/.gitconfig</code> — for pushes,
          and optionally fetches.
        </p>
      </header>

      {loading && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">Loading…</div>
      )}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-900/20 dark:text-red-300">
          Could not load the setup configuration. Try refreshing the page.
        </div>
      )}

      {setup && !setup.serviceUrlConfigured && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900/50 dark:bg-amber-900/20 dark:text-amber-300">
          <span className="font-semibold">Heads up:</span>{' '}
          <code className="font-mono">server.service-url</code> is not set, so the URLs below were
          derived from the address in your browser. If fogwall runs behind a reverse proxy, an
          operator should set <code className="font-mono">service-url</code> so these are correct
          for everyone.
        </div>
      )}

      {setup && setup.providers.length === 0 && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">
          No providers configured.
        </div>
      )}

      {setup && setup.providers.length > 0 && <QuickStart provider={setup.providers[0]} />}

      {setup && setup.providers.length > 0 && <Authentication host={host} />}

      {setup && setup.providers.length > 0 && (
        <section className="space-y-3">
          <div className="space-y-1">
            <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200">
              Advanced — always route through fogwall
            </h3>
            <p className="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
              You can point your git client at fogwall for a provider permanently — for pushes, and
              optionally fetches — by adding a block to your{' '}
              <code className="font-mono">$HOME/.gitconfig</code>. Expand a provider for the global
              one-paste form and the per-repository alternative.
            </p>
          </div>
          {setup.providers.map((p) => (
            <ProviderAccordion key={p.name} provider={p} />
          ))}
        </section>
      )}
    </div>
  )
}

/**
 * Two-step happy path shown up top: clone a repo straight through fogwall (so the remote is already fogwall, no config
 * needed), then push. Uses the first provider for a concrete, copy-pasteable clone command.
 */
function QuickStart({ provider }: { provider: SetupProvider }) {
  // serverUrl comes from /api/setup; fall back to a readable placeholder if an older backend omits it.
  const serverUrl = provider.serverUrl || `https://<fogwall-host>/server/${provider.host}/`
  const cloneCmd = `git clone ${serverUrl}<owner>/<repo>.git`
  return (
    <section className="rounded-lg border border-blue-200 bg-blue-50/50 px-6 py-4 dark:border-blue-900/40 dark:bg-blue-900/10">
      <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-2">Quick start</h3>
      <ol className="space-y-3 text-sm text-gray-700 dark:text-gray-300">
        <li className="space-y-1.5">
          <div>
            <span className="font-semibold">1.</span> Clone the repo through fogwall — this points
            the remote at fogwall, no config needed:
          </div>
          <ConfigBlock label={provider.host} config={cloneCmd} />
        </li>
        <li>
          <span className="font-semibold">2.</span> Commit and{' '}
          <code className="font-mono">git push</code> as usual. On the first push your credential
          manager prompts once — enter your username and token. fogwall validates the push and
          forwards it upstream.
        </li>
      </ol>
      <div className="mt-4 pt-3 border-t border-blue-200/70 dark:border-blue-900/40">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 mb-1">
          Where to go next
        </h4>
        <ul className="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
          <li>
            <NavLink to="/profile" className="text-blue-600 hover:underline dark:text-blue-400">
              Link your SCM identity
            </NavLink>{' '}
            so pushes are attributed to you.
          </li>
          <li>
            Watch your pushes move through review on the{' '}
            <NavLink to="/" className="text-blue-600 hover:underline dark:text-blue-400">
              Pushes
            </NavLink>{' '}
            page.
          </li>
        </ul>
      </div>
    </section>
  )
}

/** Collapsible section. Collapsed by default so the page stays short and later content stays reachable. */
function Accordion({ header, children }: { header: ReactNode; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  return (
    <section className="rounded-lg border border-gray-200 bg-white dark:border-slate-700 dark:bg-slate-800 overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="w-full flex items-center gap-3 px-6 py-4 text-left hover:bg-gray-50 dark:hover:bg-slate-700/40 transition-colors"
      >
        <svg
          className={
            'h-4 w-4 shrink-0 text-gray-400 transition-transform ' + (open ? 'rotate-90' : '')
          }
          fill="none"
          viewBox="0 0 20 20"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M7 5l6 5-6 5" />
        </svg>
        <div className="flex-1 min-w-0">{header}</div>
      </button>
      {open && (
        <div className="px-6 pb-4 pt-1 space-y-3 border-t border-gray-100 dark:border-slate-700">
          {children}
        </div>
      )}
    </section>
  )
}

/** A left-bordered informational callout with an info icon — for a notice pulled out of surrounding prose. */
function Notice({ children }: { children: ReactNode }) {
  return (
    <div className="flex gap-2 rounded-md border-l-4 border-blue-400 bg-blue-50 px-3 py-2 text-xs text-blue-800 dark:border-blue-500/60 dark:bg-blue-900/20 dark:text-blue-200">
      <svg
        className="h-4 w-4 shrink-0 mt-px"
        fill="currentColor"
        viewBox="0 0 20 20"
        aria-hidden="true"
      >
        <path
          fillRule="evenodd"
          d="M18 10A8 8 0 11 2 10a8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
          clipRule="evenodd"
        />
      </svg>
      <div className="leading-relaxed">{children}</div>
    </div>
  )
}

function TransportBadge({ label }: { label: string }) {
  return (
    <span className="px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide rounded bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
      {label}
    </span>
  )
}

function ProviderAccordion({ provider }: { provider: SetupProvider }) {
  return (
    <Accordion
      header={
        <div className="flex items-center gap-3">
          <img
            src={`https://${provider.host}/favicon.ico`}
            className="w-5 h-5 rounded"
            alt=""
            onError={(e) => (e.currentTarget.style.display = 'none')}
          />
          <span className="text-base font-semibold text-gray-800 dark:text-gray-200">
            {provider.name}
          </span>
          <span className="font-mono text-xs text-gray-500 dark:text-gray-400">
            {provider.host}
          </span>
          <span className="ml-auto flex items-center gap-1">
            <TransportBadge label="HTTPS" />
            {provider.sshEnabled && <TransportBadge label="SSH" />}
          </span>
        </div>
      }
    >
      {/* Global push-only — the one-paste recommendation. */}
      <SectionLabel>
        Global — reroutes your {provider.host} pushes{' '}
        <span className="font-normal text-gray-400 dark:text-gray-500">
          (one paste in ~/.gitconfig)
        </span>
      </SectionLabel>
      <ConfigBlock label="HTTPS" config={provider.httpPush} />
      {provider.sshEnabled && provider.sshPush && (
        <ConfigBlock label="SSH" config={provider.sshPush} />
      )}
      <Notice>
        This reroutes <em>every</em> push to {provider.host} through fogwall; clones and fetches are
        unchanged. If you also push to {provider.host} outside fogwall, use the per-repository form
        below instead.
      </Notice>

      {/* Per-repo — explicit, isolated. */}
      <SectionLabel>
        Per repository{' '}
        <span className="font-normal text-gray-400 dark:text-gray-500">
          (touches only the repo you run it in)
        </span>
      </SectionLabel>
      <ConfigBlock label="HTTPS" config={provider.httpPerRepo} />
      {provider.sshEnabled && provider.sshPerRepo && (
        <ConfigBlock label="SSH" config={provider.sshPerRepo} />
      )}

      {/* Optional read routing — de-emphasized. */}
      <SectionLabel muted>Optional — also route reads through fogwall</SectionLabel>
      <p className="text-xs text-gray-500 dark:text-gray-500 leading-relaxed">
        Only if your deployment wants fetches audited by fogwall too. Otherwise leave clones and
        fetches going straight to {provider.host}. Add this alongside the global block above.
      </p>
      <ConfigBlock label="~/.gitconfig" config={provider.httpRead} />
    </Accordion>
  )
}

/** Small sub-heading inside a provider accordion. */
function SectionLabel({ children, muted = false }: { children: ReactNode; muted?: boolean }) {
  return (
    <h4
      className={
        'text-xs font-semibold uppercase tracking-wide pt-1 ' +
        (muted ? 'text-gray-400 dark:text-gray-500' : 'text-gray-600 dark:text-gray-300')
      }
    >
      {children}
    </h4>
  )
}

/**
 * Authentication section. Explains the credential-helper-vs-inline-token gotcha: credential helpers are keyed by
 * hostname, and git talks to the fogwall host (not the upstream), so a helper entry stored for the upstream isn't used
 * — the thing developers most often get tripped up on. Rendered as a plain section (not an accordion) since it applies
 * to everyone regardless of which config form they chose.
 */
function Authentication({ host }: { host: string }) {
  const fw = host || 'the fogwall host'
  return (
    <section className="rounded-lg border border-gray-200 bg-white px-6 py-4 dark:border-slate-700 dark:bg-slate-800">
      <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-2">
        Authentication
      </h3>
      <div className="text-sm text-gray-600 dark:text-gray-400 space-y-2 leading-relaxed">
        <p>
          Credential helpers (macOS Keychain, Windows Credential Manager,{' '}
          <code className="font-mono">git-credential-store</code>) are keyed by hostname. Git talks
          to fogwall at <code className="font-mono">{fw}</code>, so it needs a credential stored
          under <code className="font-mono">{fw}</code>. If you have previously authenticated
          directly to an upstream (e.g. <code className="font-mono">github.com</code>), that
          credential <span className="font-semibold">won&rsquo;t</span> be used to authenticate
          through fogwall. That mismatch is the most common thing to get tripped up on.
        </p>
        <p>
          <span className="font-semibold text-gray-700 dark:text-gray-300">Easiest fix:</span> on
          the first push git prompts once — enter your SCM username and an upstream personal access
          token (PAT) as the password, and your helper saves it under{' '}
          <code className="font-mono">{fw}</code> for next time.
        </p>
        <Notice>
          git keys credentials by host, not path, so one entry is reused for <em>every</em> provider
          you route through fogwall — fine for a single upstream. Pushing to more than one provider
          through fogwall (e.g. <code className="font-mono">github.com</code> and{' '}
          <code className="font-mono">codeberg.org</code>)? Run this once to key credentials to the
          full URL (host + path) instead, so each provider gets its own:{' '}
          <code className="font-mono">
            git config --global credential.https://{fw}.useHttpPath true
          </code>
        </Notice>
        <p>
          <span className="font-semibold text-gray-700 dark:text-gray-300">
            Prefer an explicit remote?
          </span>{' '}
          Put the token straight in the URL — HTTP basic auth,{' '}
          <code className="font-mono">user:token@host</code> — handy for short-lived dev
          environments where a keychain entry isn&rsquo;t worth it:
        </p>
        <pre className="overflow-x-auto rounded bg-slate-50 border border-gray-200 p-3 text-xs font-mono text-gray-800 dark:bg-slate-900 dark:border-slate-700 dark:text-gray-200">
          {`git remote set-url --push origin https://<username>:<token>@${host || '<fogwall-host>'}/server/<provider>/<owner>/<repo>.git`}
        </pre>
        <p>
          The token goes in the password position; URL-encode any special characters in it. The
          username usually doesn&rsquo;t matter for token auth — most upstreams ignore it, so any
          non-empty value like <code className="font-mono">git</code>,{' '}
          <code className="font-mono">me</code>, or <code className="font-mono">token</code> works —
          but it is provider-specific, so check your SCM provider&rsquo;s docs.
        </p>
        <Notice>
          Make sure your token has sufficient permissions on the target upstream repository (e.g.{' '}
          <code className="font-mono">write</code> / <code className="font-mono">repo</code> scope
          on a GitHub classic PAT). fogwall forwards any upstream permission error back to the git
          client transparently — it can&rsquo;t tell you in advance whether your token has enough
          access.
        </Notice>
      </div>
    </section>
  )
}

function ConfigBlock({ label, config }: { label: string; config: string }) {
  const [copied, setCopied] = useState(false)

  const copy = () => {
    navigator.clipboard.writeText(config).then(
      () => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      },
      () => {
        /* clipboard blocked (e.g. insecure context) — leave the text selectable to copy by hand */
      },
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
          {label}
        </span>
        <button
          onClick={copy}
          className="text-xs px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 dark:bg-slate-700 dark:hover:bg-slate-600 dark:text-slate-200 transition-colors"
          aria-label="Copy configuration to clipboard"
        >
          {copied ? 'Copied ✓' : 'Copy'}
        </button>
      </div>
      <pre className="overflow-x-auto rounded bg-slate-50 border border-gray-200 p-3 text-xs font-mono text-gray-800 dark:bg-slate-900 dark:border-slate-700 dark:text-gray-200">
        {config}
      </pre>
    </div>
  )
}

/** Extracts the host from a base URL for display; falls back to empty string on a malformed URL. */
function safeHost(url: string): string {
  try {
    return new URL(url).host
  } catch {
    return ''
  }
}
