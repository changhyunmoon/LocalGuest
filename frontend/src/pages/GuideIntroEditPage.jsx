import { useCallback, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

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

export function GuideIntroEditPage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const [profile, setProfile] = useState(null)
  const [bio, setBio] = useState('')
  const [localStory, setLocalStory] = useState('')
  const [residenceYears, setResidenceYears] = useState('0')
  const [language, setLanguage] = useState('')
  const [guideStyle, setGuideStyle] = useState('')
  const [defaultCourse, setDefaultCourse] = useState('')
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const { toasts, addToast } = useToast()

  const parsedResidenceYears = Number.parseInt(String(residenceYears || '').replace(/[^\d]/g, ''), 10)
  const residenceYearsPreview = Number.isFinite(parsedResidenceYears) ? parsedResidenceYears : 0

  const load = useCallback(async (id) => {
    setLoadError('')
    try {
      const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) {
        throw new Error(await readJsonError(res, text))
      }
      const p = text ? JSON.parse(text) : null
      setProfile(p)
      setBio(p?.bio ?? '')
      setLocalStory(p?.localStory ?? '')
      setResidenceYears(p?.residenceYears != null ? String(p.residenceYears) : '0')
      setLanguage(p?.language ?? '')
      setGuideStyle(p?.guideStyle ?? '')
      setDefaultCourse(p?.defaultCourse ?? '')
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '불러오기 실패')
    }
  }, [])

  useEffect(() => {
    if (!guideId) return
    void load(guideId)
  }, [guideId, load])

  const handleSave = async () => {
    if (!guideId || !profile) return
    setSaving(true)
    try {
      const merged = {
        ...profile,
        bio,
        localStory,
        residenceYears: residenceYears !== '' ? Number(residenceYears) : undefined,
        language,
        guideStyle: guideStyle,
        defaultCourse,
      }
      const body = buildGuidePutBody(merged)
      const res = await apiRequest(`/guides/${guideId}`, { method: 'PUT', json: body })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      const p = text ? JSON.parse(text) : merged
      setProfile(p)
      setBio(p?.bio ?? '')
      setLocalStory(p?.localStory ?? '')
      setResidenceYears(p?.residenceYears != null ? String(p.residenceYears) : '0')
      setLanguage(p?.language ?? '')
      setGuideStyle(p?.guideStyle ?? '')
      setDefaultCourse(p?.defaultCourse ?? '')
      addToast('변경사항이 반영됐어요.')
    } catch {
      addToast('네트워크 오류', 'error')
    } finally {
      setSaving(false)
    }
  }

  const hasFallbackLanguage = !!(profile?.language && !LANGUAGE_OPTIONS.includes(profile.language))

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

  if (loadError || !profile) {
    return (
      <div className="g-panel">
        <p className="g-error">{loadError || '프로필을 불러올 수 없습니다.'}</p>
      </div>
    )
  }

  return (
    <div className="g-panel">
      <h1>✏️ 소개글 작성</h1>

      <div className="gi-cards">
        {/* 카드 1 — 기본 소개 */}
        <div className="gi-card">
          <h2 className="gi-card-title">기본 소개</h2>

          <div className="gm-field">
            <label htmlFor="gi-bio">자기소개</label>
            <textarea
              id="gi-bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              rows={6}
              maxLength={4000}
              placeholder="나를 소개하는 글을 작성해주세요."
            />
          </div>

          <div className="gm-field">
            <label htmlFor="gi-story">지역 스토리</label>
            <textarea
              id="gi-story"
              value={localStory}
              onChange={(e) => setLocalStory(e.target.value)}
              rows={6}
              maxLength={4000}
              placeholder="이 지역과의 특별한 인연이나 스토리를 공유해주세요."
            />
          </div>

          <div className="gm-field">
            <label htmlFor="gi-years">
              거주 연수 <span className="gi-range-val">{residenceYearsPreview}년</span>
            </label>
            <div className="gi-years-row">
              <input
                id="gi-years"
                type="number"
                min={0}
                max={80}
                inputMode="numeric"
                value={residenceYears}
                onChange={(e) => {
                  const next = e.target.value.replace(/[^\d]/g, '')
                  if (next === '') {
                    setResidenceYears('')
                    return
                  }
                  setResidenceYears(String(Math.min(80, Number(next))))
                }}
                className="gi-years-input"
                placeholder="거주 연수"
              />
              <span className="gi-years-suffix">년</span>
            </div>
          </div>

          <div className="gm-field">
            <label htmlFor="gi-lang">주 사용 언어</label>
            <select id="gi-lang" value={language} onChange={(e) => setLanguage(e.target.value)}>
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
        </div>

        {/* 카드 2 — 가이드 스타일 & AI 매칭 설정 */}
        <div className="gi-card">
          <h2 className="gi-card-title">가이드 스타일 &amp; AI 매칭 설정</h2>

          <div className="gm-field">
            <label>가이드 스타일</label>
            <div className="gi-chip-list">
              {GUIDE_STYLE_OPTIONS.map((chip) => (
                <button
                  key={chip}
                  type="button"
                  className={`gi-chip${guideStyle === chip ? ' is-on' : ''}`}
                  onClick={() => setGuideStyle((prev) => (prev === chip ? '' : chip))}
                >
                  {chip}
                </button>
              ))}
            </div>
            <div className="gi-tag-row" style={{ marginTop: '0.5rem' }}>
              <input
                className="gi-tag-input"
                value={GUIDE_STYLE_OPTIONS.includes(guideStyle) ? '' : guideStyle}
                onChange={(e) => setGuideStyle(e.target.value)}
                placeholder="직접 입력 (선택 옵션 외)"
              />
            </div>
          </div>

          <div className="gm-field">
            <label htmlFor="gi-course">기본 코스</label>
            <textarea
              id="gi-course"
              value={defaultCourse}
              onChange={(e) => setDefaultCourse(e.target.value)}
              rows={2}
              placeholder="주로 안내하는 코스 흐름 예: 남산 → 이태원 → 해방촌"
            />
            <p className="gm-hint">매칭된 여행자에게 첫 코스 제안으로 사용됩니다</p>
          </div>
        </div>
      </div>

      <div className="gi-save-row">
        <button type="button" className="gm-btn" onClick={() => void handleSave()} disabled={saving}>
          {saving ? '저장 중…' : '저장하기'}
        </button>
      </div>

      <Toast toasts={toasts} />
    </div>
  )
}
