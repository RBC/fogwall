import type { CurrentUser } from './types'

/** Whether the signed-in user holds a dashboard role. False until /api/me has loaded. */
export function hasRole(user: CurrentUser | null | undefined, role: 'ADMIN' | 'AUDITOR'): boolean {
  return user?.authorities?.includes(`ROLE_${role}`) ?? false
}

/** An auditor's session is read-only, whatever other role it holds. */
export function isReadOnly(user: CurrentUser | null | undefined): boolean {
  return hasRole(user, 'AUDITOR')
}

/** Whether the user may change users, groups, permissions, rules and configuration. */
export function canAdminister(user: CurrentUser | null | undefined): boolean {
  return hasRole(user, 'ADMIN') && !isReadOnly(user)
}
