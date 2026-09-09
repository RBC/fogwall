import { useState } from 'react'

const KEY = 'fogwall-nav-collapsed'

/**
 * Sidebar collapsed state, persisted per-viewer in localStorage. Lifted out of the Sidebar so the collapse toggle can
 * live in the top bar (next to the breadcrumb) while the Sidebar renders from the same state.
 */
export function useNavCollapsed() {
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(KEY) === '1'
    } catch {
      return false
    }
  })

  const toggle = () =>
    setCollapsed((v) => {
      const next = !v
      try {
        localStorage.setItem(KEY, next ? '1' : '0')
      } catch {
        /* private mode — collapse is per-session only */
      }
      return next
    })

  return { collapsed, toggle }
}
