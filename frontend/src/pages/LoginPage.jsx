import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { clearStoredOauthRejection, getStoredOauthRejection, setStoredOauthRejection } from '../lib/oauthRejection.js'
import { LoginFormPanel } from '../components/auth/LoginFormPanel.jsx'

import './FormPage.css'

export function LoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const returnTo = location.state?.returnTo ?? '/mypage'
  const preferredRole = String(location.state?.preferredRole ?? '')
  const hint = location.state?.hint ?? ''

  const [oauthRejection, setOauthRejection] = useState(() => getStoredOauthRejection())

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    if (params.get('authError') === '1') {
      const r = String(params.get('reason') ?? 'oauth_failed')
      setStoredOauthRejection(r)
      setOauthRejection(r)
      navigate({ pathname: '/auth/login' }, { replace: true, state: location.state })
    }
  }, [location.search, location.state, navigate])

  return (
    <div className="form-page">
      <LoginFormPanel
        returnTo={returnTo}
        hint={hint}
        preferredRole={preferredRole}
        oauthRejection={oauthRejection}
        onCloseOauthRejection={() => {
          clearStoredOauthRejection()
          setOauthRejection(null)
        }}
      />
    </div>
  )
}
