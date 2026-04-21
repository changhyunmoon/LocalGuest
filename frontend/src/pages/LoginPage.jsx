import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { beginGoogleOAuth } from '../api/client'
import { useAuth } from '../context/useAuth.js'

import './FormPage.css'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const returnTo = location.state?.returnTo ?? '/mypage'
  const preferredRole = String(location.state?.preferredRole ?? '')
  const initialRole =
    preferredRole.toUpperCase() === 'GUIDE' || String(returnTo).startsWith('/guide')
      ? 'GUIDE'
      : 'GUEST'

  const [email, setEmail] = useState(() => sessionStorage.getItem('prefill_login_email') ?? '')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState(initialRole)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [flash, setFlash] = useState('')
  const [roleMismatch, setRoleMismatch] = useState(null)

  useEffect(() => {
    if (location.state?.hint) {
      setFlash(location.state.hint)
      return
    }
    const f = sessionStorage.getItem('login_flash')
    if (f) {
      setFlash(f)
      sessionStorage.removeItem('login_flash')
    }
  }, [location.state])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const meta = await login(email.trim(), password, role)
      sessionStorage.removeItem('prefill_login_email')
      let destination = returnTo
      try {
        const override = sessionStorage.getItem('localguest_login_return_override')
        if (override) {
          sessionStorage.removeItem('localguest_login_return_override')
          destination = override
        }
      } catch {
        /* ignore */
      }
      const req = String(meta?.requestedRole ?? role).toUpperCase()
      const signed = String(meta?.signedInRole ?? '').toUpperCase()
      if (signed && req && signed !== req) {
        setRoleMismatch({ destination, requestedRole: req, signedInRole: signed })
        return
      }
      navigate(destination, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인 실패')
    } finally {
      setLoading(false)
    }
  }

  const roleMismatchCopy = () => {
    if (!roleMismatch) return ''
    const label = (r) => (r === 'GUIDE' ? '가이드 (GUIDE)' : '여행자 (GUEST)')
    return `선택하신 로그인 유형은 ${label(roleMismatch.requestedRole)}인데, 실제로는 ${label(roleMismatch.signedInRole)} 계정으로 로그인되었습니다. (이메일·비밀번호는 맞습니다.)`
  }

  return (
    <div className="form-page">
      <h1>로그인</h1>

      {flash && (
        <div className="form-success" style={{ marginBottom: '1rem' }}>
          {flash}
        </div>
      )}

      <form className="form-card" onSubmit={(e) => void handleSubmit(e)}>
        <div className="form-role-switch" role="tablist" aria-label="로그인 유형">
          <button
            type="button"
            className={`form-role-btn${role === 'GUEST' ? ' is-on' : ''}`}
            onClick={() => setRole('GUEST')}
            role="tab"
            aria-selected={role === 'GUEST'}
          >
            여행자
          </button>
          <button
            type="button"
            className={`form-role-btn${role === 'GUIDE' ? ' is-on' : ''}`}
            onClick={() => setRole('GUIDE')}
            role="tab"
            aria-selected={role === 'GUIDE'}
          >
            가이드
          </button>
        </div>
        <label className="field">
          <span>이메일</span>
          <input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(ev) => setEmail(ev.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>비밀번호</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(ev) => setPassword(ev.target.value)}
            required
          />
        </label>

        <div className="form-social form-social--inside">
          <button
            type="button"
            className="submit ghost form-google-login"
            onClick={() => beginGoogleOAuth(role, returnTo)}
            title="Google OAuth2 로그인"
          >
            <span className="form-google-login__icon" aria-hidden="true">
              G
            </span>
            <span>구글로 로그인</span>
          </button>
        </div>

        {error && <p className="form-error">{error}</p>}

        <button type="submit" className="submit form-login-submit" disabled={loading}>
          {loading ? '처리 중…' : '로그인'}
        </button>
      </form>

      {roleMismatch && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 50,
            padding: '1rem',
          }}
          role="dialog"
          aria-modal="true"
          aria-labelledby="role-mismatch-title"
        >
          <div
            className="form-card"
            style={{ maxWidth: 420, width: '100%', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.35)' }}
          >
            <h2 id="role-mismatch-title" style={{ marginTop: 0 }}>
              로그인 유형 안내
            </h2>
            <p className="form-hint" style={{ marginBottom: '1rem' }}>
              {roleMismatchCopy()}
            </p>
            <button
              type="button"
              className="submit"
              onClick={() => {
                const dest = roleMismatch.destination
                setRoleMismatch(null)
                navigate(dest, { replace: true })
              }}
            >
              확인 후 이동
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
