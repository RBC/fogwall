import { useEffect, useState } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { fetchConfig, fetchMe } from './api'
import { Breadcrumbs } from './components/Breadcrumbs'
import { Sidebar } from './components/Sidebar'
import { ToastProvider } from './components/Toast'
import { useDarkMode } from './hooks/useDarkMode'
import { MirrorCache } from './pages/MirrorCache'
import { Operations } from './pages/Operations'
import { Overview } from './pages/Overview'
import { ScmApiActionList } from './pages/ScmApiActionList'
import { Providers } from './pages/Providers'
import { PushDetail } from './pages/PushDetail'
import { PushDiff } from './pages/PushDiff'
import { PushList } from './pages/PushList'
import { Profile } from './pages/Profile'
import { Repos } from './pages/Repos'
import { Setup } from './pages/Setup'
import { Groups } from './pages/Groups'
import { Legal } from './pages/Legal'
import { Users } from './pages/Users'
import { UserDetail } from './pages/UserDetail'
import type { CurrentUser } from './types'

export default function App() {
  const { dark, toggle: toggleDark } = useDarkMode()
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [authProvider, setAuthProvider] = useState<string>('local')
  const [bulkReview, setBulkReview] = useState<boolean>(false)

  useEffect(() => {
    fetchMe().then(setCurrentUser).catch(console.error)
    fetchConfig()
      .then((c) => {
        setAuthProvider(c.authProvider)
        setBulkReview(c.bulkReview ?? false)
      })
      .catch(console.error)
  }, [])

  return (
    <BrowserRouter basename="/dashboard">
      <ToastProvider>
        <div className="bg-gray-100 dark:bg-slate-900 min-h-screen flex" data-hmr-test="1">
          <Sidebar currentUser={currentUser} dark={dark} toggleDark={toggleDark} />
          <div className="flex-1 flex flex-col min-w-0">
            <Breadcrumbs />
            <main className="flex-1">
              <Routes>
                <Route path="/" element={<Overview currentUser={currentUser} />} />
                <Route
                  path="/pushes"
                  element={<PushList currentUser={currentUser} bulkReviewEnabled={bulkReview} />}
                />
                <Route
                  path="/push/:id"
                  element={<PushDetail currentUser={currentUser} dark={dark} />}
                />
                <Route path="/push/:id/diff" element={<PushDiff dark={dark} />} />
                <Route path="/providers" element={<Providers />} />
                <Route path="/setup" element={<Setup />} />
                <Route path="/repos" element={<Repos currentUser={currentUser} />} />
                <Route path="/proposals" element={<ScmApiActionList currentUser={currentUser} />} />
                <Route path="/profile" element={<Profile />} />
                <Route path="/users" element={<Users authProvider={authProvider} />} />
                <Route
                  path="/users/:username"
                  element={<UserDetail authProvider={authProvider} currentUser={currentUser} />}
                />
                <Route path="/groups" element={<Groups />} />
                <Route path="/operations" element={<Operations />} />
                <Route path="/mirror-cache" element={<MirrorCache />} />
                <Route path="/legal" element={<Legal />} />
              </Routes>
            </main>
          </div>
        </div>
      </ToastProvider>
    </BrowserRouter>
  )
}
