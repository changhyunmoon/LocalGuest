import { Navigate, useLocation } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

/**
 * 로그인 + JWT 역할 GUIDE만 허용. 그 외는 홈으로 보냄(게스트가 가이드 URL에 남은 경우).
 */
export function ProtectedGuideRoute({ children }) {
  const { isAuthenticated, isGuide } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    const returnTo = `${location.pathname}${location.search}`
    return <Navigate to="/auth/login" replace state={{ returnTo }} />
  }

  if (!isGuide) {
    return <Navigate to="/" replace />
  }

  return children
}
