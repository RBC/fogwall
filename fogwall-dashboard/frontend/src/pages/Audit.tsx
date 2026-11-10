import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { fetchProviders, fetchRecordTypes } from '../api'
import { ContributionActivityView } from '../components/ContributionActivityView'
import type { RecordType } from '../types'
import { ColumnPicker, FormatPicker } from './audit/ExportOptions'
import {
  columnsParam,
  download,
  type ExportFormat,
  filterParams,
  type Values,
} from './audit/params'
import { ErrorBanner, RecordListing, TimeWindow } from './audit/RecordListing'
import {
  FETCH_ACTIVITY,
  GRANTS,
  GROUP_MEMBERS,
  type ListedType,
  MEMBERSHIP_CHANGES,
  PERMISSION_CHANGES,
  PUSHES,
  ROLE_CHANGES,
  SCM_API_ACTIONS,
  typeLabel,
} from './audit/recordTypes'
import { INPUT, PRIMARY_BUTTON } from './audit/styles'
import { useSuggestions } from './audit/useSuggestions'

/** Every record type with its export columns, from the server, so the page never carries its own copy. */
function useRecordTypes(): { recordTypes: RecordType[]; error: string | null } {
  const [recordTypes, setRecordTypes] = useState<RecordType[]>([])
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    fetchRecordTypes()
      .then(setRecordTypes)
      .catch((e: Error) => setError(e.message))
  }, [])
  return { recordTypes, error }
}

function Heading({ title, description }: { title: string; description: string }) {
  return (
    <>
      <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">{title}</h1>
      <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{description}</p>
    </>
  )
}

/**
 * An instant as the value a datetime-local input shows, in the browser's time zone. The input holds whole minutes, so
 * an exclusive end is rounded up to keep the records of its last minute inside the window.
 */
function localInput(iso: string, roundUp: boolean): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  if (roundUp && d.getTime() % 60_000 !== 0) d.setTime(Math.ceil(d.getTime() / 60_000) * 60_000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Filters a link into the page carries in its query, beside the tab: `from` and `to` as ISO-8601 instants. */
function linkedFilters(searchParams: URLSearchParams): Values {
  const values: Values = {}
  for (const [name, value] of searchParams) {
    if (name === 'tab' || !value) continue
    values[name] = name === 'from' || name === 'to' ? localInput(value, name === 'to') : value
  }
  return values
}

/** One Audit page: a tab per record type, the active one kept in the URL. */
function AuditTabs({
  title,
  description,
  types,
}: {
  title: string
  description: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  types: ListedType<any>[]
}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const { recordTypes, error } = useRecordTypes()
  const suggestions = useSuggestions()
  const active = types.find((t) => t.name === searchParams.get('tab')) ?? types[0]
  const columns = recordTypes.find((t) => t.name === active.name)?.columns ?? []
  const initial = linkedFilters(searchParams)

  return (
    <div className="max-w-7xl px-6 pt-6 pb-12">
      <Heading title={title} description={description} />
      <div className="mt-4 border-b border-gray-200 dark:border-slate-700">
        <div role="tablist" aria-label={title} className="-mb-px flex flex-wrap gap-x-6">
          {types.map((t) => (
            <button
              key={t.name}
              role="tab"
              aria-selected={t.name === active.name}
              onClick={() => setSearchParams({ tab: t.name })}
              className={
                'border-b-2 py-2 text-sm font-medium transition-colors ' +
                (t.name === active.name
                  ? 'border-blue-600 text-blue-700 dark:border-blue-400 dark:text-blue-300'
                  : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200')
              }
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      <div role="tabpanel" className="space-y-4 pt-5">
        <ErrorBanner message={error} />
        <RecordListing
          key={`${active.name}?${searchParams}`}
          type={active}
          columns={columns}
          suggestions={suggestions}
          initial={initial}
        />
      </div>
    </div>
  )
}

export function AuditDecisions() {
  return (
    <AuditTabs
      title="Decisions"
      description="Pushes, SCM API actions and fetches, and what fogwall decided for each."
      types={[PUSHES, SCM_API_ACTIONS, FETCH_ACTIVITY]}
    />
  )
}

export function AuditAccessChanges() {
  return (
    <AuditTabs
      title="Access changes"
      description="Every grant, revocation, group membership change and role change: who made it, whose access, and from where."
      types={[PERMISSION_CHANGES, MEMBERSHIP_CHANGES, ROLE_CHANGES]}
    />
  )
}

export function AuditCurrentAccess() {
  return (
    <AuditTabs
      title="Current access"
      description="Who holds which grant right now, directly or through a group, and who is in each group."
      types={[GRANTS, GROUP_MEMBERS]}
    />
  )
}

/** Several record types in one zip, each bounded by the same time window. */
export function AuditExport() {
  const { recordTypes, error } = useRecordTypes()
  const [types, setTypes] = useState<string[] | null>(null)
  const [timeWindow, setTimeWindow] = useState<Values>({})
  const [format, setFormat] = useState<ExportFormat>('csv')
  const [columns, setColumns] = useState<Record<string, string[]>>({})

  const selected = types ?? recordTypes.map((t) => t.name)
  const chosenColumns = (t: RecordType) => columns[t.name] ?? t.columns
  const missingColumns =
    format === 'csv' &&
    recordTypes.some((t) => selected.includes(t.name) && chosenColumns(t).length === 0)

  function toggle(name: string) {
    setTypes(selected.includes(name) ? selected.filter((n) => n !== name) : [...selected, name])
  }

  function start() {
    const params = filterParams(timeWindow)
    params.set(
      'types',
      recordTypes
        .filter((t) => selected.includes(t.name))
        .map((t) => t.name)
        .join(','),
    )
    params.set('format', format)
    for (const t of recordTypes) {
      if (!selected.includes(t.name)) continue
      const cols = columnsParam({ format, columns: chosenColumns(t) }, t.columns)
      if (cols) params.set(`${t.name}.columns`, cols)
    }
    download('/api/audit/export', params)
  }

  return (
    <div className="max-w-4xl space-y-5 px-6 pt-6 pb-12">
      <Heading
        title="Export"
        description="Several record types in one zip, bounded by one time window, with a manifest of each part's rows and SHA-256."
      />
      <ErrorBanner message={error} />
      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-gray-500 dark:text-gray-400">
          Record types
        </legend>
        <div className="flex flex-wrap gap-x-5 gap-y-1.5">
          {recordTypes.map((t) => (
            <label
              key={t.name}
              className="flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-300"
            >
              <input
                type="checkbox"
                checked={selected.includes(t.name)}
                onChange={() => toggle(t.name)}
              />
              {typeLabel(t.name)}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="grid max-w-xl grid-cols-2 gap-3">
        <TimeWindow
          values={timeWindow}
          onChange={(name, value) => setTimeWindow((w) => ({ ...w, [name]: value }))}
        />
      </div>
      <p className="text-xs text-gray-500 dark:text-gray-400">
        Grants and group membership are written as they are now, whatever the window.
      </p>
      <FormatPicker value={format} onChange={setFormat} />
      {format === 'csv' && (
        <div className="space-y-2">
          {recordTypes
            .filter((t) => selected.includes(t.name))
            .map((t) => (
              <details
                key={t.name}
                className="rounded border border-gray-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-800"
              >
                <summary className="cursor-pointer text-sm text-gray-700 dark:text-gray-300">
                  {typeLabel(t.name)} columns ({chosenColumns(t).length} of {t.columns.length})
                </summary>
                <div className="pt-2">
                  <ColumnPicker
                    label="CSV columns"
                    all={t.columns}
                    chosen={chosenColumns(t)}
                    onChange={(cols) => setColumns((c) => ({ ...c, [t.name]: cols }))}
                  />
                </div>
              </details>
            ))}
        </div>
      )}
      <button
        type="button"
        onClick={start}
        disabled={selected.length === 0 || missingColumns}
        className={PRIMARY_BUTTON}
      >
        Download
      </button>
    </div>
  )
}

interface ActiveRepoRow {
  provider: string
  owner: string
  repoName: string
}

/** Contribution activity across everyone, or one person, narrowed by provider and repository. */
export function AuditParticipation() {
  const suggestions = useSuggestions()
  const [searchParams, setSearchParams] = useSearchParams()
  const [repos, setRepos] = useState<ActiveRepoRow[]>([])
  const [providers, setProviders] = useState<{ id: string; name: string }[]>([])
  const provider = searchParams.get('provider') ?? ''
  const repo = searchParams.get('repo') ?? ''
  const user = searchParams.get('user') ?? ''

  useEffect(() => {
    fetch('/api/repos/active')
      .then((r) => (r.ok ? r.json() : []))
      .then(setRepos)
      .catch(() => setRepos([]))
    fetchProviders()
      .then(setProviders)
      .catch(() => setProviders([]))
  }, [])

  function set(name: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(name, value)
    else next.delete(name)
    if (name === 'provider') next.delete('repo')
    setSearchParams(next)
  }

  const filters = new URLSearchParams()
  if (provider) filters.set('provider', provider)
  if (repo) {
    const [repoProvider, owner, name] = repo.split('\n')
    filters.set('provider', repoProvider)
    filters.set('owner', owner)
    filters.set('repo', name)
  }
  const repoOptions = repos
    .filter((r) => !provider || r.provider === provider)
    .map((r) => ({
      value: `${r.provider}\n${r.owner}\n${r.repoName}`,
      label: `${r.provider}/${r.owner}/${r.repoName}`,
    }))
    .sort((a, b) => a.label.localeCompare(b.label))
  const select = INPUT + ' mt-1 block'

  return (
    <div className="max-w-7xl space-y-5 px-6 pt-6 pb-12">
      <Heading
        title="Participation"
        description="Who contributed through fogwall, where, and whether it landed: pushes, and pull requests and issues opened, edited, commented on and merged."
      />
      <ContributionActivityView
        scope={user ? { kind: 'user', username: user } : { kind: 'all' }}
        filters={filters}
        drillDown
        controls={
          <>
            <label className="block text-xs font-medium text-gray-500 dark:text-gray-400">
              Person
              <select className={select} value={user} onChange={(e) => set('user', e.target.value)}>
                <option value="">Everyone</option>
                {suggestions.users.map((u) => (
                  <option key={u} value={u}>
                    {u}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-xs font-medium text-gray-500 dark:text-gray-400">
              Provider
              <select
                className={select}
                value={provider}
                onChange={(e) => set('provider', e.target.value)}
              >
                <option value="">All</option>
                {providers.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-xs font-medium text-gray-500 dark:text-gray-400">
              Repository
              <select className={select} value={repo} onChange={(e) => set('repo', e.target.value)}>
                <option value="">All</option>
                {repoOptions.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
            </label>
          </>
        }
      />
    </div>
  )
}
