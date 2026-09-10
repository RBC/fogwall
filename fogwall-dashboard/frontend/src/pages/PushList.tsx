import { ExtIcon } from '../components/ExtIcon'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { approvePush, fetchProviders, fetchPushCounts, fetchPushes, rejectPush } from '../api'
import { useToast } from '../components/Toast'
import { StatusBadge } from '../components/StatusBadge'
import type { CurrentUser, Provider, PushRecord, PushStatus } from '../types'

const PAGE_SIZE = 25
const STATUSES: PushStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'FORWARDED',
  'RECEIVED',
  'CANCELED',
  'ERROR',
]

const STATUS_COLORS: Record<string, string> = {
  PENDING:
    'bg-amber-50 text-amber-700 border-amber-200 hover:bg-amber-100 dark:bg-amber-900/20 dark:text-amber-300 dark:border-amber-700 dark:hover:bg-amber-900/40',
  APPROVED:
    'bg-green-50 text-green-700 border-green-200 hover:bg-green-100 dark:bg-green-900/20 dark:text-green-300 dark:border-green-700 dark:hover:bg-green-900/40',
  FORWARDED:
    'bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100 dark:bg-blue-900/20 dark:text-blue-300 dark:border-blue-700 dark:hover:bg-blue-900/40',
  REJECTED:
    'bg-red-50 text-red-700 border-red-200 hover:bg-red-100 dark:bg-red-900/20 dark:text-red-300 dark:border-red-700 dark:hover:bg-red-900/40',
  RECEIVED:
    'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100 dark:bg-slate-700/40 dark:text-slate-300 dark:border-slate-600 dark:hover:bg-slate-700/60',
  CANCELED:
    'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100 dark:bg-slate-700/40 dark:text-gray-400 dark:border-slate-600 dark:hover:bg-gray-700/60',
  ERROR:
    'bg-rose-50 text-rose-800 border-rose-200 hover:bg-rose-100 dark:bg-rose-900/20 dark:text-rose-300 dark:border-rose-700 dark:hover:bg-rose-900/40',
}

// Date-range presets. Each resolves to a { from, to } pair of ISO instants applied to the push timestamp
// (from inclusive, to exclusive); '' means no bound. Custom uses the two date inputs.
const DATE_RANGES: { value: string; label: string }[] = [
  { value: '', label: 'Any time' },
  { value: '24h', label: 'Last 24 hours' },
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
  { value: 'custom', label: 'Custom range' },
]

const DAY_MS = 86_400_000

interface Filters {
  status: string
  search: string
  myPushesOnly: boolean
  newestFirst: boolean
  provider: string
  branch: string
  authorEmail: string
  user: string
  dateRange: string
  customFrom: string
  customTo: string
}

function dateBounds(f: Filters): { from?: string; to?: string } {
  const now = Date.now()
  switch (f.dateRange) {
    case '24h':
      return { from: new Date(now - DAY_MS).toISOString() }
    case '7d':
      return { from: new Date(now - 7 * DAY_MS).toISOString() }
    case '30d':
      return { from: new Date(now - 30 * DAY_MS).toISOString() }
    case 'custom': {
      const b: { from?: string; to?: string } = {}
      if (f.customFrom) b.from = new Date(f.customFrom + 'T00:00:00Z').toISOString()
      // to is exclusive; include the whole selected end day by pushing to the next midnight.
      if (f.customTo)
        b.to = new Date(new Date(f.customTo + 'T00:00:00Z').getTime() + DAY_MS).toISOString()
      return b
    }
    default:
      return {}
  }
}

// The username to actually query: an explicit advanced "user" filter wins, else the "My pushes" toggle.
function effectiveUser(f: Filters, currentUser: CurrentUser | null): string {
  if (f.user.trim()) return f.user.trim()
  if (f.myPushesOnly && currentUser?.username) return currentUser.username
  return ''
}

// Shared filter params for both the list and counts endpoints (status/pagination/order handled by the caller).
function applyFilterParams(params: URLSearchParams, f: Filters, currentUser: CurrentUser | null) {
  if (f.search.trim()) params.set('search', f.search.trim())
  const user = effectiveUser(f, currentUser)
  if (user) params.set('user', user)
  if (f.provider) params.set('provider', f.provider)
  if (f.branch.trim()) params.set('branch', f.branch.trim())
  if (f.authorEmail.trim()) params.set('authorEmail', f.authorEmail.trim())
  const { from, to } = dateBounds(f)
  if (from) params.set('from', from)
  if (to) params.set('to', to)
}

function rangeLabel(value: string): string {
  return DATE_RANGES.find((r) => r.value === value)?.label ?? value
}

function formatTime(ts: string | number | undefined) {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return String(ts)
  }
}

interface PushListProps {
  currentUser: CurrentUser | null
  bulkReviewEnabled?: boolean
}

export function PushList({ currentUser, bulkReviewEnabled = false }: PushListProps) {
  const navigate = useNavigate()
  const [pushes, setPushes] = useState<PushRecord[]>([])
  const [filters, setFilters] = useState<Filters>({
    status: '',
    search: '',
    myPushesOnly: false,
    newestFirst: true,
    provider: '',
    branch: '',
    authorEmail: '',
    user: '',
    dateRange: '',
    customFrom: '',
    customTo: '',
  })
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [providers, setProviders] = useState<string[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [lastRefresh, setLastRefresh] = useState('')
  const [counts, setCounts] = useState<Partial<Record<string, number>>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bulkReason, setBulkReason] = useState('')
  const [bulkWorking, setBulkWorking] = useState(false)
  const toast = useToast()

  // Patch a subset of the filters, resetting to the first page — filter changes always restart pagination.
  const setFilter = useCallback((patch: Partial<Filters>) => {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }, [])

  // Only allow selection when bulk review is enabled and viewing PENDING pushes
  const selectionEnabled = bulkReviewEnabled && filters.status === 'PENDING'

  useEffect(() => {
    fetchProviders()
      .then((data: Provider[]) => setProviders(data.map((p) => p.name)))
      .catch(() => setProviders([]))
  }, [])

  function toggleSelect(id: string, e: React.MouseEvent) {
    e.stopPropagation()
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function toggleSelectAll() {
    const blockedIds = pushes.filter((p) => p.status === 'PENDING').map((p) => p.id)
    setSelectedIds((prev) => (prev.size === blockedIds.length ? new Set() : new Set(blockedIds)))
  }

  const load = useCallback(
    async (f: Filters, pageNum: number) => {
      const params = new URLSearchParams({
        limit: String(PAGE_SIZE + 1),
        offset: String(pageNum * PAGE_SIZE),
      })
      if (f.status) params.set('status', f.status)
      applyFilterParams(params, f, currentUser)
      params.set('newestFirst', String(f.newestFirst))
      // Fetch one extra to detect if there's a next page
      const data: PushRecord[] = await fetchPushes(params)
      setHasMore(data.length > PAGE_SIZE)
      setPushes(data.slice(0, PAGE_SIZE))
      setLastRefresh(new Date().toLocaleTimeString())
    },
    [currentUser],
  )

  const loadCounts = useCallback(
    async (f: Filters) => {
      const params = new URLSearchParams()
      applyFilterParams(params, f, currentUser)
      const data = await fetchPushCounts(params)
      setCounts(data)
    },
    [currentUser],
  )

  async function handleBulkApprove() {
    if (!bulkReason.trim() || selectedIds.size === 0) return
    setBulkWorking(true)
    try {
      await Promise.all(
        [...selectedIds].map((id) =>
          approvePush(id, {
            reviewerUsername: currentUser?.username ?? '',
            reviewerEmail: currentUser?.emails[0]?.email ?? '',
            reason: bulkReason,
          }),
        ),
      )
      setSelectedIds(new Set())
      setBulkReason('')
      await load(filters, page)
    } catch (e) {
      toast.error(String(e))
    } finally {
      setBulkWorking(false)
    }
  }

  async function handleBulkReject() {
    if (!bulkReason.trim() || selectedIds.size === 0) return
    setBulkWorking(true)
    try {
      await Promise.all(
        [...selectedIds].map((id) =>
          rejectPush(id, {
            reviewerUsername: currentUser?.username ?? '',
            reviewerEmail: currentUser?.emails[0]?.email ?? '',
            reason: bulkReason,
          }),
        ),
      )
      setSelectedIds(new Set())
      setBulkReason('')
      await load(filters, page)
    } catch (e) {
      toast.error(String(e))
    } finally {
      setBulkWorking(false)
    }
  }

  // Clear selection when leaving PENDING filter
  useEffect(() => {
    if (filters.status !== 'PENDING') void Promise.resolve().then(() => setSelectedIds(new Set()))
  }, [filters.status])

  // Load data (debounced so typing in a text filter doesn't fire a request per keystroke) plus a periodic refresh.
  useEffect(() => {
    const debounce = setTimeout(() => load(filters, page), 200)
    const timer = setInterval(() => load(filters, page), 10_000)
    return () => {
      clearTimeout(debounce)
      clearInterval(timer)
    }
  }, [filters, page, load])

  // Counts ignore status/pagination/order, so recompute only when the shared filter inputs change.
  const countKey = JSON.stringify({
    search: filters.search,
    myPushesOnly: filters.myPushesOnly,
    provider: filters.provider,
    branch: filters.branch,
    authorEmail: filters.authorEmail,
    user: filters.user,
    dateRange: filters.dateRange,
    customFrom: filters.customFrom,
    customTo: filters.customTo,
  })
  useEffect(() => {
    const debounce = setTimeout(() => loadCounts(filters), 200)
    const timer = setInterval(() => loadCounts(filters), 10_000)
    return () => {
      clearTimeout(debounce)
      clearInterval(timer)
    }
    // filters is intentionally omitted; countKey captures the fields counts actually depends on.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [countKey, loadCounts])

  // Active advanced filters, rendered as removable chips and counted on the Filters button.
  const chips: { key: string; label: string; value: string; clear: () => void }[] = []
  if (filters.provider)
    chips.push({
      key: 'provider',
      label: 'provider',
      value: filters.provider,
      clear: () => setFilter({ provider: '' }),
    })
  if (filters.branch.trim())
    chips.push({
      key: 'branch',
      label: 'branch',
      value: filters.branch.trim(),
      clear: () => setFilter({ branch: '' }),
    })
  if (filters.authorEmail.trim())
    chips.push({
      key: 'author',
      label: 'author',
      value: filters.authorEmail.trim(),
      clear: () => setFilter({ authorEmail: '' }),
    })
  if (filters.user.trim())
    chips.push({
      key: 'user',
      label: 'user',
      value: filters.user.trim(),
      clear: () => setFilter({ user: '' }),
    })
  if (filters.dateRange)
    chips.push({
      key: 'date',
      label: 'date',
      value: rangeLabel(filters.dateRange),
      clear: () => setFilter({ dateRange: '', customFrom: '', customTo: '' }),
    })

  function clearAllAdvanced() {
    setFilter({
      provider: '',
      branch: '',
      authorEmail: '',
      user: '',
      dateRange: '',
      customFrom: '',
      customTo: '',
    })
  }

  const advLabelClass = 'block text-xs font-medium text-gray-500 mb-1 dark:text-gray-400'
  const advInputClass =
    'w-full border border-gray-300 rounded px-3 py-1.5 text-sm bg-white dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200 dark:placeholder-gray-400'

  return (
    <div>
      <div className="max-w-6xl px-6 pt-6">
        <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">Pushes</h1>
      </div>
      {/* Status summary chips */}
      <div className="max-w-6xl px-6 pt-3 flex gap-2 flex-wrap">
        <button
          onClick={() => setFilter({ status: '' })}
          className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
            filters.status === ''
              ? 'bg-gray-900 text-white border-gray-900 dark:bg-slate-100 dark:text-gray-900 dark:border-slate-100'
              : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50 dark:bg-slate-800 dark:text-gray-300 dark:border-slate-600 dark:hover:bg-gray-700'
          }`}
        >
          All
        </button>
        {STATUSES.map((s) => (
          <button
            key={s}
            onClick={() => setFilter({ status: s })}
            className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
              filters.status === s
                ? 'bg-gray-900 text-white border-gray-900 dark:bg-slate-100 dark:text-gray-900 dark:border-slate-100'
                : (STATUS_COLORS[s] ??
                  'bg-white text-gray-600 border-gray-300 hover:bg-gray-50 dark:bg-slate-800 dark:text-gray-300 dark:border-slate-600 dark:hover:bg-gray-700')
            }`}
          >
            {s.charAt(0) + s.slice(1).toLowerCase()}
            {counts[s] != null ? ` · ${counts[s]}` : ''}
          </button>
        ))}
      </div>

      {/* Bulk action bar — shown when items are selected */}
      {selectionEnabled && selectedIds.size > 0 && (
        <div className="max-w-6xl px-6 py-3 flex gap-3 flex-wrap items-center bg-amber-50 border-y border-amber-200 dark:bg-amber-900/20 dark:border-amber-700">
          <span className="text-sm font-medium text-amber-800 dark:text-amber-300">
            {selectedIds.size} push{selectedIds.size !== 1 ? 'es' : ''} selected
          </span>
          <input
            value={bulkReason}
            onChange={(e) => setBulkReason(e.target.value)}
            type="text"
            placeholder="Reason (required)..."
            className="flex-1 min-w-40 border border-amber-300 rounded px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-amber-300 dark:bg-slate-700 dark:border-amber-700 dark:text-gray-200 dark:placeholder-gray-400 dark:focus:ring-amber-600"
          />
          <button
            onClick={handleBulkApprove}
            disabled={bulkWorking || !bulkReason.trim()}
            className="px-3 py-1.5 text-sm font-medium rounded bg-green-600 text-white hover:bg-green-700 disabled:opacity-50"
          >
            {bulkWorking ? '…' : `✓ Approve ${selectedIds.size}`}
          </button>
          <button
            onClick={handleBulkReject}
            disabled={bulkWorking || !bulkReason.trim()}
            className="px-3 py-1.5 text-sm font-medium rounded bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
          >
            {bulkWorking ? '…' : `✗ Reject ${selectedIds.size}`}
          </button>
          <button
            onClick={() => {
              setSelectedIds(new Set())
              setBulkReason('')
            }}
            className="text-sm text-amber-700 hover:underline ml-auto dark:text-amber-400"
          >
            Clear
          </button>
        </div>
      )}

      {/* Primary filter bar */}
      <div className="max-w-6xl px-6 py-3 flex gap-3 flex-wrap items-center border-b border-gray-100 dark:border-slate-700">
        {selectionEnabled && (
          <label className="flex items-center gap-1.5 text-sm text-gray-500 cursor-pointer select-none dark:text-gray-400">
            <input
              type="checkbox"
              checked={
                selectedIds.size > 0 &&
                selectedIds.size === pushes.filter((p) => p.status === 'PENDING').length
              }
              onChange={toggleSelectAll}
              className="rounded border-gray-300 dark:border-slate-600"
            />
            <span className="text-xs">All</span>
          </label>
        )}
        <input
          value={filters.search}
          onChange={(e) => setFilter({ search: e.target.value })}
          type="text"
          placeholder="Filter by project or repo..."
          className="border border-gray-300 rounded px-3 py-1.5 text-sm bg-white shadow-sm w-52 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200 dark:placeholder-gray-400"
        />

        {currentUser && (
          <button
            onClick={() => setFilter({ myPushesOnly: !filters.myPushesOnly })}
            className={`px-3 py-1.5 text-sm rounded border transition-colors ${
              filters.myPushesOnly
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50 dark:bg-slate-800 dark:text-gray-300 dark:border-slate-600 dark:hover:bg-gray-700'
            }`}
          >
            My pushes
          </button>
        )}

        <button
          onClick={() => setShowAdvanced((v) => !v)}
          aria-expanded={showAdvanced}
          className={`px-3 py-1.5 text-sm rounded border transition-colors inline-flex items-center gap-1.5 ${
            showAdvanced || chips.length > 0
              ? 'bg-gray-900 text-white border-gray-900 dark:bg-slate-100 dark:text-gray-900 dark:border-slate-100'
              : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50 dark:bg-slate-800 dark:text-gray-300 dark:border-slate-600 dark:hover:bg-gray-700'
          }`}
        >
          <svg
            className="w-3.5 h-3.5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 5h18M6 12h12M10 19h4" />
          </svg>
          Filters
          {chips.length > 0 && (
            <span className="ml-0.5 inline-flex items-center justify-center rounded-full bg-blue-600 text-white text-[10px] font-semibold w-4 h-4">
              {chips.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setFilter({ newestFirst: !filters.newestFirst })}
          className="px-3 py-1.5 text-sm rounded border border-gray-300 bg-white text-gray-600 hover:bg-gray-50 transition-colors dark:bg-slate-800 dark:text-gray-300 dark:border-slate-600 dark:hover:bg-gray-700"
          title={filters.newestFirst ? 'Currently: newest first' : 'Currently: oldest first'}
        >
          {filters.newestFirst ? '↓ Newest first' : '↑ Oldest first'}
        </button>

        <div className="ml-auto flex items-center gap-4 text-sm text-gray-400 dark:text-gray-500">
          <span>
            {pushes.length} record{pushes.length !== 1 ? 's' : ''}
            {page > 0 ? ` (page ${page + 1})` : ''}
          </span>
          {lastRefresh && <span>refreshed {lastRefresh}</span>}
          <button
            onClick={() => load(filters, page)}
            className="text-blue-600 hover:underline dark:text-blue-400"
          >
            &#8635; Refresh
          </button>
        </div>
      </div>

      {/* Active advanced-filter chips */}
      {chips.length > 0 && (
        <div className="max-w-6xl px-6 pt-3 flex gap-2 flex-wrap items-center">
          {chips.map((c) => (
            <span
              key={c.key}
              className="inline-flex items-center gap-1.5 rounded-full border border-gray-200 bg-gray-50 pl-2.5 pr-1.5 py-1 text-xs text-gray-600 dark:border-slate-600 dark:bg-slate-800 dark:text-gray-300"
            >
              <span className="text-gray-400 dark:text-gray-500">{c.label}:</span>
              <span className="font-medium">{c.value}</span>
              <button
                onClick={c.clear}
                aria-label={`Remove ${c.label} filter`}
                className="ml-0.5 text-gray-400 hover:text-gray-700 dark:hover:text-gray-100"
              >
                ✕
              </button>
            </span>
          ))}
          <button
            onClick={clearAllAdvanced}
            className="text-xs text-blue-600 hover:underline dark:text-blue-400"
          >
            Clear all
          </button>
        </div>
      )}

      {/* Advanced filters panel */}
      {showAdvanced && (
        <div className="max-w-6xl px-6 pt-3">
          <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-slate-700 dark:bg-slate-800/40">
            <div className="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-3 dark:text-gray-500">
              Advanced filters
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <label htmlFor="pf-provider" className={advLabelClass}>
                  Provider
                </label>
                <select
                  id="pf-provider"
                  value={filters.provider}
                  onChange={(e) => setFilter({ provider: e.target.value })}
                  className={advInputClass}
                >
                  <option value="">Any provider</option>
                  {providers.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="pf-date" className={advLabelClass}>
                  Date range
                </label>
                <select
                  id="pf-date"
                  value={filters.dateRange}
                  onChange={(e) => setFilter({ dateRange: e.target.value })}
                  className={advInputClass}
                >
                  {DATE_RANGES.map((r) => (
                    <option key={r.value} value={r.value}>
                      {r.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="pf-branch" className={advLabelClass}>
                  Branch
                </label>
                <input
                  id="pf-branch"
                  value={filters.branch}
                  onChange={(e) => setFilter({ branch: e.target.value })}
                  type="text"
                  placeholder="refs/heads/main"
                  className={advInputClass}
                />
              </div>
              <div>
                <label htmlFor="pf-author" className={advLabelClass}>
                  Author email
                </label>
                <input
                  id="pf-author"
                  value={filters.authorEmail}
                  onChange={(e) => setFilter({ authorEmail: e.target.value })}
                  type="text"
                  placeholder="dev@example.com"
                  className={advInputClass}
                />
              </div>
              <div>
                <label htmlFor="pf-user" className={advLabelClass}>
                  User
                </label>
                <input
                  id="pf-user"
                  value={filters.user}
                  onChange={(e) => setFilter({ user: e.target.value })}
                  type="text"
                  placeholder="another user's pushes"
                  className={advInputClass}
                />
              </div>
              {filters.dateRange === 'custom' && (
                <div className="flex gap-2 sm:col-span-2 lg:col-span-3">
                  <div className="flex-1">
                    <label htmlFor="pf-from" className={advLabelClass}>
                      From
                    </label>
                    <input
                      id="pf-from"
                      value={filters.customFrom}
                      onChange={(e) => setFilter({ customFrom: e.target.value })}
                      type="date"
                      className={advInputClass}
                    />
                  </div>
                  <div className="flex-1">
                    <label htmlFor="pf-to" className={advLabelClass}>
                      To
                    </label>
                    <input
                      id="pf-to"
                      value={filters.customTo}
                      onChange={(e) => setFilter({ customTo: e.target.value })}
                      type="date"
                      className={advInputClass}
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* List */}
      <div className="max-w-6xl px-6 space-y-2 py-4 pb-12">
        {pushes.length === 0 && (
          <div className="text-center text-gray-400 dark:text-gray-500 py-16">
            No push records found.
          </div>
        )}
        {pushes.map((push) => (
          <div
            key={push.id}
            onClick={() => navigate(`/push/${push.id}`)}
            className={`bg-white rounded-lg shadow border transition-colors cursor-pointer dark:bg-slate-800 ${
              selectedIds.has(push.id)
                ? 'border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-900/20'
                : 'border-gray-200 hover:border-blue-300 dark:border-slate-700 dark:hover:border-blue-500'
            }`}
          >
            <div className="flex items-center gap-4 px-5 py-3">
              {selectionEnabled && push.status === 'PENDING' && (
                <input
                  type="checkbox"
                  checked={selectedIds.has(push.id)}
                  onClick={(e) => toggleSelect(push.id, e)}
                  onChange={() => {}}
                  className="rounded border-gray-300 shrink-0 dark:border-slate-600"
                />
              )}
              <StatusBadge status={push.status} />
              <div className="flex-1 min-w-0 space-y-0.5">
                <div className="font-mono text-sm text-gray-900 truncate dark:text-gray-100">
                  {push.repoUrl ??
                    push.upstreamUrl ??
                    push.url ??
                    (push.project ?? '') + '/' + (push.repoName ?? 'unknown')}
                  {push.repoUrl && (
                    <a
                      href={push.repoUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      title="Open repo"
                      className="ml-1.5 text-blue-500 no-underline hover:text-blue-600 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      <ExtIcon />
                    </a>
                  )}
                </div>
                <div className="text-xs text-gray-500 truncate dark:text-gray-400">
                  {push.branch ?? '—'}
                </div>
                <div className="font-mono text-xs text-gray-400 break-all dark:text-gray-500">
                  {push.commitTo ?? '—'}
                  {push.commitUrl && (
                    <a
                      href={push.commitUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      title="Open commit"
                      className="ml-1.5 text-blue-500 no-underline hover:text-blue-600 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      <ExtIcon />
                    </a>
                  )}
                </div>
              </div>
              <div className="text-right text-sm text-gray-500 shrink-0 dark:text-gray-400">
                <div>{push.author ?? push.user ?? '—'}</div>
                {push.resolvedUser ? (
                  <span className="inline-flex items-center gap-0.5 text-xs text-green-600 font-medium dark:text-green-400">
                    ● identity resolved
                  </span>
                ) : push.user ? (
                  <span className="inline-flex items-center gap-0.5 text-xs text-gray-400 font-medium dark:text-gray-500">
                    ● identity unresolved
                  </span>
                ) : null}
                <div className="text-xs text-gray-400 mt-0.5 dark:text-gray-500">
                  {formatTime(push.timestamp)}
                </div>
              </div>
              <svg
                className="w-4 h-4 text-gray-300 shrink-0 dark:text-gray-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5l7 7-7 7"
                />
              </svg>
            </div>
          </div>
        ))}
      </div>

      {/* Pagination */}
      {(page > 0 || hasMore) && (
        <div className="max-w-6xl px-6 pb-12 flex justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-4 py-2 text-sm rounded border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 dark:bg-slate-800 dark:border-slate-600 dark:text-gray-300 dark:hover:bg-gray-700"
          >
            ← Previous
          </button>
          <span className="px-4 py-2 text-sm text-gray-500 dark:text-gray-400">
            Page {page + 1}
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={!hasMore}
            className="px-4 py-2 text-sm rounded border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 dark:bg-slate-800 dark:border-slate-600 dark:text-gray-300 dark:hover:bg-gray-700"
          >
            Next →
          </button>
        </div>
      )}
    </div>
  )
}
