import type { RepoPermission } from '../types'

export function PathTypeBadge({ matchType }: { matchType: RepoPermission['matchType'] }) {
  const styles = {
    LITERAL: 'bg-gray-100 text-gray-600 dark:bg-slate-700 dark:text-gray-300',
    GLOB: 'bg-purple-50 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
    REGEX: 'bg-orange-50 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  }
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${styles[matchType]}`}
    >
      {matchType.toLowerCase()}
    </span>
  )
}

export function OperationsBadge({ operations }: { operations: RepoPermission['grant'] }) {
  const styles: Record<RepoPermission['grant'], string> = {
    PUSH: 'bg-sky-50 text-sky-700 dark:bg-sky-900/30 dark:text-sky-300',
    REVIEW: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300',
    PUSH_AND_REVIEW: 'bg-teal-50 text-teal-700 dark:bg-teal-900/30 dark:text-teal-300',
    SELF_CERTIFY: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
    ISSUE: 'bg-fuchsia-50 text-fuchsia-700 dark:bg-fuchsia-900/30 dark:text-fuchsia-300',
    PROPOSE: 'bg-violet-50 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300',
    MERGE: 'bg-rose-50 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300',
    MAINTAIN: 'bg-orange-50 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  }
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${styles[operations]}`}
    >
      {operations.toLowerCase()}
    </span>
  )
}
