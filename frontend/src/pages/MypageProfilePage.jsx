import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { useAuth } from '../context/useAuth.js'
import { getGuestDisplayName, loadGuestPrivacyForm } from '../lib/guestMypagePrefs.js'
import { getEmailFromToken, getRoleFromToken, parseJwtPayload } from '../lib/jwt.js'

import './MypageMemberPages.css'

export function MypageProfilePage() {
  const { email, token } = useAuth()

  const claims = useMemo(() => parseJwtPayload(token ?? ''), [token])
  const jwtEmail = useMemo(() => (token ? getEmailFromToken(token) : null), [token])
  const jwtRole = useMemo(() => (token ? getRoleFromToken(token) : null), [token])

  const [nickname, setNickname] = useState('')

  useEffect(() => {
    const f = loadGuestPrivacyForm(email)
    setNickname(f.nickname ?? '')
  }, [email])

  const displayName = useMemo(() => getGuestDisplayName(email), [email, nickname])

  return (
    <div className="mp-member">
      <h1>👤 프로필</h1>
      <p className="sub">
        계정 이메일·역할은 로그인 토큰 기준이며, 표시 이름 등은 <strong>개인정보 설정(/mypage/privacy)</strong>에 저장한 값을
        함께 씁니다. 서버에 게스트 프로필이 없을 때 닉네임·알림 설정은 <strong>이 브라우저(로컬)에만</strong> 남습니다.
      </p>
      <MypageDevHint>
        회원 API: <code>POST /members/join</code>, <code>DELETE /members/me?role=...</code> · 표시는 JWT + 로컬 prefs
      </MypageDevHint>

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
    </div>
  )
}
