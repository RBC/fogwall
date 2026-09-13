import { NavLink } from 'react-router'

const TABS = [
  { to: '/contributions', label: 'Activity', end: true },
  { to: '/contributions/issues', label: 'Report an issue' },
]

/**
 * Moves between the contribution surfaces: the audit view of every SCM API record, and the form that acts. Carried on
 * the page as well as in the rail, which hides its sub-destinations when collapsed. The caller owns the width and
 * gutter it sits in.
 */
export function ContributionsTabs() {
  return (
    <div className="border-b border-gray-200 dark:border-slate-700">
      <nav aria-label="Contributions" className="-mb-px flex gap-6">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              'border-b-2 py-2 text-sm font-medium transition-colors ' +
              (isActive
                ? 'border-blue-600 text-blue-700 dark:border-blue-400 dark:text-blue-300'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200')
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
