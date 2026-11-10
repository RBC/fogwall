/** The class of one tab in a strip of NavLink tabs, so each tab has its own URL. */
export function tabLinkClass({ isActive }: { isActive: boolean }): string {
  return (
    'whitespace-nowrap px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px ' +
    (isActive
      ? 'border-slate-700 text-slate-800 dark:border-slate-400 dark:text-slate-200'
      : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300')
  )
}
