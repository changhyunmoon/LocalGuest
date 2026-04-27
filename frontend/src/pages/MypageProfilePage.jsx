import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { useAuth } from '../context/useAuth.js'
import { getGuestDisplayName, loadGuestPrivacyForm, loadTravelTags } from '../lib/guestMypagePrefs.js'
import { getEmailFromToken, getRoleFromToken, parseJwtPayload } from '../lib/jwt.js'
import { buildTravelDnaPreview, loadTravelDna } from '../lib/travelDna.js'

import './MypageMemberPages.css'

export function MypageProfilePage() {
  const { email, token } = useAuth()
  const photoInputRef = useRef(null)

  const claims = useMemo(() => parseJwtPayload(token ?? ''), [token])
  const jwtEmail = useMemo(() => (token ? getEmailFromToken(token) : null), [token])
  const jwtRole = useMemo(() => (token ? getRoleFromToken(token) : null), [token])

  const [nickname, setNickname] = useState('')
  const [travelTags, setTravelTags] = useState([])
  const [profileImageUrl, setProfileImageUrl] = useState(null)
  const [imageBusy, setImageBusy] = useState(false)
  const [imageError, setImageError] = useState('')

  useEffect(() => {
    const f = loadGuestPrivacyForm(email)
    setNickname(f.nickname ?? '')
    setTravelTags(loadTravelTags())
  }, [email])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!token) {
        if (!cancelled) setProfileImageUrl(null)
        return
      }
      try {
        const res = await apiRequest('/members/me/profile', { method: 'GET' })
        const text = await res.text()
        if (!res.ok) {
          throw new Error(text || '프로필 이미지를 불러오지 못했습니다.')
        }
        const json = text ? JSON.parse(text) : {}
        if (!cancelled) setProfileImageUrl(json?.profileImageUrl ? String(json.profileImageUrl) : null)
      } catch (e) {
        if (!cancelled) {
          setProfileImageUrl(null)
          setImageError(e instanceof Error ? e.message : '프로필 이미지를 불러오지 못했습니다.')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token])

  const uploadGuestProfileImage = async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', 'guest-profile')
    const uploadRes = await apiRequest('/files/upload', { method: 'POST', body: formData })
    const uploadText = await uploadRes.text()
    if (!uploadRes.ok) throw new Error(uploadText || '이미지 업로드에 실패했습니다.')
    const uploadJson = uploadText ? JSON.parse(uploadText) : {}
    const uploadedUrl = String(uploadJson?.url ?? '').trim()
    if (!uploadedUrl) throw new Error('이미지 업로드 URL을 받지 못했습니다.')

    const saveRes = await apiRequest('/members/me/profile', {
      method: 'PUT',
      json: { profileImageUrl: uploadedUrl },
    })
    const saveText = await saveRes.text()
    if (!saveRes.ok) throw new Error(saveText || '프로필 이미지 저장에 실패했습니다.')
    const saveJson = saveText ? JSON.parse(saveText) : {}
    return saveJson?.profileImageUrl ? String(saveJson.profileImageUrl) : uploadedUrl
  }

  const clearGuestProfileImage = async () => {
    const saveRes = await apiRequest('/members/me/profile', {
      method: 'PUT',
      json: { profileImageUrl: '' },
    })
    const saveText = await saveRes.text()
    if (!saveRes.ok) throw new Error(saveText || '프로필 이미지 삭제에 실패했습니다.')
    return null
  }

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
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', alignItems: 'flex-end' }}>
          <div
            aria-label="게스트 프로필 이미지"
            style={{
              width: '4.3rem',
              height: '4.3rem',
              borderRadius: '50%',
              border: '1px solid #f0d8e5',
              backgroundColor: '#fff',
              backgroundSize: 'cover',
              backgroundPosition: 'center',
              backgroundImage: profileImageUrl ? `url(${profileImageUrl})` : 'none',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#7a4f68',
              fontWeight: 700,
              fontSize: '0.82rem',
            }}
          >
            {!profileImageUrl ? 'No Img' : ''}
          </div>
          <input
            ref={photoInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (!file) return
              if (!file.type.startsWith('image/')) {
                setImageError('이미지 파일만 업로드할 수 있습니다.')
                return
              }
              void (async () => {
                setImageBusy(true)
                setImageError('')
                try {
                  const savedUrl = await uploadGuestProfileImage(file)
                  setProfileImageUrl(savedUrl)
                } catch (err) {
                  setImageError(err instanceof Error ? err.message : '프로필 이미지 업로드에 실패했습니다.')
                } finally {
                  setImageBusy(false)
                }
              })()
            }}
          />
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem', justifyContent: 'flex-end' }}>
            <button type="button" className="mp-btn mp-btn--line" disabled={imageBusy} onClick={() => photoInputRef.current?.click()}>
              {imageBusy ? '업로드 중…' : '프로필 사진 등록'}
            </button>
            <button
              type="button"
              className="mp-btn mp-btn--line"
              disabled={imageBusy || !profileImageUrl}
              onClick={() => {
                void (async () => {
                  setImageBusy(true)
                  setImageError('')
                  try {
                    await clearGuestProfileImage()
                    setProfileImageUrl(null)
                  } catch (err) {
                    setImageError(err instanceof Error ? err.message : '프로필 이미지 삭제에 실패했습니다.')
                  } finally {
                    setImageBusy(false)
                  }
                })()
              }}
            >
              사진 삭제
            </button>
          </div>
          <Link to="/mypage/privacy" className="mp-btn" style={{ textDecoration: 'none' }}>
            개인정보/알림 설정
          </Link>
        </div>
      </div>
      {imageError && (
        <p className="err" style={{ marginTop: '-0.25rem', marginBottom: '0.85rem' }}>
          {imageError}
        </p>
      )}

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
