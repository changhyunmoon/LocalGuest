import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { useAuth } from '../context/useAuth.js'
import { getGuestDisplayName, loadGuestPrivacyForm, loadTravelTags } from '../lib/guestMypagePrefs.js'
import { getEmailFromToken, getRoleFromToken, parseJwtPayload } from '../lib/jwt.js'
import { buildTravelDnaPreview, loadTravelDna } from '../lib/travelDna.js'

import './MypageMemberPages.css'

export function MypageProfilePage() {
  const { email, token } = useAuth()

  const claims = useMemo(() => parseJwtPayload(token ?? ''), [token])
  const jwtEmail = useMemo(() => (token ? getEmailFromToken(token) : null), [token])
  const jwtRole = useMemo(() => (token ? getRoleFromToken(token) : null), [token])

  const [nickname, setNickname] = useState('')
  const [travelTags, setTravelTags] = useState([])

  useEffect(() => {
    const f = loadGuestPrivacyForm(email)
    setNickname(f.nickname ?? '')
    setTravelTags(loadTravelTags())
  }, [email])

  const displayName = useMemo(() => getGuestDisplayName(email), [email, nickname])
  const travelDnaPreview = useMemo(() => buildTravelDnaPreview(loadTravelDna()), [])

  return (
    <div className="mp-member">
      <h1>👤 프로필</h1>
      <p className="sub">내 계정 정보와 여행 성향 태그를 확인할 수 있어요.</p>
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

      <h2 style={{ margin: '1.1rem 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>내 여행 성향 태그</h2>
      <div className="mp-scrap-hero" style={{ marginBottom: '0.75rem' }}>
        {travelTags.length > 0 ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem' }}>
            {travelTags.map((tag) => (
              <span
                key={tag}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  border: '1px solid #efbfd0',
                  borderRadius: '999px',
                  padding: '0.22rem 0.58rem',
                  background: '#fff7fb',
                  color: '#7a4f68',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                }}
              >
                #{tag}
              </span>
            ))}
          </div>
        ) : (
          <p style={{ margin: 0, color: '#5f5266', lineHeight: 1.55 }}>
            {travelDnaPreview || '아직 저장된 여행 성향이 없습니다.'}
          </p>
        )}
      </div>
    </div>
  )
}
