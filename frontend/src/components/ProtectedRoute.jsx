import { Navigate, useLocation } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

export function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    const returnTo = `${location.pathname}${location.search}`
    return <Navigate to="/auth/login" replace state={{ returnTo }} />
  }

  return children
}
