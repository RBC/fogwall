import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { StatusBadge } from '../../components/StatusBadge'
import { Action, Muted, Origin, Roles } from './cells'
import type { SuggestionSource } from './useSuggestions'
import type {
  ChangeOrigin,
  FetchActivity,
  GrantHolding,
  GroupMembership,
  MembershipChangeEvent,
  PermissionChangeEvent,
  PushRecord,
  RoleChangeEvent,
  ScmApiActionRecord,
} from '../../types'

/**
 * One listing filter: one of a fixed set of values, or text. Text may suggest values that exist now; a strict filter
 * offers only those, which suits current state but not history, where a deleted user or group must stay findable.
 */
export interface FilterDef {
  name: string
  label: string
  options?: string[]
  suggest?: SuggestionSource
  strict?: boolean
  placeholder?: string
}

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
}

/** A record type the Audit pages list: where it is read and exported, how it is filtered, and how a row is shown. */
export interface ListedType<T> {
  /** The record type's name, as the export and record-types endpoint name it. */
  name: string
  label: string
  /** The listing; its export is at this path plus /export. */
  path: string
  /** History pages by cursor in time order, with a time window; current state pages by offset and has neither. */
  history: boolean
  /** Filters this type answers, besides the time window. */
  filters: FilterDef[]
  /** Shown under the filters when a filter needs explaining. */
  hint?: string
  columns: Column<T>[]
  rowKey: (row: T) => string
}

const ORIGINS: ChangeOrigin[] = ['DASHBOARD', 'API', 'CONFIG', 'IDP']

const PROVIDER: FilterDef = { name: 'provider', label: 'Provider', suggest: 'providers' }
const OWNER: FilterDef = { name: 'owner', label: 'Owner' }
const REPO: FilterDef = { name: 'repo', label: 'Repo' }
const SEARCH: FilterDef = { name: 'search', label: 'Search' }
const ACTOR: FilterDef = { name: 'user', label: 'Changed by', suggest: 'users' }
const ORIGIN: FilterDef = { name: 'origin', label: 'Origin', options: ORIGINS }

const PATTERN_HINT =
  'Owner and repo are given together, and select the grants whose pattern applies to that repository.'

export function formatTime(ts: string | number | undefined | null): string {
  if (ts === undefined || ts === null || ts === '') return ''
  const date = new Date(ts)
  return Number.isNaN(date.getTime()) ? String(ts) : date.toLocaleString()
}

function repoOf(owner?: string, name?: string): string {
  return owner && name ? `${owner}/${name}` : (owner ?? name ?? '—')
}

function pattern(r: { provider: string; value: string; matchType: string; target: string }) {
  return (
    <>
      <Muted>{r.provider} · </Muted>
      <span className="font-mono text-xs">{r.value}</span>{' '}
      <Muted>
        {r.matchType.toLowerCase()} {r.target.toLowerCase()}
      </Muted>
    </>
  )
}

export const PUSHES: ListedType<PushRecord> = {
  name: 'pushes',
  label: 'Pushes',
  path: '/api/push',
  history: true,
  filters: [
    {
      name: 'status',
      label: 'Status',
      options: ['PENDING', 'APPROVED', 'REJECTED', 'FORWARDED', 'RECEIVED', 'CANCELED', 'ERROR'],
    },
    PROVIDER,
    OWNER,
    REPO,
    { name: 'user', label: 'Pusher', suggest: 'users' },
    SEARCH,
  ],
  rowKey: (p) => p.id,
  columns: [
    { header: 'Time', cell: (p) => formatTime(p.timestamp) },
    { header: 'Status', cell: (p) => <StatusBadge status={p.status} /> },
    {
      header: 'Repository',
      cell: (p) => (
        <Link to={`/push/${p.id}`} className="text-blue-600 hover:underline dark:text-blue-400">
          {repoOf(p.project, p.repoName)}
        </Link>
      ),
    },
    { header: 'Branch', cell: (p) => <span className="font-mono text-xs">{p.branch ?? '—'}</span> },
    { header: 'Pusher', cell: (p) => p.resolvedUser ?? '—' },
  ],
}

export const SCM_API_ACTIONS: ListedType<ScmApiActionRecord> = {
  name: 'scm-api-actions',
  label: 'SCM API actions',
  path: '/api/scm-api-actions',
  history: true,
  filters: [
    { name: 'status', label: 'Status', options: ['FORWARDED', 'DENIED', 'REJECTED', 'ERROR'] },
    { name: 'origin', label: 'Surface', options: ['DASHBOARD', 'SCM_API'] },
    PROVIDER,
    OWNER,
    REPO,
    { name: 'user', label: 'Caller', suggest: 'users' },
    SEARCH,
  ],
  rowKey: (a) => a.id,
  columns: [
    { header: 'Time', cell: (a) => formatTime(a.timestamp) },
    { header: 'Status', cell: (a) => <StatusBadge status={a.status} /> },
    {
      header: 'Operation',
      cell: (a) => (
        <span className="font-mono text-xs">
          {a.provider ?? '—'} · {a.mutationField ?? '—'}
        </span>
      ),
    },
    { header: 'Repository', cell: (a) => repoOf(a.repoOwner, a.repoName) },
    { header: 'Caller', cell: (a) => a.resolvedUser ?? a.scmUsername ?? '—' },
  ],
}

export const FETCH_ACTIVITY: ListedType<FetchActivity> = {
  name: 'fetch-activity',
  label: 'Fetch activity',
  path: '/api/fetches',
  history: true,
  filters: [
    { name: 'result', label: 'Result', options: ['ALLOWED', 'BLOCKED'] },
    { name: 'transport', label: 'Transport', options: ['HTTP', 'SSH'] },
    PROVIDER,
    OWNER,
    REPO,
    SEARCH,
  ],
  rowKey: (f) => f.id,
  columns: [
    { header: 'Hour', cell: (f) => formatTime(f.bucketStart) },
    { header: 'Result', cell: (f) => <StatusBadge status={f.result} /> },
    {
      header: 'Repository',
      cell: (f) => (
        <>
          {f.provider && <Muted>{f.provider} · </Muted>}
          {repoOf(f.owner, f.repoName)}
        </>
      ),
    },
    { header: 'Transport', cell: (f) => `${f.transport} · ${f.mode.toLowerCase()}` },
    { header: 'Fetches', cell: (f) => f.fetchCount },
    { header: 'Refusal', cell: (f) => f.refusal ?? <Muted>—</Muted> },
  ],
}

export const PERMISSION_CHANGES: ListedType<PermissionChangeEvent> = {
  name: 'permission-changes',
  label: 'Permission changes',
  path: '/api/audit/permission-changes',
  history: true,
  filters: [
    { name: 'action', label: 'Action', options: ['GRANTED', 'REVOKED'] },
    ORIGIN,
    { name: 'subjectType', label: 'Held by', options: ['USER', 'GROUP'] },
    { name: 'subject', label: 'Whose access', suggest: 'subjects' },
    ACTOR,
    PROVIDER,
    OWNER,
    REPO,
    SEARCH,
  ],
  hint: PATTERN_HINT,
  rowKey: (e) => e.id,
  columns: [
    { header: 'Time', cell: (e) => formatTime(e.occurredAt) },
    { header: 'Action', cell: (e) => <Action action={e.action} /> },
    {
      header: 'Whose access',
      cell: (e) => (
        <>
          {e.subject} <Muted>{e.subjectType.toLowerCase()}</Muted>
        </>
      ),
    },
    { header: 'Grant', cell: (e) => <span className="font-mono text-xs">{e.grant}</span> },
    { header: 'Repositories', cell: pattern },
    { header: 'Changed by', cell: (e) => e.actor ?? <Muted>—</Muted> },
    { header: 'Origin', cell: (e) => <Origin origin={e.origin} revision={e.sourceRevision} /> },
  ],
}

export const MEMBERSHIP_CHANGES: ListedType<MembershipChangeEvent> = {
  name: 'membership-changes',
  label: 'Membership changes',
  path: '/api/audit/membership-changes',
  history: true,
  filters: [
    { name: 'action', label: 'Action', options: ['ADDED', 'REMOVED'] },
    ORIGIN,
    { name: 'group', label: 'Group', suggest: 'groups' },
    { name: 'subject', label: 'Member', suggest: 'users' },
    ACTOR,
    SEARCH,
  ],
  rowKey: (e) => e.id,
  columns: [
    { header: 'Time', cell: (e) => formatTime(e.occurredAt) },
    { header: 'Action', cell: (e) => <Action action={e.action} /> },
    { header: 'Group', cell: (e) => e.groupName },
    { header: 'Member', cell: (e) => e.member },
    { header: 'Changed by', cell: (e) => e.actor ?? <Muted>—</Muted> },
    { header: 'Origin', cell: (e) => <Origin origin={e.origin} revision={e.sourceRevision} /> },
  ],
}

export const ROLE_CHANGES: ListedType<RoleChangeEvent> = {
  name: 'role-changes',
  label: 'Role changes',
  path: '/api/audit/role-changes',
  history: true,
  filters: [ORIGIN, { name: 'subject', label: 'User', suggest: 'users' }, ACTOR, SEARCH],
  rowKey: (e) => e.id,
  columns: [
    { header: 'Time', cell: (e) => formatTime(e.occurredAt) },
    { header: 'User', cell: (e) => e.username },
    { header: 'Before', cell: (e) => <Roles roles={e.previousRoles} /> },
    { header: 'After', cell: (e) => <Roles roles={e.roles} /> },
    { header: 'Changed by', cell: (e) => e.actor },
    { header: 'Origin', cell: (e) => <Origin origin={e.origin} /> },
  ],
}

export const GRANTS: ListedType<GrantHolding> = {
  name: 'grants',
  label: 'Grants',
  path: '/api/audit/grants',
  history: false,
  filters: [
    { name: 'subject', label: 'Holder', suggest: 'users', strict: true },
    { name: 'via', label: 'Via', options: ['DIRECT', 'GROUP'] },
    { name: 'group', label: 'Group', suggest: 'groups', strict: true },
    { ...PROVIDER, strict: true },
    // A granter may be the API key, or a user since deleted: suggested, not limited to.
    { name: 'user', label: 'Granted by', suggest: 'users' },
    OWNER,
    REPO,
  ],
  hint: PATTERN_HINT,
  rowKey: (h) => `${h.permissionId}:${h.holder}`,
  columns: [
    { header: 'Holder', cell: (h) => h.holder },
    {
      header: 'Via',
      cell: (h) => (h.via === 'GROUP' ? <>group {h.group}</> : <Muted>direct</Muted>),
    },
    { header: 'Grant', cell: (h) => <span className="font-mono text-xs">{h.grant}</span> },
    { header: 'Repositories', cell: pattern },
    {
      header: 'Granted',
      cell: (h) =>
        h.grantedBy || h.grantedAt ? (
          <>
            {h.grantedBy ?? <Muted>{h.source.toLowerCase()}</Muted>}{' '}
            <Muted>{formatTime(h.grantedAt)}</Muted>
          </>
        ) : (
          <Muted>{h.source.toLowerCase()}</Muted>
        ),
    },
    {
      header: 'In effect',
      cell: (h) => (h.effective ? 'yes' : <Muted>no — the holder’s roles cannot act</Muted>),
    },
  ],
}

export const GROUP_MEMBERS: ListedType<GroupMembership> = {
  name: 'group-members',
  label: 'Group membership',
  path: '/api/audit/group-members',
  history: false,
  filters: [
    { name: 'group', label: 'Group', suggest: 'groups', strict: true },
    { name: 'subject', label: 'Member', suggest: 'users', strict: true },
  ],
  rowKey: (m) => `${m.groupId}:${m.member}`,
  columns: [
    { header: 'Group', cell: (m) => m.group },
    { header: 'Member', cell: (m) => m.member },
    { header: 'Defined in', cell: (m) => <Muted>{m.groupSource.toLowerCase()}</Muted> },
  ],
}

/** Human label for a record type name. */
export function typeLabel(name: string): string {
  const all = [
    PUSHES,
    SCM_API_ACTIONS,
    FETCH_ACTIVITY,
    PERMISSION_CHANGES,
    MEMBERSHIP_CHANGES,
    ROLE_CHANGES,
    GRANTS,
    GROUP_MEMBERS,
  ] as { name: string; label: string }[]
  return all.find((t) => t.name === name)?.label ?? name
}
