import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

type ToastKind = 'error' | 'success' | 'info'

interface Toast {
  id: number
  kind: ToastKind
  message: string
}

interface ToastApi {
  error: (message: string) => void
  success: (message: string) => void
  info: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

// Errors linger long enough to read a server detail; confirmations clear quickly.
const DISMISS_MS: Record<ToastKind, number> = { error: 8000, success: 4000, info: 5000 }

const STYLES: Record<ToastKind, string> = {
  error:
    'border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/80 dark:text-red-200',
  success:
    'border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/80 dark:text-green-200',
  info: 'border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/80 dark:text-blue-200',
}

/**
 * App-wide transient notifications. Action failures (a rejected API call) pop here rather than as inline text far
 * from where the user clicked; genuine field validation stays inline next to the field. Rendered top-right, above the
 * content, and auto-dismissed — errors linger longest so a server detail can be read.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id))
  }, [])

  const push = useCallback((kind: ToastKind, message: string) => {
    setToasts((current) => [...current, { id: nextId.current++, kind, message }])
  }, [])

  const api = useMemo<ToastApi>(
    () => ({
      error: (m) => push('error', m),
      success: (m) => push('success', m),
      info: (m) => push('info', m),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="fixed top-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2"
        aria-live="polite"
        aria-atomic="false"
      >
        {toasts.map((t) => (
          <ToastRow key={t.id} toast={t} onDismiss={() => dismiss(t.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

function ToastRow({ toast, onDismiss }: { toast: Toast; onDismiss: () => void }) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, DISMISS_MS[toast.kind])
    return () => clearTimeout(timer)
  }, [toast.kind, onDismiss])

  return (
    <div
      role={toast.kind === 'error' ? 'alert' : 'status'}
      className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-sm shadow-lg motion-safe:animate-[fadeIn_150ms_ease-out] ${STYLES[toast.kind]}`}
    >
      <span className="min-w-0 flex-1 break-words">{toast.message}</span>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="-mr-1 -mt-0.5 shrink-0 rounded px-1 text-lg leading-none opacity-60 hover:opacity-100"
      >
        ×
      </button>
    </div>
  )
}

/** Access the toast API. Must be called under a {@link ToastProvider}. */
// eslint-disable-next-line react-refresh/only-export-components -- hook colocated with its provider; HMR-only rule
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within a ToastProvider')
  return ctx
}
