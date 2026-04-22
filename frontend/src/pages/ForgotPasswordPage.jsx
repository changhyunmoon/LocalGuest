import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './FormPage.css'

function parseErrorMessage(text) {
  if (!text?.trim()) return '요청을 처리할 수 없습니다.'
  try {
    const j = JSON.parse(text)
    if (typeof j.message === 'string' && j.message.trim()) return j.message.trim()
  } catch {
    /* ignore */
  }
  return text.trim()
}

export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const initialRole =
    String(location.state?.role ?? '').toUpperCase() === 'GUIDE' ? 'GUIDE' : 'GUEST'

  const [role, setRole] = useState(initialRole)
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPassword2, setNewPassword2] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  const [sendBusy, setSendBusy] = useState(false)
  const [submitBusy, setSubmitBusy] = useState(false)

  const roleLabel = useMemo(() => (role === 'GUIDE' ? '가이드' : '여행자'), [role])

  const handleSend = async () => {
    setError('')
    setSendBusy(true)
    try {
      const res = await apiRequest('/members/password-reset/send', {
        method: 'POST',
        json: { email: email.trim(), role },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseErrorMessage(text))
      setCodeSent(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : '발송에 실패했습니다.')
    } finally {
      setSendBusy(false)
    }
  }

  const handleReset = async (e) => {
    e.preventDefault()
    setError('')
    if (newPassword !== newPassword2) {
      setError('새 비밀번호가 서로 다릅니다.')
      return
    }
    setSubmitBusy(true)
    try {
      const res = await apiRequest('/members/password-reset/confirm', {
        method: 'POST',
        json: {
          email: email.trim(),
          role,
          code: code.replace(/\D/g, '').slice(0, 6),
          newPassword,
        },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseErrorMessage(text))
      setDone(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : '처리에 실패했습니다.')
    } finally {
      setSubmitBusy(false)
    }
  }

  return (
    <div className="form-page">
      <h1>비밀번호 찾기</h1>
      <p className="form-hint">
        {roleLabel} 계정의 가입 이메일로 인증번호를 보낸 뒤, 새 비밀번호를 설정합니다. (소셜 전용 계정은 구글 로그인을 이용해 주세요.)
      </p>

      <form className="form-card" onSubmit={(e) => void handleReset(e)}>
        <div className="form-role-switch" role="tablist" aria-label="가입 유형">
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
            value={email}
            onChange={(ev) => setEmail(ev.target.value)}
            required
            autoComplete="email"
          />
        </label>

        <div className="field">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: '0.5rem' }}>
            <span>인증번호</span>
            <button
              type="button"
              className="submit ghost"
              style={{ padding: '0.35rem 0.65rem', fontSize: '0.82rem' }}
              onClick={() => void handleSend()}
              disabled={sendBusy || !email.trim()}
            >
              {sendBusy ? '발송 중…' : codeSent ? '재발송' : '인증번호 발송'}
            </button>
          </div>
          <input
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="6자리"
            maxLength={6}
            value={code}
            onChange={(ev) => setCode(ev.target.value.replace(/\D/g, '').slice(0, 6))}
          />
        </div>
        {codeSent && (
          <p className="form-hint" style={{ marginTop: '-0.35rem', color: '#047857', fontWeight: 700 }}>
            인증번호가 발송되었습니다.
          </p>
        )}

        <label className="field">
          <span>새 비밀번호</span>
          <input
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(ev) => setNewPassword(ev.target.value)}
            minLength={8}
            maxLength={16}
            required
          />
        </label>
        <label className="field">
          <span>새 비밀번호 확인</span>
          <input
            type="password"
            autoComplete="new-password"
            value={newPassword2}
            onChange={(ev) => setNewPassword2(ev.target.value)}
            minLength={8}
            maxLength={16}
            required
          />
        </label>

        {error && <p className="form-error">{error}</p>}
        {done && (
          <div className="form-success">
            <p style={{ margin: 0 }}>비밀번호가 변경되었습니다. 로그인해 주세요.</p>
          </div>
        )}

        {!done && (
          <button type="submit" className="submit form-login-submit form-auth-primary-btn" disabled={submitBusy}>
            {submitBusy ? '처리 중…' : '비밀번호 변경'}
          </button>
        )}
        {done && (
          <button type="button" className="submit form-login-submit form-auth-primary-btn" onClick={() => navigate('/auth/login')}>
            로그인으로 이동
          </button>
        )}

        <nav className="form-account-links" aria-label="다른 메뉴">
          <Link to="/auth/login" className="form-text-link">
            로그인
          </Link>
          <span className="form-account-links__sep" aria-hidden>
            ·
          </span>
          <Link to="/auth/find-id" className="form-text-link">
            아이디 찾기
          </Link>
          <span className="form-account-links__sep" aria-hidden>
            ·
          </span>
          <Link to="/auth/signup" className="form-text-link">
            회원가입
          </Link>
        </nav>
      </form>
    </div>
  )
}
