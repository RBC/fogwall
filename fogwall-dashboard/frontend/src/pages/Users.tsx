import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createUser, fetchUsers } from '../api'
import type { PushStatus, UserSummary } from '../types'

interface UsersProps {
  authProvider: string
}

const PUSH_STAT_CONFIG: {
  status: PushStatus
  label: string
  icon: string
  classes: string
}[] = [
  {
    status: 'FORWARDED',
    label: 'Forwarded',
    icon: '✓',
    classes: 'bg-blue-50 text-blue-700 border-blue-200',
  },
  {
    status: 'APPROVED',
    label: 'Approved',
    icon: '✓',
    classes: 'bg-green-50 text-green-700 border-green-200',
  },
  {
    status: 'PENDING',
    label: 'Pending',
    icon: '⊘',
    classes: 'bg-amber-50 text-amber-700 border-amber-200',
  },
  {
    status: 'REJECTED',
    label: 'Rejected',
    icon: '✗',
    classes: 'bg-red-50 text-red-700 border-red-200',
  },
]

function PushStatChip({
  count,
  icon,
  label,
  classes,
}: {
  count: number
  icon: string
  label: string
  classes: string
}) {
  if (count === 0) return null
  return (
    <span
      className={`inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-xs font-medium ${classes}`}
      title={`${count} ${label}`}
    >
      <span>{icon}</span>
      <span>{count}</span>
    </span>
  )
}

function AddUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [isAdmin, setIsAdmin] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const roles = isAdmin ? ['USER', 'ADMIN'] : ['USER']
      await createUser(username.trim(), password, email.trim() || undefined, roles)
      onCreated()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create user')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-base font-semibold text-gray-800 mb-4">Add User</h3>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Username</label>
            <input
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Password</label>
            <input
              required
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">
              Email <span className="text-gray-400">(optional)</span>
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input
              type="checkbox"
              checked={isAdmin}
              onChange={(e) => setIsAdmin(e.target.checked)}
              className="rounded"
            />
            Grant admin role
          </label>
          {error && <p className="text-xs text-red-500">{error}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded border border-gray-200 text-xs text-gray-600 hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-3 py-1.5 rounded bg-slate-700 text-white text-xs hover:bg-slate-800 disabled:opacity-50"
            >
              {submitting ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function Users({ authProvider }: UsersProps) {
  const navigate = useNavigate()
  const [users, setUsers] = useState<UserSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [showAddModal, setShowAddModal] = useState(false)

  const isLocalAuth = authProvider === 'local'

  function loadUsers() {
    fetchUsers()
      .then(setUsers)
      .catch(() => setError('Failed to load users'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadUsers()
  }, [])

  const filtered = users.filter(
    (u) =>
      u.username.toLowerCase().includes(search.toLowerCase()) ||
      (u.primaryEmail ?? '').toLowerCase().includes(search.toLowerCase()),
  )

  if (loading)
    return <div className="max-w-5xl mx-auto px-4 py-16 text-center text-gray-400">Loading…</div>
  if (error)
    return <div className="max-w-5xl mx-auto px-4 py-16 text-center text-red-500">{error}</div>

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-4">
      {showAddModal && (
        <AddUserModal onClose={() => setShowAddModal(false)} onCreated={loadUsers} />
      )}
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Users</h2>
        {isLocalAuth && (
          <button
            onClick={() => setShowAddModal(true)}
            className="px-3 py-1.5 rounded bg-slate-700 text-white text-sm hover:bg-slate-800"
          >
            + Add User
          </button>
        )}
      </div>

      <input
        type="text"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search by username or email…"
        className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
      />

      {filtered.length === 0 ? (
        <p className="text-sm text-gray-400 italic py-8 text-center">No users found.</p>
      ) : (
        <div className="rounded-lg border border-gray-200 bg-white overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                <th className="px-4 py-3">Username</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">SCM Identities</th>
                <th className="px-4 py-3">Push Activity</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map((u) => (
                <tr
                  key={u.username}
                  className="hover:bg-gray-50 cursor-pointer transition-colors"
                  onClick={() => navigate(`/users/${encodeURIComponent(u.username)}`)}
                >
                  <td className="px-4 py-3 font-medium text-gray-800">{u.username}</td>
                  <td className="px-4 py-3 text-gray-500">
                    {u.primaryEmail ?? <span className="italic text-gray-300">none</span>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {u.scmProviders.length === 0 ? (
                        <span className="italic text-gray-300 text-xs">none</span>
                      ) : (
                        u.scmProviders.map((p) => (
                          <span
                            key={p}
                            className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600"
                          >
                            {p}
                          </span>
                        ))
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1">
                      {PUSH_STAT_CONFIG.map(({ status, icon, label, classes }) => (
                        <PushStatChip
                          key={status}
                          count={u.pushCounts[status] ?? 0}
                          icon={icon}
                          label={label}
                          classes={classes}
                        />
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <span className="text-gray-400 text-xs">›</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
