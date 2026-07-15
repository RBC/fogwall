import { useEffect, useState } from 'react'
import {
  addGroupMember,
  addGroupPermission,
  createGroup,
  deleteGroup,
  deleteGroupPermission,
  fetchGroup,
  fetchGroups,
  fetchProviders,
  fetchUsers,
  removeGroupMember,
  updateGroup,
} from '../api'
import type {
  GroupDetail,
  GroupPermissionRule,
  GroupSummary,
  Provider,
  UserSummary,
} from '../types'

export function Groups() {
  const [groups, setGroups] = useState<GroupSummary[]>([])
  const [selected, setSelected] = useState<GroupDetail | null>(null)
  const [providers, setProviders] = useState<Provider[]>([])
  const [users, setUsers] = useState<UserSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // create group form
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)

  // edit group form
  const [editName, setEditName] = useState('')
  const [editDesc, setEditDesc] = useState('')
  const [editError, setEditError] = useState<string | null>(null)
  const [editSaving, setEditSaving] = useState(false)

  // add member form
  const [newMember, setNewMember] = useState('')
  const [memberError, setMemberError] = useState<string | null>(null)

  // add rule form
  const [ruleProvider, setRuleProvider] = useState('')
  const [ruleValue, setRuleValue] = useState('')
  const [ruleMatchType, setRuleMatchType] = useState('GLOB')
  const [ruleGrant, setRuleGrant] = useState('PUSH')
  const [ruleError, setRuleError] = useState<string | null>(null)

  const refreshGroups = () =>
    fetchGroups()
      .then((data: GroupSummary[]) => setGroups(data))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load groups'))
      .finally(() => setLoading(false))

  const loadDetail = (id: string) =>
    fetchGroup(id)
      .then((data: GroupDetail) => {
        setSelected(data)
        setEditName(data.name)
        setEditDesc(data.description ?? '')
        setEditError(null)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load group'))

  useEffect(() => {
    fetchGroups()
      .then((data: GroupSummary[]) => setGroups(data))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load groups'))
      .finally(() => setLoading(false))
    fetchProviders()
      .then((data: Provider[]) => {
        setProviders(data)
        if (data.length > 0) setRuleProvider(data[0].id)
      })
      .catch(() => {})
    fetchUsers()
      .then((data: UserSummary[]) => setUsers(data))
      .catch(() => {})
  }, [])

  const handleSaveEdit = async () => {
    if (!selected || !editName.trim()) {
      setEditError('Name is required')
      return
    }
    setEditError(null)
    setEditSaving(true)
    try {
      const updated = await updateGroup(selected.id, {
        name: editName.trim(),
        description: editDesc.trim() || undefined,
      })
      setSelected(updated)
      await refreshGroups()
    } catch (e) {
      setEditError(e instanceof Error ? e.message : 'Failed to update group')
    } finally {
      setEditSaving(false)
    }
  }

  const handleCreate = async () => {
    if (!newName.trim()) {
      setCreateError('Name is required')
      return
    }
    setCreateError(null)
    try {
      await createGroup({ name: newName.trim(), description: newDesc.trim() || undefined })
      setNewName('')
      setNewDesc('')
      await refreshGroups()
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : 'Failed to create group')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteGroup(id)
      if (selected?.id === id) setSelected(null)
      await refreshGroups()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete group')
    }
  }

  const handleAddMember = async () => {
    if (!selected || !newMember) {
      setMemberError('Select a user')
      return
    }
    setMemberError(null)
    try {
      await addGroupMember(selected.id, newMember.trim())
      setNewMember('')
      await loadDetail(selected.id)
    } catch (e) {
      setMemberError(e instanceof Error ? e.message : 'Failed to add member')
    }
  }

  const handleRemoveMember = async (username: string) => {
    if (!selected) return
    try {
      await removeGroupMember(selected.id, username)
      await loadDetail(selected.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove member')
    }
  }

  const handleAddRule = async () => {
    if (!selected) return
    if (!ruleProvider.trim() || !ruleValue.trim()) {
      setRuleError('Provider and value are required')
      return
    }
    setRuleError(null)
    try {
      await addGroupPermission(selected.id, {
        provider: ruleProvider.trim(),
        value: ruleValue.trim(),
        matchType: ruleMatchType,
        grant: ruleGrant,
      })
      setRuleProvider('')
      setRuleValue('')
      await loadDetail(selected.id)
    } catch (e) {
      setRuleError(e instanceof Error ? e.message : 'Failed to add permission rule')
    }
  }

  const handleDeleteRule = async (ruleId: string) => {
    if (!selected) return
    try {
      await deleteGroupPermission(selected.id, ruleId)
      await loadDetail(selected.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete rule')
    }
  }

  if (loading) return <div className="p-6 text-slate-500 dark:text-slate-400">Loading groups…</div>

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100 mb-6">
        Permission Groups
      </h1>

      {error && (
        <div className="mb-4 p-3 rounded bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 text-sm">
          {error}
        </div>
      )}

      <div className="flex gap-6">
        {/* left: group list + create form */}
        <div className="w-80 flex-shrink-0 space-y-4">
          {/* create */}
          <div className="bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 p-4 space-y-3">
            <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">New Group</h2>
            <input
              className="w-full text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
              placeholder="Name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
            />
            <input
              className="w-full text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
              placeholder="Description (optional)"
              value={newDesc}
              onChange={(e) => setNewDesc(e.target.value)}
            />
            {createError && <p className="text-red-500 text-xs">{createError}</p>}
            <button
              onClick={handleCreate}
              className="w-full text-sm bg-blue-600 hover:bg-blue-700 text-white rounded px-3 py-1.5 transition-colors"
            >
              Create
            </button>
          </div>

          {/* list */}
          <div className="bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 divide-y divide-slate-100 dark:divide-slate-700">
            {groups.length === 0 && (
              <p className="p-4 text-sm text-slate-400">No groups defined.</p>
            )}
            {groups.map((g) => (
              <div
                key={g.id}
                className={`p-3 cursor-pointer transition-colors ${
                  selected?.id === g.id
                    ? 'bg-blue-50 dark:bg-blue-900/20'
                    : 'hover:bg-slate-50 dark:hover:bg-slate-700/50'
                }`}
                onClick={() => loadDetail(g.id)}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-800 dark:text-slate-100 truncate">
                      {g.name}
                    </p>
                    {g.description && (
                      <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                        {g.description}
                      </p>
                    )}
                    <p className="text-xs text-slate-400 mt-0.5">
                      {g.memberCount} member{g.memberCount !== 1 ? 's' : ''} · {g.ruleCount} rule
                      {g.ruleCount !== 1 ? 's' : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    {g.source === 'CONFIG' && (
                      <span className="text-xs bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 rounded px-1.5 py-0.5">
                        config
                      </span>
                    )}
                    {g.source === 'DB' && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          handleDelete(g.id)
                        }}
                        className="text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 transition-colors"
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* right: detail panel */}
        {selected ? (
          <div className="flex-1 space-y-5">
            <div className="bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 p-5">
              {selected.source === 'CONFIG' ? (
                <>
                  <div className="flex items-center gap-2 mb-1">
                    <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">
                      {selected.name}
                    </h2>
                    <span className="text-xs bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 rounded px-1.5 py-0.5">
                      config
                    </span>
                  </div>
                  {selected.description && (
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      {selected.description}
                    </p>
                  )}
                </>
              ) : (
                <div className="space-y-2">
                  <input
                    className="w-full text-base font-semibold border rounded px-2 py-1 text-slate-800 dark:text-slate-100 dark:bg-slate-700 dark:border-slate-600"
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    placeholder="Group name"
                  />
                  <input
                    className="w-full text-sm border rounded px-2 py-1 text-slate-500 dark:text-slate-400 dark:bg-slate-700 dark:border-slate-600"
                    value={editDesc}
                    onChange={(e) => setEditDesc(e.target.value)}
                    placeholder="Description (optional)"
                  />
                  {editError && <p className="text-red-500 text-xs">{editError}</p>}
                  <div className="flex justify-end">
                    <button
                      onClick={handleSaveEdit}
                      disabled={editSaving}
                      className="text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-3 py-1 transition-colors"
                    >
                      {editSaving ? 'Saving…' : 'Save'}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* members */}
            <div className="bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 p-5">
              <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">
                Members
              </h3>
              {selected.members.length === 0 ? (
                <p className="text-sm text-slate-400">No members.</p>
              ) : (
                <ul className="space-y-1 mb-3">
                  {selected.members.map((m) => (
                    <li key={m} className="flex items-center justify-between text-sm">
                      <span className="text-slate-700 dark:text-slate-200 font-mono">{m}</span>
                      {selected.source === 'DB' && (
                        <button
                          onClick={() => handleRemoveMember(m)}
                          className="text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 transition-colors"
                        >
                          Remove
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              {selected.source === 'DB' && (
                <div className="flex gap-2 mt-2">
                  <select
                    className="flex-1 text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
                    value={newMember}
                    onChange={(e) => setNewMember(e.target.value)}
                  >
                    <option value="">Select user…</option>
                    {users
                      .filter((u) => !selected.members.includes(u.username))
                      .map((u) => (
                        <option key={u.username} value={u.username}>
                          {u.username}
                        </option>
                      ))}
                  </select>
                  <button
                    onClick={handleAddMember}
                    className="text-sm bg-blue-600 hover:bg-blue-700 text-white rounded px-3 py-1 transition-colors"
                  >
                    Add
                  </button>
                </div>
              )}
              {memberError && <p className="text-red-500 text-xs mt-1">{memberError}</p>}
            </div>

            {/* permission rules */}
            <div className="bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 p-5">
              <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">
                Permission Rules
              </h3>
              {selected.rules.length === 0 ? (
                <p className="text-sm text-slate-400 mb-3">No rules.</p>
              ) : (
                <table className="w-full text-sm mb-4">
                  <thead>
                    <tr className="text-left text-xs text-slate-500 dark:text-slate-400 border-b border-slate-100 dark:border-slate-700">
                      <th className="pb-1 pr-3">Provider</th>
                      <th className="pb-1 pr-3">Value</th>
                      <th className="pb-1 pr-3">Match</th>
                      <th className="pb-1 pr-3">Grant</th>
                      {selected.source === 'DB' && <th className="pb-1" />}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50 dark:divide-slate-700/50">
                    {selected.rules.map((r: GroupPermissionRule) => (
                      <tr key={r.id} className="text-slate-700 dark:text-slate-300">
                        <td className="py-1 pr-3 font-mono text-xs">{r.provider}</td>
                        <td className="py-1 pr-3 font-mono text-xs">{r.value}</td>
                        <td className="py-1 pr-3 text-xs">{r.matchType}</td>
                        <td className="py-1 pr-3 text-xs">{r.grant}</td>
                        {selected.source === 'DB' && (
                          <td className="py-1 text-right">
                            <button
                              onClick={() => handleDeleteRule(r.id)}
                              className="text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 transition-colors"
                            >
                              Remove
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {selected.source === 'DB' && (
                <div className="space-y-2">
                  <div className="flex gap-2">
                    <select
                      className="text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
                      value={ruleProvider}
                      onChange={(e) => setRuleProvider(e.target.value)}
                    >
                      <option value="">Select provider…</option>
                      {providers.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                    <input
                      className="flex-1 text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
                      placeholder="Value (e.g. /myorg/**)"
                      value={ruleValue}
                      onChange={(e) => setRuleValue(e.target.value)}
                    />
                  </div>
                  <div className="flex gap-2">
                    <select
                      className="text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
                      value={ruleMatchType}
                      onChange={(e) => setRuleMatchType(e.target.value)}
                    >
                      <option value="LITERAL">Literal</option>
                      <option value="GLOB">Glob</option>
                      <option value="REGEX">Regex</option>
                    </select>
                    <select
                      className="text-sm border rounded px-2 py-1 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-100"
                      value={ruleGrant}
                      onChange={(e) => setRuleGrant(e.target.value)}
                    >
                      <option value="PUSH">Push</option>
                      <option value="REVIEW">Review</option>
                      <option value="PUSH_AND_REVIEW">Push and review</option>
                      <option value="SELF_CERTIFY">Self-certify</option>
                    </select>
                    <button
                      onClick={handleAddRule}
                      className="text-sm bg-blue-600 hover:bg-blue-700 text-white rounded px-3 py-1 transition-colors"
                    >
                      Add Rule
                    </button>
                  </div>
                  {ruleError && <p className="text-red-500 text-xs">{ruleError}</p>}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="flex-1 flex items-center justify-center text-slate-400 dark:text-slate-500 text-sm">
            Select a group to view details
          </div>
        )}
      </div>
    </div>
  )
}
