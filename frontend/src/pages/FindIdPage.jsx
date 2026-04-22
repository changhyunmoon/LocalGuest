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

export function FindIdPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const initialRole =
    String(location.state?.role ?? '').toUpperCase() === 'GUIDE' ? 'GUIDE' : 'GUEST'

  const [role, setRole] = useState(initialRole)
  const [name, setName] = useState('')
  const [nickname, setNickname] = useState('')
  const [maskedEmail, setMaskedEmail] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const roleLabel = useMemo(() => (role === 'GUIDE' ? '가이드' : '여행자'), [role])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setMaskedEmail('')
    setLoading(true)
    try {
      const res = await apiRequest('/members/find-id', {
        method: 'POST',
        json: { name: name.trim(), nickname: nickname.trim(), role },
      })
      const text = await res.text()
      if (!res.ok) {
        throw new Error(parseErrorMessage(text))
      }
      const data = text ? JSON.parse(text) : {}
      if (typeof data.maskedEmail === 'string' && data.maskedEmail) {
        setMaskedEmail(data.maskedEmail)
      } else {
        throw new Error('응답 형식이 올바르지 않습니다.')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="form-page">
      <h1>아이디(이메일) 찾기</h1>
      <p className="form-hint">가입 시 입력한 이름·닉네임·{roleLabel} 유형이 일치하면 이메일 일부를 안내합니다.</p>

      <form className="form-card" onSubmit={(e) => void handleSubmit(e)}>
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
          <span>이름</span>
          <input value={name} onChange={(ev) => setName(ev.target.value)} required autoComplete="name" />
        </label>
        <label className="field">
          <span>닉네임</span>
          <input value={nickname} onChange={(ev) => setNickname(ev.target.value)} required autoComplete="nickname" />
        </label>

        {error && <p className="form-error">{error}</p>}
        {maskedEmail && (
          <div className="form-success">
            <p style={{ margin: 0 }}>등록된 이메일(일부만 표시)</p>
            <p style={{ margin: '0.35rem 0 0', fontWeight: 800, fontSize: '1.05rem' }}>{maskedEmail}</p>
          </div>
        )}

        <button type="submit" className="submit form-login-submit form-auth-primary-btn" disabled={loading}>
          {loading ? '확인 중…' : '확인'}
        </button>

        <nav className="form-account-links" aria-label="다른 메뉴">
          <Link to="/auth/login" className="form-text-link">
            로그인
          </Link>
          <span className="form-account-links__sep" aria-hidden>
            ·
          </span>
          <Link to="/auth/forgot-password" className="form-text-link">
            비밀번호 찾기
          </Link>
          <span className="form-account-links__sep" aria-hidden>
            ·
          </span>
          <Link to="/auth/signup" className="form-text-link">
            회원가입
          </Link>
        </nav>
      </form>

      <p className="form-footer" style={{ marginTop: '1rem' }}>
        <button type="button" className="form-text-link" onClick={() => navigate(-1)}>
          이전 페이지
        </button>
      </p>
    </div>
  )
}
