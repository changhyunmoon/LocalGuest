import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'
import { LoginPage } from '../pages/LoginPage'
import { OnboardingPage } from '../pages/OnboardingPage'
import { SignupPage } from '../pages/SignupPage'

function OAuth2CallbackPage() {
  const { setToken } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const token = String(params.get('token') ?? '').trim()
    if (!token) return
    setToken(token)

    let destination = '/mypage'
    try {
      const saved = sessionStorage.getItem('oauth_return_to')
      if (saved) {
        destination = saved
        sessionStorage.removeItem('oauth_return_to')
      }
    } catch {
      /* ignore */
    }
    navigate(destination, { replace: true })
  }, [location.search, navigate, setToken])

  const hasToken = new URLSearchParams(location.search).has('token')
  if (hasToken) {
    return <p style={{ margin: '2rem auto', maxWidth: 420 }}>소셜 로그인 처리 중입니다…</p>
  }
  return (
    <div style={{ margin: '2rem auto', maxWidth: 520 }}>
      <h2>소셜 로그인 콜백 처리 실패</h2>
      <p>토큰이 전달되지 않았습니다. 백엔드 OAuth2 리다이렉트 URL 설정을 확인해 주세요.</p>
    </div>
  )
}

export const authRoutes = [
  { path: 'auth/login', element: <LoginPage /> },
  { path: 'auth/signup', element: <SignupPage /> },
  { path: 'oauth2/callback', element: <OAuth2CallbackPage /> },
  { path: 'onboarding', element: <OnboardingPage /> },
]

