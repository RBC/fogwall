import { useEffect, useState } from 'react'
import { fetchGroups, fetchProviders, fetchUsers } from '../../api'

/** Where a filter's values come from. */
export type SuggestionSource = 'users' | 'groups' | 'providers' | 'subjects'

export type Suggestions = Record<SuggestionSource, string[]>

const EMPTY: Suggestions = { users: [], groups: [], providers: [], subjects: [] }

async function names(load: () => Promise<unknown>, field: string): Promise<string[]> {
  try {
    const rows = (await load()) as Record<string, string>[]
    return rows
      .map((r) => r[field])
      .filter(Boolean)
      .sort()
  } catch {
    return []
  }
}

async function loadSuggestions(): Promise<Suggestions> {
  const [users, groups, providers] = await Promise.all([
    names(fetchUsers, 'username'),
    names(fetchGroups, 'name'),
    names(fetchProviders, 'name'),
  ])
  return { users, groups, providers, subjects: [...users, ...groups].sort() }
}

// One load per page visit, shared by every listing on the page.
let pending: Promise<Suggestions> | null = null

/** The users, groups and providers that exist now, for filters to offer. Empty until loaded, or if a load fails. */
export function useSuggestions(): Suggestions {
  const [suggestions, setSuggestions] = useState<Suggestions>(EMPTY)
  useEffect(() => {
    pending ??= loadSuggestions().finally(() => {
      pending = null
    })
    let live = true
    pending.then((s) => live && setSuggestions(s))
    return () => {
      live = false
    }
  }, [])
  return suggestions
}
