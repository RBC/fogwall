import { useEffect, useState } from 'react'
import type { CacheEntry, CacheListResponse, CacheRef } from '../types'
import { fetchCache, fetchCacheRefs, invalidateCacheAll, invalidateCacheEntry } from '../api'

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
    <section className="bg-white rounded-lg shadow p-6 space-y-2 dark:bg-slate-800">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
          {MODE_LABELS[mode] ?? mode}{' '}
          <span className="font-normal text-gray-400 dark:text-gray-500">
            ({entries.length} mirror{entries.length === 1 ? '' : 's'})
          </span>
        </h2>
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
    </section>
  )
}

export function MirrorCache() {
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
    <div className="max-w-6xl px-6 py-8 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">
          Local mirror cache
        </h1>
        <button
          onClick={load}
          disabled={loading}
          className="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-white text-xs rounded transition-colors"
        >
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>
      <p className="text-sm text-gray-500 dark:text-gray-400">
        fogwall keeps a local bare mirror of each proxied repo to inspect push content. Invalidate a
        mirror to force a fresh clone on its next use — the fix for a stale or poisoned mirror
        without restarting the pod. State is <strong>per-pod</strong>: this shows the cache of
        whichever pod served this request.
      </p>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {data &&
        (['server', 'proxy'] as const).map((mode) => (
          <CacheModeTable key={mode} mode={mode} entries={data[mode]} onChanged={load} />
        ))}
    </div>
  )
}
