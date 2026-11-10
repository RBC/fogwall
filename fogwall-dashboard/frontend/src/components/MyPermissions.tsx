import { useEffect, useState } from 'react'
import { fetchMyPermissions } from '../api'
import type { UserGroupView } from '../api'
import type { RepoPermission } from '../types'
import { OperationsBadge, PathTypeBadge } from './PermissionBadges'
import { LockedBadge } from './SourceBadges'

/** The repository grants the signed-in user holds, directly and through groups. */
export function MyPermissions() {
  const [permissions, setPermissions] = useState<RepoPermission[]>([])
  const [groups, setGroups] = useState<UserGroupView[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchMyPermissions()
      .then((data) => {
        setPermissions(data.direct)
        setGroups(data.groups)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="space-y-4">
      <p className="text-sm text-gray-500 dark:text-gray-400">
        Repository access permissions granted to your account.
      </p>
      {loading ? (
        <p className="text-sm text-gray-400 dark:text-gray-500">Loading…</p>
      ) : permissions.length === 0 ? (
        <p className="text-sm text-gray-400 italic dark:text-gray-500">
          No permissions configured.
        </p>
      ) : (
        <div className="rounded-lg border border-gray-200 bg-white overflow-hidden dark:bg-slate-800 dark:border-slate-700">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide dark:bg-slate-700/50 dark:border-slate-700 dark:text-gray-400">
                <th className="px-4 py-3">Provider</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Path</th>
                <th className="px-4 py-3">Grant</th>
                <th className="px-4 py-3">Source</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {permissions.map((p) => (
                <tr
                  key={p.id}
                  className="hover:bg-gray-50 transition-colors dark:hover:bg-gray-700/50"
                >
                  <td className="px-4 py-3">
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                      {p.provider}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <PathTypeBadge matchType={p.matchType} />
                  </td>
                  <td className="px-4 py-3 font-mono text-gray-700 text-xs dark:text-gray-300">
                    {p.value}
                  </td>
                  <td className="px-4 py-3">
                    <OperationsBadge operations={p.grant} />
                  </td>
                  <td className="px-4 py-3">
                    {p.source === 'CONFIG' ? (
                      <LockedBadge source="config" />
                    ) : (
                      <span className="text-xs text-gray-400 dark:text-gray-500">local</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {groups.length > 0 && (
        <div className="space-y-3 mt-6">
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
            Group memberships
          </h3>
          {groups.map((g) => (
            <div
              key={g.id}
              className="rounded-lg border border-gray-200 bg-white dark:bg-slate-800 dark:border-slate-700 overflow-hidden"
            >
              <div className="px-4 py-3 flex items-center gap-2 border-b border-gray-100 dark:border-slate-700">
                <span className="text-sm font-medium text-gray-800 dark:text-gray-100">
                  {g.name}
                </span>
                <span className="text-xs bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 rounded px-1.5 py-0.5">
                  {g.source.toLowerCase()}
                </span>
                {g.description && (
                  <span className="text-xs text-gray-500 dark:text-gray-400">
                    — {g.description}
                  </span>
                )}
              </div>
              {g.rules.length === 0 ? (
                <p className="px-4 py-3 text-xs text-gray-400 italic">No rules in this group.</p>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-slate-700/50">
                      <th className="px-4 py-2">Provider</th>
                      <th className="px-4 py-2">Type</th>
                      <th className="px-4 py-2">Path</th>
                      <th className="px-4 py-2">Grant</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-slate-700">
                    {g.rules.map((r) => (
                      <tr key={r.id} className="text-gray-700 dark:text-gray-300">
                        <td className="px-4 py-2">
                          <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                            {r.provider}
                          </span>
                        </td>
                        <td className="px-4 py-2">
                          <PathTypeBadge matchType={r.matchType} />
                        </td>
                        <td className="px-4 py-2 font-mono text-xs">{r.value}</td>
                        <td className="px-4 py-2">
                          <OperationsBadge operations={r.grant} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
