import type { ReactNode } from 'react'
import type { ChangeOrigin } from '../../types'

export function Muted({ children }: { children: ReactNode }) {
  return <span className="text-gray-400 dark:text-gray-500">{children}</span>
}

/** Where a change came from; a configuration change also names the revision it was loaded from. */
export function Origin({ origin, revision }: { origin: ChangeOrigin; revision?: string }) {
  return (
    <span>
      {origin.toLowerCase()}
      {revision && (
        <span className="ml-1 font-mono text-xs text-gray-400 dark:text-gray-500" title={revision}>
          {revision.length > 19 ? revision.slice(0, 19) + '…' : revision}
        </span>
      )}
    </span>
  )
}

export function Roles({ roles }: { roles: string[] }) {
  return roles.length > 0 ? (
    <span className="font-mono text-xs">{roles.join(', ')}</span>
  ) : (
    <Muted>none</Muted>
  )
}

const ACTION_CLASSES: Record<string, string> = {
  GRANTED: 'text-green-700 dark:text-green-400',
  ADDED: 'text-green-700 dark:text-green-400',
  REVOKED: 'text-red-700 dark:text-red-400',
  REMOVED: 'text-red-700 dark:text-red-400',
}

export function Action({ action }: { action: string }) {
  return <span className={'text-xs font-semibold ' + (ACTION_CLASSES[action] ?? '')}>{action}</span>
}
