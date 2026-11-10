export function VerifiedBadge({ source }: { source?: string }) {
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-300"
      title="Confirmed via OAuth account linking"
    >
      verified{source ? ` (${source})` : ''}
    </span>
  )
}

export function LockedBadge({ source }: { source: string }) {
  const title =
    source === 'config'
      ? 'Defined in server configuration and cannot be removed'
      : `Managed by your ${source.toUpperCase()} identity provider and cannot be removed`
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-600 dark:bg-blue-900/30 dark:text-blue-300"
      title={title}
    >
      locked ({source})
    </span>
  )
}

/** The signed-in user's dashboard roles, as badges. */
export function RoleBadges({ authorities }: { authorities: string[] }) {
  return (
    <div className="flex flex-wrap gap-1 mt-2">
      {authorities
        .filter((a) => a.startsWith('ROLE_'))
        .map((a) => {
          const label = a.replace('ROLE_', '')
          const colour =
            label === 'ADMIN'
              ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300'
              : label === 'AUDITOR'
                ? 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-300'
                : label === 'SELF_CERTIFY'
                  ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'
                  : 'bg-gray-100 text-gray-600 dark:bg-slate-700 dark:text-gray-300'
          return (
            <span key={a} className="relative group inline-flex">
              <span
                className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${colour}`}
              >
                {label}
              </span>
              {label === 'SELF_CERTIFY' && (
                <span className="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 w-56 rounded bg-gray-800 px-2 py-1.5 text-xs text-white opacity-0 group-hover:opacity-100 transition-opacity z-10">
                  This role allows you to self-approve your own pushes, but an admin must also grant
                  the Self-certify permission on each individual repository.
                  <span className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-gray-800" />
                </span>
              )}
            </span>
          )
        })}
    </div>
  )
}
