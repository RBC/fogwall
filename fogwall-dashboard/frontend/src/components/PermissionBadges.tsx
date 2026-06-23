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
    PUSH: 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    REVIEW: 'bg-green-50 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    PUSH_AND_REVIEW: 'bg-teal-50 text-teal-700 dark:bg-teal-900/30 dark:text-teal-300',
    SELF_CERTIFY: 'bg-yellow-50 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
  }
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${styles[operations]}`}
    >
      {operations.toLowerCase()}
    </span>
  )
}
