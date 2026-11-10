import { useEffect, useState } from 'react'
import { Navigate, NavLink, useParams } from 'react-router'
import { fetchMe } from '../api'
import { ContributionActivityView } from '../components/ContributionActivityView'
import { MyPermissions } from '../components/MyPermissions'
import { RoleBadges } from '../components/SourceBadges'
import { tabLinkClass } from '../tabs'
import type { CurrentUser } from '../types'

const TABS = [
  { slug: 'activity', label: 'Activity' },
  { slug: 'permissions', label: 'Permissions' },
] as const

/** What the signed-in user has done through fogwall and what they may do: the view half of their account. */
export function Me() {
  const { tab } = useParams()
  const [me, setMe] = useState<CurrentUser | null>(null)

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => {})
  }, [])

  if (!TABS.some((t) => t.slug === tab)) return <Navigate to="/me/activity" replace />

  return (
    <div className="max-w-7xl px-6 py-6 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">
          {me?.username ?? 'You'}
        </h1>
        {me && <RoleBadges authorities={me.authorities} />}
      </div>

      <nav
        aria-label="Your account"
        className="flex gap-1 overflow-x-auto border-b border-gray-200 dark:border-slate-700"
      >
        {TABS.map((t) => (
          <NavLink key={t.slug} to={`/me/${t.slug}`} className={tabLinkClass}>
            {t.label}
          </NavLink>
        ))}
      </nav>

      {tab === 'activity' && <ContributionActivityView scope={{ kind: 'me' }} />}
      {tab === 'permissions' && <MyPermissions />}
    </div>
  )
}
