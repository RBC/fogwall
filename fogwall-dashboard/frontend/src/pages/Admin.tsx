import { useEffect, useRef, useState } from 'react'
import type { CacheEntry, CacheListResponse, CacheRef, Provider } from '../types'
import {
  type GitProbeResult,
  type LogStep,
  type ProviderConnectivity,
  type TcpResult,
  type TlsResult,
  type HttpResult,
  checkConnectivity,
  checkTargetedConnectivity,
  fetchCache,
  fetchCacheRefs,
  fetchProviders,
  invalidateCacheAll,
  invalidateCacheEntry,
  triggerConfigReload,
} from '../api'

function ms(n: number) {
  return `${n} ms`
}

function TcpBadge({ tcp }: { tcp: TcpResult }) {
  const ok = tcp.status === 'ok'
  return (
    <span
      className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
        ok
          ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
          : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
      }`}
    >
      {ok ? '✓' : '✗'} TCP {ok ? ms(tcp.durationMs) : (tcp.error ?? 'ERROR')}
    </span>
  )
}

function TlsBadge({ tls }: { tls: TlsResult | null | undefined }) {
  if (tls == null) return null
  const ok = tls.status === 'ok'
  return (
    <span
      className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
        ok
          ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
          : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
      }`}
    >
      {ok ? '✓' : '✗'} TLS {ok ? ms(tls.durationMs) : (tls.error ?? 'ERROR')}
    </span>
  )
}

function HttpBadge({ http }: { http: HttpResult | null | undefined }) {
  if (http == null) return null
  const ok = typeof http.status === 'number' && http.status < 500
  const label =
    typeof http.status === 'number' ? `HTTP ${http.status} ${ms(http.durationMs)}` : 'HTTP ERROR'
  return (
    <span
      className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
        ok
          ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
          : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
      }`}
    >
      {ok ? '✓' : '✗'} {label}
    </span>
  )
}

function GitProbeBadge({ label, result }: { label: string; result: GitProbeResult }) {
  const ok = result.status === 'ok'
  const detail = ok ? `${result.httpStatus} ${ms(result.durationMs)}` : (result.error ?? 'ERROR')
  return (
    <span
      className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
        ok
          ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
          : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
      }`}
    >
      {ok ? '✓' : '✗'} {label} {detail}
    </span>
  )
}

function formatSteps(steps: LogStep[]): string {
  return steps
    .map((s) => {
      const time = new Date(s.timestamp).toISOString().substring(11, 23) // HH:MM:SS.mmm
      const dur = s.durationMs != null ? ` (${s.durationMs}ms)` : ''
      const label = s.step.padEnd(12)
      return `[${time}] ${label} ${s.detail}${dur}`
    })
    .join('\n')
}

function DiagnosticLog({ steps }: { steps: LogStep[] }) {
  const [copied, setCopied] = useState(false)
  const textRef = useRef<HTMLPreElement>(null)

  function handleCopy() {
    navigator.clipboard.writeText(formatSteps(steps)).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="relative mt-2">
      <button
        onClick={handleCopy}
        title="Copy to clipboard"
        className="absolute top-2 right-2 text-xs px-2 py-0.5 rounded bg-slate-600 hover:bg-slate-500 text-slate-200 transition-colors"
      >
        {copied ? 'Copied!' : 'Copy'}
      </button>
      <pre
        ref={textRef}
        className="text-xs font-mono bg-slate-900 text-slate-200 rounded p-3 pr-20 overflow-x-auto whitespace-pre leading-5"
      >
        {formatSteps(steps)}
      </pre>
    </div>
  )
}

function ConnectivityRow({ result }: { name: string; result: ProviderConnectivity }) {
  const tcpOk = result.tcp.status === 'ok'
  const tlsOk = result.tls == null || result.tls.status === 'ok'

  return (
    <div className="border border-gray-200 rounded-lg p-4 space-y-2 dark:border-slate-700">
      {/* Header row */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex flex-wrap gap-1.5 shrink-0">
          <TcpBadge tcp={result.tcp} />
          <TlsBadge tls={result.tls} />
          <HttpBadge http={result.http} />
        </div>
        <span className="text-xs text-gray-400 font-mono text-right break-all dark:text-gray-500">
          {result.uri}
        </span>
      </div>
      {result.gitProbe && (
        <div className="flex flex-wrap gap-1.5">
          <GitProbeBadge label="fetch" result={result.gitProbe.uploadPack} />
          <GitProbeBadge label="push" result={result.gitProbe.receivePack} />
        </div>
      )}

      {/* Error details */}
      {!tcpOk && (
        <div className="text-xs font-mono bg-red-50 text-red-700 rounded px-2 py-1.5 space-y-0.5 dark:bg-red-900/20 dark:text-red-300">
          <div>
            TCP {result.tcp.host}:{result.tcp.port} → <strong>{result.tcp.error}</strong> (
            {ms(result.tcp.durationMs)})
          </div>
          {result.tcp.detail && (
            <div className="text-red-500 dark:text-red-400">{result.tcp.detail}</div>
          )}
        </div>
      )}
      {tcpOk && !tlsOk && result.tls && (
        <div className="text-xs font-mono bg-red-50 text-red-700 rounded px-2 py-1.5 space-y-0.5 dark:bg-red-900/20 dark:text-red-300">
          <div>
            TLS → <strong>{result.tls.error}</strong> ({ms(result.tls.durationMs)})
          </div>
          {result.tls.detail && (
            <div className="text-red-500 dark:text-red-400">{result.tls.detail}</div>
          )}
        </div>
      )}
      {result.http && typeof result.http.status === 'string' && (
        <div className="text-xs font-mono bg-red-50 text-red-700 rounded px-2 py-1.5 dark:bg-red-900/20 dark:text-red-300">
          HTTP → ERROR ({ms(result.http.durationMs)})
          {result.http.detail && (
            <div className="text-red-500 dark:text-red-400">{result.http.detail}</div>
          )}
        </div>
      )}
      {result.gitProbe &&
        (['uploadPack', 'receivePack'] as const)
          .filter((k) => result.gitProbe![k].status === 'error')
          .map((k) => {
            const r = result.gitProbe![k]
            const label = k === 'uploadPack' ? 'fetch (upload-pack)' : 'push (receive-pack)'
            return (
              <div
                key={k}
                className="text-xs font-mono bg-red-50 text-red-700 rounded px-2 py-1.5 space-y-0.5 dark:bg-red-900/20 dark:text-red-300"
              >
                <div>
                  Git {label} → <strong>{r.error}</strong> ({ms(r.durationMs)})
                </div>
                {r.detail && <div className="text-red-500 dark:text-red-400">{r.detail}</div>}
                {r.probeUrl && (
                  <div className="text-red-400 break-all dark:text-red-500">URL: {r.probeUrl}</div>
                )}
              </div>
            )
          })}

      {/* Success details */}
      {tcpOk && tlsOk && result.tls && result.tls.status === 'ok' && (
        <div className="text-xs text-gray-400 font-mono dark:text-gray-500">
          {result.tls.protocol} · {result.tls.cipher}
          {result.tls.peerCn && <span className="ml-2">· CN={result.tls.peerCn}</span>}
        </div>
      )}
      {result.http && typeof result.http.status === 'number' && result.http.location && (
        <div className="text-xs text-gray-400 font-mono dark:text-gray-500">
          → {result.http.location}
        </div>
      )}
      {result.gitProbe && (
        <>
          {result.gitProbe.uploadPack.status === 'ok' && result.gitProbe.uploadPack.contentType && (
            <div className="text-xs text-gray-400 font-mono dark:text-gray-500">
              fetch content-type: {result.gitProbe.uploadPack.contentType}
            </div>
          )}
          {result.gitProbe.receivePack.status === 'ok' &&
            result.gitProbe.receivePack.contentType && (
              <div className="text-xs text-gray-400 font-mono dark:text-gray-500">
                push content-type: {result.gitProbe.receivePack.contentType}
              </div>
            )}
        </>
      )}
      {result.steps && result.steps.length > 0 && <DiagnosticLog steps={result.steps} />}
    </div>
  )
}

function humanBytes(n: number): string {
  if (n <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(n) / Math.log(1024)))
  const v = n / Math.pow(1024, i)
  return `${i === 0 ? v : v.toFixed(1)} ${units[i]}`
}

function fmtTime(ms: number): string {
  if (!ms) return '—'
  return new Date(ms).toLocaleString()
}

const MODE_LABELS: Record<string, string> = {
  server: 'Server mode',
  proxy: 'Transparent proxy',
}

function CacheRow({
  mode,
  entry,
  onChanged,
}: {
  mode: string
  entry: CacheEntry
  onChanged: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const [refs, setRefs] = useState<CacheRef[] | null>(null)
  const [refsError, setRefsError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function toggleRefs() {
    const next = !expanded
    setExpanded(next)
    if (next && refs == null) {
      try {
        setRefs(await fetchCacheRefs(mode, entry.cacheKey))
        setRefsError(null)
      } catch (e) {
        setRefsError(e instanceof Error ? e.message : 'Failed to load refs')
      }
    }
  }

  async function invalidate() {
    if (!window.confirm(`Invalidate the local mirror for ${entry.remoteUrl}?`)) return
    setBusy(true)
    try {
      await invalidateCacheEntry(mode, entry.cacheKey)
      onChanged()
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Invalidation failed')
      setBusy(false)
    }
  }

  return (
    <>
      <tr className="border-t border-gray-100 dark:border-slate-700">
        <td className="py-2 pr-3 font-mono text-xs text-gray-800 dark:text-gray-200 break-all">
          {entry.remoteUrl}
          {entry.shallow && (
            <span className="ml-2 inline-block text-[10px] font-medium px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300">
              {entry.unshallowed ? 'deepened' : 'shallow'}
            </span>
          )}
        </td>
        <td className="py-2 pr-3 whitespace-nowrap text-right">
          <button
            onClick={toggleRefs}
            className="text-xs text-slate-600 hover:underline dark:text-slate-300"
            disabled={entry.refCount < 0}
          >
            {entry.refCount < 0
              ? 'refs n/a'
              : `${entry.refCount} ref${entry.refCount === 1 ? '' : 's'}`}
            {entry.refCount > 0 && <span className="ml-1">{expanded ? '▾' : '▸'}</span>}
          </button>
        </td>
        <td className="py-2 pr-3 whitespace-nowrap text-right text-gray-600 dark:text-gray-400">
          {humanBytes(entry.sizeBytes)}
        </td>
        <td className="py-2 pr-3 whitespace-nowrap text-xs text-gray-500 dark:text-gray-400">
          {fmtTime(entry.cachedAtMillis)}
        </td>
        <td className="py-2 pr-3 whitespace-nowrap text-xs text-gray-500 dark:text-gray-400">
          {fmtTime(entry.lastFetchedAtMillis)}
        </td>
        <td className="py-2 whitespace-nowrap text-right">
          <button
            onClick={invalidate}
            disabled={busy}
            className="px-2.5 py-1 bg-red-600 hover:bg-red-500 disabled:opacity-50 text-white text-xs rounded transition-colors"
          >
            {busy ? '…' : 'Invalidate'}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr className="bg-gray-50 dark:bg-slate-900/40">
          <td colSpan={6} className="py-2 px-3">
            {refsError && <p className="text-xs text-red-600 dark:text-red-400">{refsError}</p>}
            {refs && refs.length === 0 && (
              <p className="text-xs text-gray-400 dark:text-gray-500">No refs.</p>
            )}
            {refs && refs.length > 0 && (
              <ul className="space-y-0.5">
                {refs.map((r) => (
                  <li
                    key={r.name}
                    className="font-mono text-[11px] text-gray-600 dark:text-gray-400"
                  >
                    <span
                      className={
                        'inline-block w-12 ' +
                        (r.type === 'tag'
                          ? 'text-purple-600 dark:text-purple-400'
                          : 'text-blue-600 dark:text-blue-400')
                      }
                    >
                      {r.type}
                    </span>
                    {r.name}
                    <span className="ml-2 text-gray-400 dark:text-gray-500">
                      {r.objectId.slice(0, 10)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

function CacheModeTable({
  mode,
  entries,
  onChanged,
}: {
  mode: string
  entries: CacheEntry[]
  onChanged: () => void
}) {
  const [busy, setBusy] = useState(false)

  async function invalidateAll() {
    if (
      !window.confirm(
        `Invalidate all ${entries.length} ${MODE_LABELS[mode] ?? mode} mirror(s)? Each will be re-cloned on next use.`,
      )
    )
      return
    setBusy(true)
    try {
      await invalidateCacheAll(mode)
      onChanged()
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Invalidation failed')
      setBusy(false)
    }
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
          {MODE_LABELS[mode] ?? mode}{' '}
          <span className="font-normal text-gray-400 dark:text-gray-500">
            ({entries.length} mirror{entries.length === 1 ? '' : 's'})
          </span>
        </h3>
        {entries.length > 0 && (
          <button
            onClick={invalidateAll}
            disabled={busy}
            className="px-2.5 py-1 border border-red-300 text-red-700 hover:bg-red-50 disabled:opacity-50 text-xs rounded transition-colors dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/20"
          >
            {busy ? '…' : 'Invalidate all'}
          </button>
        )}
      </div>
      {entries.length === 0 ? (
        <p className="text-xs text-gray-400 dark:text-gray-500">No mirrors cached.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-400 dark:text-gray-500">
                <th className="py-1 pr-3 font-medium">Repository</th>
                <th className="py-1 pr-3 font-medium text-right">Refs</th>
                <th className="py-1 pr-3 font-medium text-right">Size</th>
                <th className="py-1 pr-3 font-medium">Cached</th>
                <th className="py-1 pr-3 font-medium">Last fetch</th>
                <th className="py-1 font-medium text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <CacheRow key={e.cacheKey} mode={mode} entry={e} onChanged={onChanged} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function CacheSection() {
  const [data, setData] = useState<CacheListResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setData(await fetchCache())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load cache')
    } finally {
      setLoading(false)
    }
  }

  // Initial load via .then (not a synchronous setState in the effect body); the Refresh button and child
  // onChanged callbacks reuse load(), which drives the loading state.
  useEffect(() => {
    let active = true
    fetchCache()
      .then((d) => active && setData(d))
      .catch((e) => active && setError(e instanceof Error ? e.message : 'Failed to load cache'))
    return () => {
      active = false
    }
  }, [])

  return (
    <section className="bg-white rounded-lg shadow p-6 space-y-4 dark:bg-slate-800">
      <div>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-medium text-gray-700 dark:text-gray-300">
            Local mirror cache
          </h2>
          <button
            onClick={load}
            disabled={loading}
            className="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-white text-xs rounded transition-colors"
          >
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
        <p className="text-sm text-gray-500 mt-1 dark:text-gray-400">
          fogwall keeps a local bare mirror of each proxied repo to inspect push content. Invalidate
          a mirror to force a fresh clone on its next use — the fix for a stale or poisoned mirror
          without restarting the pod. State is <strong>per-pod</strong>: this shows the cache of
          whichever pod served this request.
        </p>
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {data && (
        <div className="space-y-6">
          {(['server', 'proxy'] as const).map((mode) => (
            <CacheModeTable key={mode} mode={mode} entries={data[mode]} onChanged={load} />
          ))}
        </div>
      )}
    </section>
  )
}

export function Admin() {
  const [reloadStatus, setReloadStatus] = useState<'idle' | 'loading' | 'ok' | 'error'>('idle')
  const [reloadMessage, setReloadMessage] = useState<string | null>(null)
  const [reloadSection, setReloadSection] = useState<string>('all')

  const [connStatus, setConnStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle')
  const [connCheckedAt, setConnCheckedAt] = useState<string | null>(null)
  const [connResults, setConnResults] = useState<Record<string, ProviderConnectivity> | null>(null)
  const [connError, setConnError] = useState<string | null>(null)

  // Targeted probe state
  const [providerList, setProviderList] = useState<Provider[]>([])
  const [selectedProvider, setSelectedProvider] = useState<string>('')
  const [repoPath, setRepoPath] = useState<string>('')
  const [targetStatus, setTargetStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle')
  const [targetCheckedAt, setTargetCheckedAt] = useState<string | null>(null)
  const [targetResults, setTargetResults] = useState<Record<string, ProviderConnectivity> | null>(
    null,
  )
  const [targetError, setTargetError] = useState<string | null>(null)

  useEffect(() => {
    fetchProviders()
      .then((list: Provider[]) => {
        setProviderList(list)
        if (list.length > 0) setSelectedProvider(list[0].name)
      })
      .catch(console.error)
  }, [])

  async function handleReload() {
    setReloadStatus('loading')
    setReloadMessage(null)
    try {
      const result = await triggerConfigReload(reloadSection)
      setReloadMessage(result.message)
      setReloadStatus('ok')
    } catch (e) {
      setReloadMessage(e instanceof Error ? e.message : 'Unknown error')
      setReloadStatus('error')
    }
  }

  async function handleConnectivityCheck() {
    setConnStatus('loading')
    setConnResults(null)
    setConnCheckedAt(null)
    setConnError(null)
    try {
      const result = await checkConnectivity()
      setConnResults(result.providers)
      setConnCheckedAt(result.checkedAt)
      setConnStatus('done')
    } catch (e) {
      setConnError(e instanceof Error ? e.message : 'Unknown error')
      setConnStatus('error')
    }
  }

  async function handleTargetedCheck() {
    if (!selectedProvider) return
    setTargetStatus('loading')
    setTargetResults(null)
    setTargetCheckedAt(null)
    setTargetError(null)
    try {
      const result = await checkTargetedConnectivity(selectedProvider, repoPath)
      setTargetResults(result.providers)
      setTargetCheckedAt(result.checkedAt)
      setTargetStatus('done')
    } catch (e) {
      setTargetError(e instanceof Error ? e.message : 'Unknown error')
      setTargetStatus('error')
    }
  }

  return (
    <div className="max-w-2xl mx-auto px-6 py-8 space-y-6">
      <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">Admin</h1>

      <section className="bg-white rounded-lg shadow p-6 space-y-4 dark:bg-slate-800">
        <div>
          <h2 className="text-lg font-medium text-gray-700 dark:text-gray-300">
            Configuration Reload
          </h2>
          <p className="text-sm text-gray-500 mt-1 dark:text-gray-400">
            Reloads config from the configured source (file or git) without restarting the server.
            Select a section to reload only that portion, or choose <em>All sections</em> to reload
            everything. Provider, server, and database changes still require a restart.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <select
            value={reloadSection}
            onChange={(e) => setReloadSection(e.target.value)}
            disabled={reloadStatus === 'loading'}
            className="px-3 py-2 border border-gray-300 rounded text-sm text-gray-700 bg-white focus:outline-none focus:ring-2 focus:ring-slate-400 disabled:opacity-50 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200"
          >
            <option value="all">All sections</option>
            <option value="commit">Commit rules</option>
            <option value="diff-scan">Diff scan</option>
            <option value="secret-scan">Secret scan</option>
            <option value="rules">Rules</option>
            <option value="permissions">Permissions</option>
          </select>

          <button
            onClick={handleReload}
            disabled={reloadStatus === 'loading'}
            className="px-4 py-2 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-white text-sm rounded transition-colors"
          >
            {reloadStatus === 'loading' ? 'Reloading…' : 'Reload config'}
          </button>

          {reloadMessage && (
            <p
              className={
                'text-sm ' +
                (reloadStatus === 'error'
                  ? 'text-red-600 dark:text-red-400'
                  : 'text-green-700 dark:text-green-400')
              }
            >
              {reloadMessage}
            </p>
          )}
        </div>
      </section>

      <CacheSection />

      <section className="bg-white rounded-lg shadow p-6 space-y-4 dark:bg-slate-800">
        <div>
          <h2 className="text-lg font-medium text-gray-700 dark:text-gray-300">
            Provider Connectivity
          </h2>
          <p className="text-sm text-gray-500 mt-1 dark:text-gray-400">
            Tests outbound connectivity to each configured upstream provider: TCP handshake, TLS
            negotiation, and HTTP response. Error codes: REFUSED (RST received — port closed or
            firewall REJECT), TIMEOUT (no response — firewall DROP), RESET (connection torn down
            mid-stream). Full details logged at INFO level in{' '}
            <code className="font-mono">application.log</code>.
          </p>
        </div>

        <div className="flex items-center gap-4">
          <button
            onClick={handleConnectivityCheck}
            disabled={connStatus === 'loading'}
            className="px-4 py-2 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-white text-sm rounded transition-colors"
          >
            {connStatus === 'loading' ? 'Checking…' : 'Run connectivity check'}
          </button>
          {connCheckedAt && (
            <span className="text-xs text-gray-400 dark:text-gray-500">
              checked at {new Date(connCheckedAt).toLocaleTimeString()}
            </span>
          )}
        </div>

        {connError && <p className="text-sm text-red-600 dark:text-red-400">{connError}</p>}

        {connResults && (
          <div className="space-y-2">
            {Object.entries(connResults).map(([name, result]) => (
              <ConnectivityRow key={name} name={name} result={result} />
            ))}
          </div>
        )}
      </section>

      <section className="bg-white rounded-lg shadow p-6 space-y-4 dark:bg-slate-800">
        <div>
          <h2 className="text-lg font-medium text-gray-700 dark:text-gray-300">
            Targeted Git Probe
          </h2>
          <p className="text-sm text-gray-500 mt-1 dark:text-gray-400">
            Runs the full connectivity check for a single provider, then sends{' '}
            <code className="font-mono">GET /info/refs?service=git-upload-pack</code> with{' '}
            <code className="font-mono">User-Agent: git/2.x.x</code> to a specific repo — the same
            request git makes at the start of a clone or fetch. Use this to detect CSAB or DLP
            appliances that pass generic HTTP but block git-specific URL patterns. Any HTTP response
            (200, 401, 403, 404) means the request reached the upstream; TIMEOUT or RESET indicates
            git-specific filtering.
          </p>
        </div>

        <div className="space-y-3">
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1 sm:w-48">
              <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
                Provider
              </label>
              <select
                value={selectedProvider}
                onChange={(e) => setSelectedProvider(e.target.value)}
                className="border border-gray-300 rounded px-3 py-2 text-sm text-gray-800 bg-white focus:outline-none focus:ring-2 focus:ring-slate-400 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200"
              >
                {providerList.map((p) => (
                  <option key={p.name} value={p.name}>
                    {p.name} ({p.host})
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
                Repo path{' '}
                <span className="font-normal text-gray-400 dark:text-gray-500">
                  (optional — skips git probe if blank)
                </span>
              </label>
              <div className="flex items-stretch">
                <span className="inline-flex items-center px-3 rounded-l border border-r-0 border-gray-300 bg-gray-50 text-gray-400 text-xs font-mono select-none dark:bg-slate-700 dark:border-slate-600 dark:text-gray-500">
                  {providerList.find((p) => p.name === selectedProvider)?.uri ?? ''}
                </span>
                <input
                  type="text"
                  placeholder="/owner/repo.git"
                  value={repoPath}
                  onChange={(e) => setRepoPath(e.target.value)}
                  className="flex-1 border border-gray-300 rounded-r px-3 py-2 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-slate-400 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200"
                />
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <button
              onClick={handleTargetedCheck}
              disabled={targetStatus === 'loading' || !selectedProvider}
              className="px-4 py-2 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-white text-sm rounded transition-colors"
            >
              {targetStatus === 'loading' ? 'Checking…' : 'Run targeted check'}
            </button>
            {targetCheckedAt && (
              <span className="text-xs text-gray-400 dark:text-gray-500">
                checked at {new Date(targetCheckedAt).toLocaleTimeString()}
              </span>
            )}
          </div>
        </div>

        {targetError && <p className="text-sm text-red-600 dark:text-red-400">{targetError}</p>}

        {targetResults && (
          <div className="space-y-2">
            {Object.entries(targetResults).map(([name, result]) => (
              <ConnectivityRow key={name} name={name} result={result} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
