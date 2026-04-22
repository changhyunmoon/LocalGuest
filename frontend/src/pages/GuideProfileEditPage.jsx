import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { buildGuidePutBody } from '../lib/guideProfilePayload.js'
import { loadKakaoSdk } from '../lib/kakaoMapSdk.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'

const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

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
  if (typeof document === 'undefined') return null
  return createPortal(
    <div style={{ position: 'fixed', top: '1.1rem', left: '50%', transform: 'translateX(-50%)', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '0.5rem', alignItems: 'center', pointerEvents: 'none' }}>
      {toasts.map((t) => (
        <div
          key={t.id}
          style={{
            padding: '0.74rem 0.98rem',
            borderRadius: 12,
            border: t.type === 'success' ? '1px solid #e7a8c2' : '1px solid #f7b4c1',
            background: t.type === 'success' ? 'linear-gradient(180deg, #f7d9e8, #efbfd0)' : 'linear-gradient(180deg, #ffe8ee, #ffd8e1)',
            color: t.type === 'success' ? '#5a2f45' : '#9f274c',
            fontSize: '0.86rem',
            fontWeight: 700,
            boxShadow: '0 14px 28px rgba(15, 23, 42, 0.16)',
            textAlign: 'center',
            letterSpacing: '-0.01em',
            minWidth: 240,
            maxWidth: 420,
          }}
        >
          {t.message}
        </div>
      ))}
    </div>,
    document.body,
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

const LANGUAGE_OPTIONS = ['한국어', '영어', '한국어+영어', '일본어', '중국어']
const GUIDE_STYLE_OPTIONS = ['활기찬 탐험가형', '여유로운 동네 친구형', '전문 해설사형', '감성 포토 투어형', '맛집 전문 큐레이터형']

function detectRegionByCurrentLocation(kakao) {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('브라우저 위치 기능을 지원하지 않습니다.'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const geocoder = new kakao.maps.services.Geocoder()
        geocoder.coord2RegionCode(position.coords.longitude, position.coords.latitude, (result, status) => {
          if (status !== kakao.maps.services.Status.OK || !Array.isArray(result) || result.length === 0) {
            reject(new Error('현재 위치에서 지역 정보를 찾지 못했습니다.'))
            return
          }
          const hRegion = result.find((r) => r.region_type === 'H') ?? result[0]
          const city = String(hRegion?.region_2depth_name ?? '').trim()
          const province = String(hRegion?.region_1depth_name ?? '').trim()
          resolve(city || [province, String(hRegion?.region_2depth_name ?? '').trim()].filter(Boolean).join(' '))
        })
      },
      () => reject(new Error('위치 권한이 없어 현재 위치를 가져올 수 없습니다.')),
      { enableHighAccuracy: true, timeout: 10000 },
    )
  })
}

export function GuideProfileEditPage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const [profile, setProfile] = useState(null)
  const [tagInput, setTagInput] = useState('')
  const { toasts, addToast } = useToast()
  const photoInputRef = useRef(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [pendingUploadFile, setPendingUploadFile] = useState(null)
  const [imageDeleted, setImageDeleted] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [resolvingRegion, setResolvingRegion] = useState(false)

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

  useEffect(() => {
    return () => {
      if (previewUrl && previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(previewUrl)
      }
    }
  }, [previewUrl])

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
      if (pendingUploadFile && !imageDeleted) {
        const formData = new FormData()
        formData.append('file', pendingUploadFile)
        formData.append('folder', 'guide-profile')
        const uploadRes = await apiRequest('/files/upload', { method: 'POST', body: formData })
        const uploadText = await uploadRes.text()
        if (!uploadRes.ok) {
          addToast(await readJsonError(uploadRes, uploadText), 'error')
          setSaving(false)
          return
        }
        let uploadedUrl = ''
        try {
          const json = uploadText ? JSON.parse(uploadText) : {}
          uploadedUrl = String(json?.url ?? '').trim()
        } catch {
          uploadedUrl = ''
        }
        if (!uploadedUrl) {
          addToast('이미지 업로드 URL을 받지 못했습니다.', 'error')
          setSaving(false)
          return
        }
        body.profileImage = uploadedUrl
      }
      if (imageDeleted) {
        body.profileImage = null
      }
      const res = await apiRequest(`/guides/${guideId}`, { method: 'PUT', json: body })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      const updated = text ? JSON.parse(text) : profile
      setProfile(updated)
      if (previewUrl && previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(previewUrl)
      }
      setPreviewUrl(null)
      setPendingUploadFile(null)
      setImageDeleted(false)
      addToast('변경사항이 반영됐어요.')
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

  const hasFallbackLanguage = !!(profile.language && !LANGUAGE_OPTIONS.includes(String(profile.language).trim()))
  const parsedResidenceYears = Number.parseInt(String(profile.residenceYears ?? '').replace(/[^\d]/g, ''), 10)
  const residenceYearsPreview = Number.isFinite(parsedResidenceYears) ? parsedResidenceYears : 0

  return (
    <div className="g-panel">
      <div className="gpv-sheet">
      <section className="gpv-head">
        <h1>가이드 프로필 · 소개 📸</h1>
        <p>기본 정보와 소개글을 한곳에서 입력·저장할 수 있어요.</p>
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
              void (async () => {
                try {
                  const objectUrl = URL.createObjectURL(file)
                  if (previewUrl && previewUrl.startsWith('blob:')) {
                    URL.revokeObjectURL(previewUrl)
                  }
                  setPreviewUrl(objectUrl)
                  setPendingUploadFile(file)
                  setImageDeleted(false)
                } catch {
                  addToast('이미지 파일 처리에 실패했습니다.', 'error')
                }
              })()
              e.target.value = ''
            }}
          />
          <button
            type="button"
            className="gpv-photo-btn"
            onClick={() => {
              if (previewUrl && previewUrl.startsWith('blob:')) {
                URL.revokeObjectURL(previewUrl)
              }
              setImageDeleted(true)
              setPreviewUrl(null)
              setPendingUploadFile(null)
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
                setPendingUploadFile(null)
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
          <div className="gpv-region-row">
            <input
              id="gp-region"
              value={profile.region ?? ''}
              onChange={(e) => setField('region', e.target.value)}
              placeholder="예: 구미시"
            />
            <button
              type="button"
              className="gpv-photo-btn gpv-region-btn"
              disabled={resolvingRegion}
              onClick={() => {
                void (async () => {
                  setResolvingRegion(true)
                  try {
                    const kakao = await loadKakaoSdk(KAKAO_APP_KEY)
                    const city = await detectRegionByCurrentLocation(kakao)
                    if (!city) {
                      addToast('현재 위치 지역을 찾지 못했습니다. 직접 입력해 주세요.', 'error')
                      return
                    }
                    setField('region', city)
                    addToast(`현재 위치 지역(${city})으로 입력했습니다.`)
                  } catch (e) {
                    addToast(e instanceof Error ? e.message : '현재 위치 지역 입력에 실패했습니다.', 'error')
                  } finally {
                    setResolvingRegion(false)
                  }
                })()
              }}
            >
              {resolvingRegion ? '위치 확인 중…' : '현재 위치'}
            </button>
          </div>
          <p className="gm-hint gpv-region-hint">📍 버튼을 누르면 현재 위치 기준 지역이 자동으로 입력됩니다.</p>
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

        <div className="gi-cards gpv-profile-intro-cards">
          <div className="gi-card">
            <h2 className="gi-card-title">기본 소개</h2>

            <div className="gm-field">
              <label htmlFor="gp-bio">자기소개</label>
              <textarea
                id="gp-bio"
                value={profile.bio ?? ''}
                onChange={(e) => setField('bio', e.target.value)}
                rows={6}
                maxLength={4000}
                placeholder="나를 소개하는 글을 작성해주세요."
              />
            </div>

            <div className="gm-field">
              <label htmlFor="gp-story">지역 스토리</label>
              <textarea
                id="gp-story"
                value={profile.localStory ?? ''}
                onChange={(e) => setField('localStory', e.target.value)}
                rows={6}
                maxLength={4000}
                placeholder="이 지역과의 특별한 인연이나 스토리를 공유해주세요."
              />
            </div>

            <div className="gm-field">
              <label htmlFor="gp-years">
                거주 연수 <span className="gi-range-val">{residenceYearsPreview}년</span>
              </label>
              <div className="gi-years-row">
                <input
                  id="gp-years"
                  type="number"
                  min={0}
                  max={80}
                  inputMode="numeric"
                  value={profile.residenceYears != null && profile.residenceYears !== '' ? String(profile.residenceYears) : ''}
                  onChange={(e) => {
                    const next = e.target.value.replace(/[^\d]/g, '')
                    if (next === '') {
                      setField('residenceYears', '')
                      return
                    }
                    setField('residenceYears', Math.min(80, Number(next)))
                  }}
                  className="gi-years-input"
                  placeholder="거주 연수"
                />
                <span className="gi-years-suffix">년</span>
              </div>
            </div>
          </div>
        </div>

        <div className="gpv-mini-grid">
          <div className="gm-field">
            <label htmlFor="gp-lang">기본 언어</label>
            <select id="gp-lang" value={profile.language ?? ''} onChange={(e) => setField('language', e.target.value)}>
              {hasFallbackLanguage && (
                <option value={profile.language}>기존값 유지 ({profile.language})</option>
              )}
              <option value="">선택 안 함</option>
              <option value="한국어">한국어</option>
              <option value="영어">영어</option>
              <option value="한국어+영어">한국어+영어</option>
              <option value="일본어">일본어</option>
              <option value="중국어">중국어</option>
            </select>
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

        <div className="gi-cards gpv-profile-intro-cards">
          <div className="gi-card">
            <h2 className="gi-card-title">가이드 스타일 &amp; AI 매칭 설정</h2>

            <div className="gm-field">
              <label>가이드 스타일</label>
              <div className="gi-chip-list">
                {GUIDE_STYLE_OPTIONS.map((chip) => (
                  <button
                    key={chip}
                    type="button"
                    className={`gi-chip${profile.guideStyle === chip ? ' is-on' : ''}`}
                    onClick={() => {
                      const g = profile.guideStyle ?? ''
                      setField('guideStyle', g === chip ? '' : chip)
                    }}
                  >
                    {chip}
                  </button>
                ))}
              </div>
              <div className="gi-tag-row" style={{ marginTop: '0.5rem' }}>
                <input
                  className="gi-tag-input"
                  value={GUIDE_STYLE_OPTIONS.includes(String(profile.guideStyle ?? '').trim()) ? '' : (profile.guideStyle ?? '')}
                  onChange={(e) => setField('guideStyle', e.target.value)}
                  placeholder="직접 입력 (선택 옵션 외)"
                />
              </div>
            </div>

            <div className="gm-field">
              <label htmlFor="gp-course">기본 코스</label>
              <textarea
                id="gp-course"
                value={profile.defaultCourse ?? ''}
                onChange={(e) => setField('defaultCourse', e.target.value)}
                rows={2}
                placeholder="주로 안내하는 코스 흐름 예: 남산 → 이태원 → 해방촌"
              />
              <p className="gm-hint">매칭된 여행자에게 첫 코스 제안으로 사용됩니다</p>
            </div>
          </div>
        </div>

        <div className="gpv-save-row">
          <button type="button" className="gpv-save" onClick={() => void handleSave()} disabled={saving}>
            {saving ? '저장 중…' : '저장하기'}
          </button>
        </div>
      </div>
      </div>
      <Toast toasts={toasts} />
    </div>
  )
}
