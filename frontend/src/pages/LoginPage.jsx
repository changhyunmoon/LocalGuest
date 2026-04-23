import { useLocation } from 'react-router-dom'

import { LoginFormPanel } from '../components/auth/LoginFormPanel.jsx'

import './FormPage.css'

export function LoginPage() {
  const location = useLocation()
  const returnTo = location.state?.returnTo ?? '/mypage'
  const preferredRole = String(location.state?.preferredRole ?? '')
  const hint = location.state?.hint ?? ''

  return (
    <div className="form-page">
      <LoginFormPanel returnTo={returnTo} hint={hint} preferredRole={preferredRole} />
    </div>
  )
}
