import type { CurrentUser } from './types'

type DashboardRole = 'READER' | 'USER' | 'AUDITOR' | 'ADMIN'

/** Whether the signed-in user holds a dashboard role. False until /api/me has loaded. */
export function hasRole(user: CurrentUser | null | undefined, role: DashboardRole): boolean {
  return user?.authorities?.includes(`ROLE_${role}`) ?? false
}

/**
 * A session acts only with USER or ADMIN, and never as an auditor. Readers and auditors see the dashboard without
 * its controls. False until /api/me has loaded, so a route guard does not redirect before the session is known.
 */
export function isReadOnly(user: CurrentUser | null | undefined): boolean {
  if (!user) return false
  return !(hasRole(user, 'USER') || hasRole(user, 'ADMIN')) || hasRole(user, 'AUDITOR')
}

/** Whether the user may change users, groups, permissions, rules and configuration. */
export function canAdminister(user: CurrentUser | null | undefined): boolean {
  return hasRole(user, 'ADMIN') && !hasRole(user, 'AUDITOR')
}
