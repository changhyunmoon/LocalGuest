import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useAuth } from '../context/useAuth.js'
import { getGuestDisplayName, loadGuestPrivacyForm } from '../lib/guestMypagePrefs.js'
import { getEmailFromToken, getRoleFromToken, parseJwtPayload } from '../lib/jwt.js'

import './MypageMemberPages.css'

function parseApiErrorMessage(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

export function MypageProfilePage() {
  const { email, token, logout } = useAuth()
  const navigate = useNavigate()

  const claims = useMemo(() => parseJwtPayload(token ?? ''), [token])
  const jwtEmail = useMemo(() => (token ? getEmailFromToken(token) : null), [token])
  const jwtRole = useMemo(() => (token ? getRoleFromToken(token) : null), [token])

  const [nickname, setNickname] = useState('')
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')

  useEffect(() => {
    const f = loadGuestPrivacyForm(email)
    setNickname(f.nickname ?? '')
  }, [email])

  const displayName = useMemo(() => getGuestDisplayName(email), [email, nickname])

  const onWithdraw = async () => {
    const ok = window.confirm('정말 탈퇴할까요? 탈퇴 후에는 복구가 어려울 수 있어요.')
    if (!ok) return

    setBusy(true)
    setToast('')
    try {
      const res = await apiRequest('/members/me?role=GUEST', { method: 'DELETE' })
      const text = await res.text()
      if (!res.ok) throw new Error(parseApiErrorMessage(text))
      await logout()
      navigate('/', { replace: true, state: { hint: '회원 탈퇴가 완료되었습니다.' } })
    } catch (e) {
      setToast(e instanceof Error ? e.message : '탈퇴 처리 실패')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mp-member">
      <h1>👤 프로필</h1>
      <p className="sub">
        현재 백엔드는 <code>POST /members/join</code>, <code>DELETE /members/me?role=...</code> 중심입니다. 표시 정보는{' '}
        <strong>JWT + 로컬 설정(/mypage/privacy)</strong> 기반으로 구성합니다.
      </p>

      {toast && <p className="err">{toast}</p>}

      <div className="mp-scrap-hero" style={{ marginBottom: '1rem' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '1rem', fontWeight: 800 }}>{displayName}</h2>
          <p style={{ margin: '0.35rem 0 0', color: '#4b5563', lineHeight: 1.55 }}>
            이메일: <strong>{email ?? '—'}</strong>
            <br />
            표시 닉네임(로컬): <strong>{nickname?.trim() ? nickname.trim() : '—'}</strong>
          </p>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem', justifyContent: 'flex-end' }}>
          <Link to="/mypage/privacy" className="mp-btn" style={{ textDecoration: 'none' }}>
            개인정보/알림 설정
          </Link>
        </div>
      </div>

      <h2 style={{ margin: '0 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>계정/토큰 정보</h2>
      <div className="mp-cards">
        <article className="mp-trip-card mp-trip-card--past">
          <div className="mp-trip-card__meta">
            <p className="mp-trip-detail">JWT 이메일(sub): {jwtEmail ?? '—'}</p>
            <p className="mp-trip-detail">JWT 역할: {jwtRole ?? '—'}</p>
            <p className="mp-trip-detail">jti: {claims?.jti ? String(claims.jti) : '—'}</p>
            <p className="mp-trip-detail">exp: {claims?.exp ? String(claims.exp) : '—'}</p>
          </div>
          <div className="mp-trip-actions">
            <span className="mp-thumb" style={{ minHeight: '4.5rem' }}>
              Account
            </span>
            <Link to="/auth/login" className="mp-btn mp-btn--line" style={{ textDecoration: 'none', textAlign: 'center' }}>
              다른 계정으로 로그인
            </Link>
          </div>
        </article>
      </div>

      <h2 style={{ margin: '1.5rem 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>위험 구역</h2>
      <p className="sub">탈퇴는 되돌리기 어려운 동작입니다. 로컬 개발 계정에서만 테스트해 주세요.</p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem' }}>
        <button type="button" className="mp-btn mp-btn--danger" disabled={busy} onClick={() => void onWithdraw()}>
          {busy ? '처리 중…' : '회원 탈퇴(DELETE /members/me?role=GUEST)'}
        </button>
      </div>
    </div>
  )
}
