import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'

import { AppLayout } from './layouts/AppLayout'
import { authRoutes } from './routes/authRoutes'
import { commonRoutes } from './routes/commonRoutes'
import { guideRoutes } from './routes/guideRoutes'
import { mypageRoutes } from './routes/mypageRoutes'

const rawDevStartPath = String(import.meta.env.VITE_DEV_START_PATH ?? '').trim()
const devStartPath = rawDevStartPath ? (rawDevStartPath.startsWith('/') ? rawDevStartPath : `/${rawDevStartPath}`) : ''
const resolvedCommonRoutes = commonRoutes.map((route) => {
  if (!route.index) return route
  if (!import.meta.env.DEV || !devStartPath) return route
  return { ...route, element: <Navigate to={devStartPath} replace /> }
})

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      ...resolvedCommonRoutes,
      ...authRoutes,
      ...mypageRoutes,
      ...guideRoutes,
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
