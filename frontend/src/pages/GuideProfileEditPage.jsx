import { useCallback, useEffect, useRef, useState } from 'react'

import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { buildGuidePutBody } from '../lib/guideProfilePayload.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'

function useToast() {
  const [toasts, setToasts] = useState([])
  const addToast = useCallback((message, type = 'success') => {
    const id = Date.now() + Math.random()
    setToasts((prev) => [...prev, { id, message, type }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 2000)
  }, [])
  return { toasts, addToast }
}

function Toast({ toasts }) {
  if (toasts.length === 0) return null
  return (
    <div style={{ position: 'fixed', bottom: '1.5rem', right: '1.5rem', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '0.5rem', pointerEvents: 'none' }}>
      {toasts.map((t) => (
        <div key={t.id} style={{ padding: '0.7rem 1.2rem', borderRadius: 10, background: t.type === 'success' ? '#15803d' : '#b91c1c', color: '#fff', fontSize: '0.88rem', fontWeight: 600, boxShadow: '0 4px 16px rgba(0,0,0,0.18)', minWidth: 180, maxWidth: 320 }}>
          {t.message}
        </div>
      ))}
    </div>
  )
}

async function readJsonError(res, text) {
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

export function GuideProfileEditPage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const [profile, setProfile] = useState(null)
  const [tagInput, setTagInput] = useState('')
  const { toasts, addToast } = useToast()
  const photoInputRef = useRef(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [imageDeleted, setImageDeleted] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async (id) => {
    setLoadError('')
    try {
      const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) {
        throw new Error(await readJsonError(res, text))
      }
      setProfile(text ? JSON.parse(text) : null)
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '불러오기 실패')
    }
  }, [])

  useEffect(() => {
    if (!guideId) return
    void load(guideId)
  }, [guideId, load])

  const setField = (key, value) => {
    setProfile((p) => (p ? { ...p, [key]: value } : p))
  }

  const tags =
    profile?.keywords && String(profile.keywords).trim()
      ? String(profile.keywords)
          .split(/[,#\s]+/)
          .map((t) => t.trim())
          .filter(Boolean)
      : []

  const setTags = (nextTags) => {
    const unique = [...new Set(nextTags.map((t) => t.trim()).filter(Boolean))].slice(0, 3)
    setField('keywords', unique.join(', '))
  }

  const addTag = () => {
    const t = tagInput.trim()
    if (!t) return
    setTags([...tags, t])
    setTagInput('')
  }

  const removeTag = (t) => {
    setTags(tags.filter((v) => v !== t))
  }

  const handleSave = async () => {
    if (!guideId || !profile) return
    setSaving(true)
    try {
      const body = buildGuidePutBody(profile)
      if (!body.nickname || !body.region || !body.language) {
        addToast('닉네임, 활동 지역, 언어는 필수입니다.', 'error')
        setSaving(false)
        return
      }
      if (!body.pricePerHour || body.pricePerHour <= 0) {
        addToast('시간당 가격은 0보다 커야 합니다. (비용 메뉴에서 종일 패키지로 설정할 수 있습니다.)', 'error')
        setSaving(false)
        return
      }
      if (imageDeleted) {
        body.profileImage = null
      } else if (previewUrl) {
        body.profileImage = profile.profileImage ?? undefined
      }
      const res = await apiRequest(`/guides/${guideId}`, { method: 'PUT', json: body })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      addToast('저장되었습니다.')
      window.location.reload()
    } catch {
      addToast('네트워크 오류', 'error')
    } finally {
      setSaving(false)
    }
  }

  if (idLoading) {
    return (
      <div className="g-panel">
        <p>불러오는 중…</p>
      </div>
    )
  }

  if (idError) {
    return (
      <div className="g-panel">
        <p className="g-error">{idError}</p>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="g-panel">
        <p className="g-error">{loadError}</p>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="g-panel">
        <p>프로필을 불러올 수 없습니다.</p>
      </div>
    )
  }

  return (
    <div className="g-panel">
      <section className="gpv-head">
        <h1>가이드 프로필 등록 📸</h1>
        <p>여행자들에게 보일 기본 정보를 입력해주세요.</p>
      </section>
      <div className="gpv-profile-row">
        <div
          className="gpv-photo"
          style={(() => {
            const img = imageDeleted ? null : (previewUrl ?? profile.profileImage ?? null)
            return img ? { backgroundImage: `url(${img})` } : undefined
          })()}
        />
        <div className="gpv-photo-actions">
          <button type="button" className="gpv-photo-btn" onClick={() => photoInputRef.current?.click()}>
            사진 변경
          </button>
          <input
            ref={photoInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (!file) return
              setPreviewUrl(URL.createObjectURL(file))
              setImageDeleted(false)
              e.target.value = ''
            }}
          />
          <button
            type="button"
            className="gpv-photo-btn"
            onClick={() => {
              setImageDeleted(true)
              setPreviewUrl(null)
              if (photoInputRef.current) photoInputRef.current.value = ''
            }}
          >
            사진 삭제
          </button>
          <details className="gpv-photo-url-box">
            <summary>이미지 URL 직접 입력</summary>
            <input
              id="gp-img"
              className="gpv-photo-input"
              value={imageDeleted ? '' : (previewUrl ? '' : (profile.profileImage ?? ''))}
              onChange={(e) => {
                setField('profileImage', e.target.value)
                setPreviewUrl(null)
                setImageDeleted(false)
              }}
              placeholder="https://image-url"
            />
          </details>
        </div>
      </div>

      <div className="gpv-form">
        <div className="gpv-field">
          <label htmlFor="gp-nick">활동 닉네임</label>
          <input id="gp-nick" value={profile.nickname ?? ''} onChange={(e) => setField('nickname', e.target.value)} />
        </div>

        <div className="gpv-field">
          <label htmlFor="gp-region">주 활동 지역</label>
          <select id="gp-region" value={profile.region ?? ''} onChange={(e) => setField('region', e.target.value)}>
            {profile.region && !['제주특별자치도 제주시', '제주특별자치도 서귀포시', '부산광역시', '강원특별자치도 강릉시', '전라남도 여수시'].includes(profile.region) && (
              <option value={profile.region}>{profile.region}</option>
            )}
            <option value="">지역 선택</option>
            <option value="제주특별자치도 제주시">제주특별자치도 제주시</option>
            <option value="제주특별자치도 서귀포시">제주특별자치도 서귀포시</option>
            <option value="부산광역시">부산광역시</option>
            <option value="강원특별자치도 강릉시">강원특별자치도 강릉시</option>
            <option value="전라남도 여수시">전라남도 여수시</option>
          </select>
        </div>

        <div className="gpv-field">
          <label htmlFor="gp-tag-input">전문 분야 태그 (최대 3개)</label>
          <input
            id="gp-tag-input"
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                addTag()
              }
            }}
            placeholder="태그를 입력하고 엔터를 누르세요"
          />
          <div className="gpv-tag-list">
            {tags.map((t) => (
              <button key={t} type="button" className="gpv-tag" onClick={() => removeTag(t)} title="태그 제거">
                #{t}
              </button>
            ))}
          </div>
        </div>

        <div className="gpv-mini-grid">
          <div className="gpv-field">
            <label htmlFor="gp-lang">기본 언어</label>
            <input
              id="gp-lang"
              value={profile.language ?? ''}
              onChange={(e) => setField('language', e.target.value)}
              placeholder="한국어, English"
            />
          </div>
          <div className="gpv-field">
            <label htmlFor="gp-price">시간당 가격(원)</label>
            <input
              id="gp-price"
              inputMode="numeric"
              value={profile.pricePerHour ?? ''}
              onChange={(e) => setField('pricePerHour', e.target.value.replace(/[^\d]/g, ''))}
            />
          </div>
        </div>

        <div className="gpv-field">
          <label htmlFor="gp-course">기본 코스</label>
          <input
            id="gp-course"
            value={profile.defaultCourse ?? ''}
            onChange={(e) => setField('defaultCourse', e.target.value)}
            placeholder="예: 골목 투어 → 전통 시장 → 야경"
          />
        </div>

        <div className="gpv-field">
          <label htmlFor="gp-style">가이드 스타일</label>
          <input
            id="gp-style"
            value={profile.guideStyle ?? ''}
            onChange={(e) => setField('guideStyle', e.target.value)}
            placeholder="예: 편안하고 친근한 동네 산책형"
          />
        </div>

        <div className="gpv-save-row">
          <button type="button" className="gpv-save" onClick={() => void handleSave()} disabled={saving}>
            {saving ? '저장 중…' : '저장하기'}
          </button>
        </div>
      </div>
      <Toast toasts={toasts} />
    </div>
  )
}
