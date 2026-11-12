import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { formatPeriod, pushOutcomes } from '../activity'
import { fetchContributionActivity } from '../api'
import type {
  ActivityGroup,
  ActivityPeriod,
  ActivityWindow,
  ContributionActivity,
  ProposalActivity,
} from '../types'

/** The Decisions tabs a figure links to. */
type DecisionsTab = 'pushes' | 'scm-api-actions'

export type ActivityScope = { kind: 'me' } | { kind: 'user'; username: string } | { kind: 'all' }

/** The windows offered, in days back from now. */
const RANGES = [
  { days: 30, label: 'Last 30 days' },
  { days: 90, label: 'Last 90 days' },
  { days: 365, label: 'Last 12 months' },
]

// Categorical slots 1 and 2, validated against the dashboard's light and dark card surfaces.
const PUSH_FILL = 'bg-[#2a78d6] dark:bg-[#3987e5]'
const PROPOSAL_FILL = 'bg-[#eb6834] dark:bg-[#d95926]'

const SELECT =
  'rounded border border-gray-300 bg-white px-2 py-1 text-sm text-gray-700 dark:border-slate-600 dark:bg-slate-800 dark:text-gray-200'
const CARD =
  'rounded-lg border border-gray-200 bg-white px-4 py-3 shadow-sm dark:border-slate-700 dark:bg-slate-800'

function sum(values: Record<string, number> | undefined): number {
  return Object.values(values ?? {}).reduce((a, b) => a + b, 0)
}

/** Contributions made through the SCM API: opened, edited, commented on, merged. */
function proposalCount(p: ProposalActivity | undefined): number {
  if (!p) return 0
  return p.pullRequests.opened + p.issues.opened + p.edited + p.commented + p.merged
}

/**
 * Outcome hues: status colours, each always shown with its label, never alone. The 600 steps clear 3:1 against both
 * card surfaces.
 */
const TONE = {
  good: 'bg-emerald-600',
  info: 'bg-sky-600',
  neutral: 'bg-slate-500',
  warning: 'bg-amber-600',
  danger: 'bg-rose-600',
} as const

interface Part {
  label: string
  value: number
  tone: keyof typeof TONE
}

/** A headline count split into outcomes that add up to it: a proportion bar, then each outcome with its count. */
function Breakdown({ label, parts }: { label: string; parts: Part[] }) {
  const total = parts.reduce((sum, p) => sum + p.value, 0)
  return (
    <div role="group" aria-label={label} className={CARD}>
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-sm font-semibold text-gray-700 dark:text-gray-200">{label}</span>
        <span className="text-2xl font-semibold tabular-nums text-gray-900 dark:text-gray-100">
          {total.toLocaleString()}
        </span>
      </div>
      <div
        className="mt-2 flex h-1.5 gap-0.5 overflow-hidden rounded-full bg-gray-100 dark:bg-slate-700"
        aria-hidden="true"
      >
        {parts
          .filter((p) => p.value > 0)
          .map((p) => (
            <div
              key={p.label}
              className={TONE[p.tone]}
              style={{ width: `${(p.value / total) * 100}%` }}
            />
          ))}
      </div>
      <dl className="mt-2.5 space-y-1 text-xs">
        {parts.map((p) => (
          <div
            key={p.label}
            className={
              'flex items-center justify-between gap-2 ' +
              (p.value === 0
                ? 'text-gray-400 dark:text-gray-500'
                : 'text-gray-600 dark:text-gray-300')
            }
          >
            <dt className="flex items-center gap-1.5">
              <span className={`inline-block h-2 w-2 rounded-full ${TONE[p.tone]}`} />
              {p.label}
            </dt>
            <dd className="tabular-nums">{p.value.toLocaleString()}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

/** Single figures that need no breakdown, side by side in one strip. */
function Figures({ figures }: { figures: { label: string; value: number }[] }) {
  return (
    <div className={CARD + ' flex flex-wrap gap-x-8 gap-y-3'}>
      {figures.map((f) => (
        <div key={f.label} role="group" aria-label={f.label}>
          <div className="text-xs text-gray-500 dark:text-gray-400">{f.label}</div>
          <div className="text-lg font-semibold tabular-nums text-gray-900 dark:text-gray-100">
            {f.value.toLocaleString()}
          </div>
        </div>
      ))}
    </div>
  )
}

function Totals({ activity }: { activity: ContributionActivity }) {
  const pushes = pushOutcomes(activity.totals.pushes)
  const p = activity.totals.proposals
  const figures: { label: string; value: number }[] = []
  if (activity.contributors !== undefined)
    figures.push({ label: 'Contributors', value: activity.contributors })
  if (p) {
    figures.push(
      { label: 'Edited', value: p.edited },
      { label: 'Commented', value: p.commented },
      { label: 'Requests rejected', value: p.rejected },
    )
  }
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-[repeat(auto-fit,minmax(14rem,1fr))] gap-3">
        <Breakdown
          label="Pushes"
          parts={[
            { label: 'Forwarded', value: pushes.forwarded, tone: 'good' },
            { label: 'Pending', value: pushes.pending, tone: 'warning' },
            { label: 'Failed', value: pushes.failed, tone: 'danger' },
          ]}
        />
        {p && (
          <>
            <Breakdown
              label="Pull requests"
              parts={[
                { label: 'Merged', value: p.pullRequests.merged, tone: 'good' },
                { label: 'Open', value: p.pullRequests.open, tone: 'info' },
                { label: 'Closed', value: p.pullRequests.closed, tone: 'neutral' },
                ...(p.pullRequests.unknown > 0
                  ? [
                      {
                        label: 'State unknown',
                        value: p.pullRequests.unknown,
                        tone: 'neutral' as const,
                      },
                    ]
                  : []),
                { label: 'Refused', value: p.pullRequests.refused, tone: 'danger' },
              ]}
            />
            <Breakdown
              label="Issues"
              parts={[
                { label: 'Open', value: p.issues.open, tone: 'info' },
                { label: 'Closed', value: p.issues.closed, tone: 'neutral' },
                ...(p.issues.unknown > 0
                  ? [{ label: 'State unknown', value: p.issues.unknown, tone: 'neutral' as const }]
                  : []),
                { label: 'Refused', value: p.issues.refused, tone: 'danger' },
              ]}
            />
          </>
        )}
      </div>
      {figures.length > 0 && <Figures figures={figures} />}
    </div>
  )
}

/** Pushes and SCM API contributions per period, stacked, with a tooltip per bar. */
function PeriodChart({
  periods,
  granularity,
  showProposals,
}: {
  periods: ActivityPeriod[]
  granularity: ActivityWindow['granularity']
  showProposals: boolean
}) {
  const [hover, setHover] = useState<number | null>(null)
  const rows = periods.map((p) => ({
    start: p.start,
    pushes: sum(p.pushes),
    proposals: showProposals ? proposalCount(p.proposals) : 0,
  }))
  const max = Math.max(1, ...rows.map((r) => r.pushes + r.proposals))
  const labelEvery = Math.max(1, Math.ceil(rows.length / 6))
  const active = hover === null ? null : rows[hover]

  return (
    <div className={CARD}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">Over time</h3>
        <div className="flex items-center gap-4 text-xs text-gray-600 dark:text-gray-400">
          <span className="flex items-center gap-1.5">
            <span className={`inline-block h-2.5 w-2.5 rounded-sm ${PUSH_FILL}`} /> Pushes
          </span>
          {showProposals && (
            <span className="flex items-center gap-1.5">
              <span className={`inline-block h-2.5 w-2.5 rounded-sm ${PROPOSAL_FILL}`} /> Pull
              requests &amp; issues
            </span>
          )}
        </div>
      </div>
      <div className="relative mt-3">
        <div
          className="flex h-40 items-end gap-0.5 border-b border-gray-200 dark:border-slate-700"
          onMouseLeave={() => setHover(null)}
        >
          {rows.map((r, i) => (
            <div
              key={r.start}
              className="flex h-full flex-1 cursor-default flex-col justify-end"
              onMouseEnter={() => setHover(i)}
              aria-label={`${formatPeriod(r.start, granularity)}: ${r.pushes} pushes${showProposals ? `, ${r.proposals} pull request and issue contributions` : ''}`}
            >
              {r.proposals > 0 && (
                <div
                  className={`${PROPOSAL_FILL} rounded-t ${hover === i ? 'opacity-80' : ''}`}
                  style={{ height: `${(r.proposals / max) * 100}%` }}
                />
              )}
              {r.pushes > 0 && (
                <div
                  className={`${PUSH_FILL} ${r.proposals > 0 ? 'mt-0.5' : 'rounded-t'} ${hover === i ? 'opacity-80' : ''}`}
                  style={{ height: `${(r.pushes / max) * 100}%` }}
                />
              )}
            </div>
          ))}
        </div>
        {active && (
          <div className="pointer-events-none absolute -top-2 right-0 rounded border border-gray-200 bg-white px-2.5 py-1.5 text-xs shadow dark:border-slate-600 dark:bg-slate-900">
            <div className="font-medium text-gray-800 dark:text-gray-200">
              {formatPeriod(active.start, granularity)}
            </div>
            <div className="text-gray-600 dark:text-gray-400">
              {active.pushes.toLocaleString()} pushes
              {showProposals &&
                ` · ${active.proposals.toLocaleString()} pull request & issue contributions`}
            </div>
          </div>
        )}
        <div className="mt-1 flex gap-0.5 text-[10.5px] text-gray-400 dark:text-gray-500">
          {rows.map((r, i) => (
            <div key={r.start} className="flex-1 truncate">
              {i % labelEvery === 0
                ? formatPeriod(r.start, granularity).replace('Week of ', '')
                : ''}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function GroupTable({
  title,
  groups,
  showProposals,
  showContributors,
  name,
  decisions,
}: {
  title: string
  groups: ActivityGroup[]
  showProposals: boolean
  showContributors: boolean
  name: (g: ActivityGroup) => string
  /** The Decisions listing behind a row's figures; absent where the viewer cannot open it. */
  decisions?: (g: ActivityGroup, tab: DecisionsTab, status?: 'REJECTED') => string
}) {
  const th = 'py-1.5 pr-4 font-medium'
  const num = 'py-1.5 pr-4 text-right tabular-nums'
  // A non-zero figure opens the records it counts.
  const figure = (g: ActivityGroup, value: number, tab: DecisionsTab, status?: 'REJECTED') => (
    <td className={num}>
      {decisions && value > 0 ? (
        <Link
          to={decisions(g, tab, status)}
          className="text-blue-600 hover:underline dark:text-blue-400"
        >
          {value}
        </Link>
      ) : (
        value
      )}
    </td>
  )
  return (
    <section aria-label={title} className={CARD}>
      <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">{title}</h3>
      {groups.length === 0 ? (
        <p className="mt-2 text-sm text-gray-400 dark:text-gray-500">No activity in this period.</p>
      ) : (
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-400 dark:text-gray-500">
                <th className={th}>{title === 'By provider' ? 'Provider' : 'Repository'}</th>
                <th className={th + ' text-right'}>Pushes</th>
                <th className={th + ' text-right'}>Forwarded</th>
                {showProposals && (
                  <>
                    <th className={th + ' text-right'}>PRs opened</th>
                    <th className={th + ' text-right'}>Merged</th>
                    <th className={th + ' text-right'}>Issues opened</th>
                    <th className={th + ' text-right'}>Edited</th>
                    <th className={th + ' text-right'}>Commented</th>
                    <th className={th + ' text-right'}>Rejected</th>
                  </>
                )}
                {showContributors && <th className={th + ' text-right'}>Contributors</th>}
              </tr>
            </thead>
            <tbody className="text-gray-700 dark:text-gray-300">
              {groups.map((g) => {
                const p = g.proposals
                return (
                  <tr key={name(g)} className="border-t border-gray-100 dark:border-slate-700">
                    <td className="py-1.5 pr-4 font-mono text-xs">{name(g)}</td>
                    {figure(g, sum(g.pushes), 'pushes')}
                    <td className={num}>{g.pushes.FORWARDED ?? 0}</td>
                    {showProposals && (
                      <>
                        {figure(g, p?.pullRequests.opened ?? 0, 'scm-api-actions')}
                        <td className={num}>{p?.pullRequests.merged ?? 0}</td>
                        {figure(g, p?.issues.opened ?? 0, 'scm-api-actions')}
                        {figure(g, p?.edited ?? 0, 'scm-api-actions')}
                        {figure(g, p?.commented ?? 0, 'scm-api-actions')}
                        {figure(g, p?.rejected ?? 0, 'scm-api-actions', 'REJECTED')}
                      </>
                    )}
                    {showContributors && <td className={num}>{g.contributors ?? 0}</td>}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

/**
 * Contribution activity for one person or for everyone: totals, a period chart, and per-provider and per-repository
 * tables. `filters` are extra query parameters (provider, owner, repo) the caller owns.
 */
export function ContributionActivityView({
  scope,
  filters,
  controls,
  drillDown = false,
}: {
  scope: ActivityScope
  filters?: URLSearchParams
  /** Extra controls rendered beside the range picker. */
  controls?: React.ReactNode
  /** Link figures to the Audit → Decisions records behind them; only for viewers who can open that page. */
  drillDown?: boolean
}) {
  const [days, setDays] = useState(90)
  const [activity, setActivity] = useState<ContributionActivity | null>(null)
  const [error, setError] = useState<string | null>(null)
  const scopeKey = scope.kind === 'user' ? `user:${scope.username}` : scope.kind
  const filterKey = filters?.toString() ?? ''

  useEffect(() => {
    const params = new URLSearchParams(filterKey)
    params.set('from', new Date(Date.now() - days * 86_400_000).toISOString())
    let live = true
    fetchContributionActivity(scope, params)
      .then((a) => {
        if (!live) return
        setActivity(a)
        setError(null)
      })
      .catch((e: Error) => live && setError(e.message))
    return () => {
      live = false
    }
    // scope is identified by scopeKey; the object itself is recreated on every render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey, filterKey, days])

  const showProposals = activity?.proposalsAvailable ?? false
  const decisions =
    drillDown && activity
      ? (g: ActivityGroup, tab: DecisionsTab, status?: 'REJECTED') => {
          const params = new URLSearchParams({
            tab,
            from: activity.window.from,
            to: activity.window.to,
            provider: g.provider,
          })
          if (g.owner) params.set('owner', g.owner)
          if (g.repo) params.set('repo', g.repo)
          if (scope.kind === 'user') params.set('user', scope.username)
          if (status) params.set('status', status)
          return `/audit/decisions?${params}`
        }
      : undefined
  const aggregate = scope.kind === 'all'

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block text-xs font-medium text-gray-500 dark:text-gray-400">
          Period
          <select
            className={SELECT + ' mt-1 block'}
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
          >
            {RANGES.map((r) => (
              <option key={r.days} value={r.days}>
                {r.label}
              </option>
            ))}
          </select>
        </label>
        {controls}
      </div>
      <p className="text-xs text-gray-500 dark:text-gray-400">
        Counts what passed through fogwall. A pull request merged or closed on the provider directly
        shows as fogwall last saw it.
      </p>
      {error && (
        <div
          role="alert"
          className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300"
        >
          {error}
        </div>
      )}
      {!activity && !error && <p className="text-sm text-gray-400 dark:text-gray-500">Loading…</p>}
      {activity && (
        <>
          <Totals activity={activity} />
          <PeriodChart
            periods={activity.periods}
            granularity={activity.window.granularity}
            showProposals={showProposals}
          />
          {aggregate && (
            <GroupTable
              title="By provider"
              groups={activity.providers}
              showProposals={showProposals}
              showContributors
              name={(g) => g.provider}
              decisions={decisions}
            />
          )}
          <GroupTable
            title="By repository"
            groups={activity.repos}
            showProposals={showProposals}
            showContributors={aggregate}
            name={(g) => `${g.provider}/${g.owner}/${g.repo}`}
            decisions={decisions}
          />
        </>
      )}
    </div>
  )
}
