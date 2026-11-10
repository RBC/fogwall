import type { ActivityWindow } from './types'

/** A period's label from its start: a day, "Week of" its Monday, or a month, in UTC like the periods themselves. */
export function formatPeriod(start: string, granularity: ActivityWindow['granularity']): string {
  const opts: Intl.DateTimeFormatOptions =
    granularity === 'MONTH'
      ? { month: 'short', year: 'numeric', timeZone: 'UTC' }
      : { month: 'short', day: 'numeric', timeZone: 'UTC' }
  const label = new Date(start).toLocaleDateString(undefined, opts)
  return granularity === 'WEEK' ? `Week of ${label}` : label
}

const PENDING = ['RECEIVED', 'PROCESSING', 'PENDING', 'APPROVED']
const FAILED = ['REJECTED', 'CANCELED', 'ERROR']

/**
 * Push counts by status folded into what happened: forwarded upstream, still in flight (received, under validation or
 * review, or approved and not yet forwarded), or stopped (rejected, canceled, errored).
 */
export function pushOutcomes(byStatus: Record<string, number>): {
  total: number
  forwarded: number
  pending: number
  failed: number
} {
  const count = (statuses: string[]) => statuses.reduce((n, s) => n + (byStatus[s] ?? 0), 0)
  return {
    total: Object.values(byStatus).reduce((a, b) => a + b, 0),
    forwarded: byStatus.FORWARDED ?? 0,
    pending: count(PENDING),
    failed: count(FAILED),
  }
}
