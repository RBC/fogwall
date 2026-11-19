import { useEffect, useState, type ReactNode } from 'react'
import { NavLink } from 'react-router'
import { fetchConfig, fetchSetup } from '../api'
import type { SetupInfo, SetupProvider } from '../types'
import { ConfigBlock } from '../components/ConfigBlock'

const USER_GUIDE_URL = 'https://rbc.github.io/fogwall/user/remotes.html'

/**
 * In-app developer quick start. Public page (no login required): generated, deployment-specific commands that read
 * through fogwall's transparent proxy and push through server mode. Content comes from `/api/setup`, so it cannot
 * drift from what fogwall actually serves. Detail, tips and suggested configs live in the user guide; the only extra
 * here is one collapsed "More options" section for routing every repository and for SSH.
 */
export function Setup() {
  const [setup, setSetup] = useState<SetupInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [brokered, setBrokered] = useState<string[]>([])
  const [deferred, setDeferred] = useState<string[]>([])
  const [selected, setSelected] = useState(0)

  useEffect(() => {
    fetchConfig()
      .then((c) => {
        setBrokered(c.brokeredPushProviders ?? [])
        setDeferred(c.deferredForwardingProviders ?? [])
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    fetchSetup()
      .then(setSetup)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [])

  const provider = setup?.providers[selected] ?? setup?.providers[0]

  return (
    <div className="max-w-6xl px-6 py-6 space-y-4">
      <header className="space-y-2">
        <h2 className="text-xl font-semibold text-gray-800 dark:text-gray-200">
          Connect your git client
        </h2>
        <p className="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
          fogwall checks your pushes against policy before they reach the upstream host. Clones and
          fetches go through its proxy; pushes go through server mode.
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
          derived from the address in your browser. Behind a reverse proxy an operator should set it
          so they are correct for everyone.
        </div>
      )}

      {setup && setup.providers.length === 0 && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">
          No providers configured.
        </div>
      )}

      {setup && provider && (
        <>
          <QuickStart
            setup={setup}
            provider={provider}
            selected={selected}
            onSelect={setSelected}
            brokered={brokered.includes(provider.name)}
            deferred={deferred.includes(provider.name)}
          />
          <MoreOptions provider={provider} />
        </>
      )}
    </div>
  )
}

/** What to enter as the git password when pushing, which depends on how the provider is set up. */
function pushCredential(brokered: boolean, deferred: boolean): ReactNode {
  if (!brokered) {
    return <>your upstream personal access token, with write access to the repository.</>
  }
  return (
    <>
      a fogwall credential. Link your account and create one under Git Credentials on your{' '}
      <NavLink to="/profile" className="text-blue-600 hover:underline dark:text-blue-400">
        profile
      </NavLink>
      .
      {deferred &&
        ' fogwall acknowledges the push once its checks pass and forwards it after approval.'}
    </>
  )
}

function QuickStart({
  setup,
  provider,
  selected,
  onSelect,
  brokered,
  deferred,
}: {
  setup: SetupInfo
  provider: SetupProvider
  selected: number
  onSelect: (index: number) => void
  brokered: boolean
  deferred: boolean
}) {
  return (
    <section className="rounded-lg border border-blue-200 bg-blue-50/50 px-6 py-4 dark:border-blue-900/40 dark:bg-blue-900/10">
      <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">Quick start</h3>
      <ol className="space-y-4 text-sm text-gray-700 dark:text-gray-300">
        <li className="space-y-1.5">
          <div>
            <span className="font-semibold">1.</span> Once per machine, keep a separate credential
            for each repository:
          </div>
          <ConfigBlock label="Terminal" config={setup.credentialKeying} wrap />
        </li>
        <li className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <span>
              <span className="font-semibold">2.</span> Clone
            </span>
            {setup.providers.length > 1 ? (
              <select
                aria-label="Provider"
                value={selected}
                onChange={(e) => onSelect(Number(e.target.value))}
                className="rounded border border-gray-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200"
              >
                {setup.providers.map((p, i) => (
                  <option key={p.name} value={i}>
                    {p.name} ({p.host})
                  </option>
                ))}
              </select>
            ) : (
              <span className="font-mono text-xs">{provider.host}</span>
            )}
            <span>through fogwall:</span>
          </div>
          <ConfigBlock label="New clone" config={provider.httpClone} wrap />
          <ConfigBlock label="Already cloned?" config={provider.httpExisting} wrap />
        </li>
        <li>
          <span className="font-semibold">3.</span> Commit and{' '}
          <code className="font-mono">git push</code>. Git prompts once: use any username and, as
          the password, {pushCredential(brokered, deferred)}
        </li>
      </ol>
      <div className="mt-4 pt-3 border-t border-blue-200/70 dark:border-blue-900/40 text-sm text-gray-600 dark:text-gray-400 space-y-1">
        <p>
          <NavLink to="/profile" className="text-blue-600 hover:underline dark:text-blue-400">
            Link your SCM identity
          </NavLink>{' '}
          so pushes are attributed to you, then follow them on the{' '}
          <NavLink to="/" className="text-blue-600 hover:underline dark:text-blue-400">
            Pushes
          </NavLink>{' '}
          page.
        </p>
        <p>
          Private repositories also ask for a token on clone. For tips, token scopes and
          troubleshooting, see the{' '}
          <a
            href={USER_GUIDE_URL}
            target="_blank"
            rel="noreferrer"
            className="text-blue-600 hover:underline dark:text-blue-400"
          >
            user guide
          </a>
          .
        </p>
      </div>
    </section>
  )
}

/** One collapsed section for the less common setups: routing every repository through fogwall, and SSH. */
function MoreOptions({ provider }: { provider: SetupProvider }) {
  const [open, setOpen] = useState(false)
  return (
    <section className="rounded-lg border border-gray-200 bg-white dark:border-slate-700 dark:bg-slate-800 overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="w-full flex items-center gap-3 px-6 py-3 text-left text-sm font-semibold text-gray-800 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-slate-700/40 transition-colors"
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
        More options for {provider.name}
        <span className="ml-auto flex items-center gap-1">
          <TransportBadge label="HTTPS" />
          {provider.sshEnabled && <TransportBadge label="SSH" />}
        </span>
      </button>
      {open && (
        <div className="px-6 pb-4 pt-3 space-y-3 border-t border-gray-100 dark:border-slate-700 text-sm text-gray-600 dark:text-gray-400">
          <p>
            Route <em>every</em> {provider.host} repository through fogwall by pasting into{' '}
            <code className="font-mono">~/.gitconfig</code>. Pushes only:
          </p>
          <ConfigBlock label="Pushes" config={provider.httpPush} />
          <p>Reads as well:</p>
          <ConfigBlock label="Reads" config={provider.httpRead} />
          {provider.sshEnabled && provider.sshPush && (
            <>
              <p>Over SSH (requires agent forwarding):</p>
              <ConfigBlock label="SSH" config={provider.sshPush} />
              {provider.sshPerRepo && (
                <ConfigBlock label="SSH, one repository" config={provider.sshPerRepo} />
              )}
            </>
          )}
        </div>
      )}
    </section>
  )
}

function TransportBadge({ label }: { label: string }) {
  return (
    <span className="px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide rounded bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
      {label}
    </span>
  )
}
