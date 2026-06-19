import { NavLink } from 'react-router-dom'
import type { CurrentUser } from '../types'

interface NavProps {
  currentUser: CurrentUser | null
}

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function logout() {
  const token = getCsrfToken()
  await fetch('/logout', {
    method: 'POST',
    headers: token ? { 'X-XSRF-TOKEN': token } : {},
    credentials: 'same-origin',
  })
  window.location.href = '/login.html?logout'
}

export function Nav({ currentUser }: NavProps) {
  return (
    <header className="bg-slate-800 text-white px-6 py-4 flex items-center gap-4 shadow">
      <NavLink
        to="/"
        className="flex items-center gap-2 shrink-0 hover:opacity-80 transition-opacity"
      >
        <svg
          className="h-8 w-auto"
          viewBox="0 0 28 32"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          {/* Shield outline */}
          <path
            d="M14 1 L26 6 L26 16 C26 23 14 31 14 31 C14 31 2 23 2 16 L2 6 Z"
            stroke="white"
            strokeWidth="1.8"
            strokeLinejoin="round"
            fill="none"
            opacity="0.85"
          />
          {/* Waves inside shield */}
          <path
            d="M7 10 Q10 8 14 10 Q18 12 21 10"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            fill="none"
            opacity="0.9"
          />
          <path
            d="M7 15 Q10 13 14 15 Q18 17 21 15"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            fill="none"
            opacity="0.7"
          />
          <path
            d="M8 20 Q11 18 14 20 Q17 22 20 20"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            fill="none"
            opacity="0.5"
          />
        </svg>
        <span className="text-white font-semibold tracking-tight text-xl">fogwall</span>
      </NavLink>

      <nav className="flex gap-1 ml-2">
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            'px-3 py-1 rounded text-sm transition-colors ' +
            (isActive
              ? 'bg-slate-600 text-white'
              : 'text-slate-300 hover:text-white hover:bg-slate-700')
          }
        >
          Pushes
        </NavLink>
        <NavLink
          to="/repos"
          className={({ isActive }) =>
            'px-3 py-1 rounded text-sm transition-colors ' +
            (isActive
              ? 'bg-slate-600 text-white'
              : 'text-slate-300 hover:text-white hover:bg-slate-700')
          }
        >
          Repos
        </NavLink>
        <NavLink
          to="/providers"
          className={({ isActive }) =>
            'px-3 py-1 rounded text-sm transition-colors ' +
            (isActive
              ? 'bg-slate-600 text-white'
              : 'text-slate-300 hover:text-white hover:bg-slate-700')
          }
        >
          Providers
        </NavLink>
        {currentUser?.authorities.includes('ROLE_ADMIN') && (
          <>
            <NavLink
              to="/users"
              className={({ isActive }) =>
                'px-3 py-1 rounded text-sm transition-colors ' +
                (isActive
                  ? 'bg-slate-600 text-white'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700')
              }
            >
              Users
            </NavLink>
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                'px-3 py-1 rounded text-sm transition-colors ' +
                (isActive
                  ? 'bg-slate-600 text-white'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700')
              }
            >
              Admin
            </NavLink>
          </>
        )}
      </nav>

      <div className="ml-auto flex items-center gap-3 text-sm text-slate-300">
        <a
          href="https://github.com/RBC/fogwall/blob/main/docs/CONFIGURATION.md"
          target="_blank"
          rel="noopener noreferrer"
          className="text-slate-400 hover:text-white transition-colors"
          title="Documentation"
        >
          Docs
        </a>
        {currentUser && (
          <>
            <NavLink
              to="/profile"
              className={({ isActive }) =>
                'transition-colors ' + (isActive ? 'text-white' : 'hover:text-white')
              }
            >
              {currentUser.username}
            </NavLink>
            <button
              onClick={logout}
              className="px-2 py-1 rounded text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 hover:text-white transition-colors"
            >
              Sign out
            </button>
          </>
        )}
      </div>
    </header>
  )
}
