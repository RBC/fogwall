import { useEffect, useState } from 'react'
import { fetchProviders } from '../api'
import type { Provider } from '../types'

export function Providers() {
  const [providers, setProviders] = useState<Provider[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchProviders()
      .then(setProviders)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-4">
      <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-200">Active Providers</h2>

      {loading && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">Loading…</div>
      )}

      {!loading && providers.length === 0 && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">
          No providers configured.
        </div>
      )}

      {providers.map((p) => (
        <div
          key={p.name}
          className="bg-white rounded-lg shadow border border-gray-200 px-6 py-4 dark:bg-slate-800 dark:border-slate-700"
        >
          <div className="flex items-center gap-3 mb-3">
            <img
              src={`https://${p.host}/favicon.ico`}
              className="w-5 h-5 rounded"
              alt=""
              onError={(e) => (e.currentTarget.style.display = 'none')}
            />
            <span className="font-semibold text-gray-900 dark:text-gray-100">{p.name}</span>
            <a
              href={p.uri}
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-600 hover:underline text-sm font-mono dark:text-blue-400"
            >
              {p.uri}
            </a>
          </div>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="text-gray-500 text-xs uppercase tracking-wide dark:text-gray-400">
                Store &amp; Forward (push)
              </span>
              <div className="font-mono text-xs text-gray-700 mt-0.5 dark:text-gray-300">
                {p.pushPath}/*
              </div>
            </div>
            <div>
              <span className="text-gray-500 text-xs uppercase tracking-wide dark:text-gray-400">
                Transparent Proxy
              </span>
              <div className="font-mono text-xs text-gray-700 mt-0.5 dark:text-gray-300">
                {p.proxyPath}/*
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
