import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

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
      await login(email.trim(), password, role)
      sessionStorage.removeItem('prefill_login_email')
      navigate(returnTo, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인 실패')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="form-page">
      <h1>로그인</h1>
      <p className="form-hint">F01-02 · 이메일 / 비밀번호 (백엔드 JWT)</p>

      {flash && (
        <div className="form-success" style={{ marginBottom: '1rem' }}>
          {flash}
        </div>
      )}

      <form className="form-card" onSubmit={(e) => void handleSubmit(e)}>
        <label className="field">
          <span>로그인 유형</span>
          <select value={role} onChange={(ev) => setRole(ev.target.value)}>
            <option value="GUEST">여행자 (GUEST)</option>
            <option value="GUIDE">가이드 (GUIDE)</option>
          </select>
        </label>
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

        {error && <p className="form-error">{error}</p>}

        <button type="submit" className="submit" disabled={loading}>
          {loading ? '처리 중…' : '로그인'}
        </button>
      </form>

      <div className="form-social">
        <p>소셜 로그인</p>
        <button type="button" className="submit ghost" disabled title="백엔드 미구현">
          카카오 / 네이버 (예정)
        </button>
      </div>

      <p className="form-footer">
        계정이 없나요? <Link to="/auth/signup">회원가입</Link>
      </p>
    </div>
  )
}
