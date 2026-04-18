import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

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

export function GuideIntroEditPage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const [profile, setProfile] = useState(null)
  const [bio, setBio] = useState('')
  const [localStory, setLocalStory] = useState('')
  const [residenceYears, setResidenceYears] = useState('')
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const { toasts, addToast } = useToast()

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
      setResidenceYears(p?.residenceYears != null ? String(p.residenceYears) : '')
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
      const merged = { ...profile, bio, localStory, residenceYears: residenceYears ? Number(residenceYears) : undefined }
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
      setResidenceYears(p?.residenceYears != null ? String(p.residenceYears) : '')
      addToast('저장되었습니다.')
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
      <p className="g-hint">
        <code>PUT /api/guides/&#123;guideId&#125;</code> 의 <code>bio</code>, <code>localStory</code> 필드입니다. 닉네임·
        지역 등은 <Link to="/guide/mypage/profile">프로필 등록</Link>에서 수정합니다.
      </p>

      <div className="gm-stack" style={{ marginTop: '0.75rem', maxWidth: '40rem' }}>
        <div className="gm-field">
          <label htmlFor="gi-bio">자기소개 (bio)</label>
          <textarea id="gi-bio" value={bio} onChange={(e) => setBio(e.target.value)} rows={8} maxLength={4000} />
        </div>
        <div className="gm-field">
          <label htmlFor="gi-story">지역 스토리 (localStory)</label>
          <textarea
            id="gi-story"
            value={localStory}
            onChange={(e) => setLocalStory(e.target.value)}
            rows={8}
            maxLength={4000}
          />
        </div>
        <div className="gm-field">
          <label htmlFor="gi-years">거주 연수 (residenceYears)</label>
          <input
            id="gi-years"
            type="number"
            min={0}
            value={residenceYears}
            onChange={(e) => setResidenceYears(e.target.value.replace(/[^\d]/g, ''))}
            placeholder="예: 15"
          />
        </div>
        <div className="gm-actions">
          <button type="button" className="gm-btn" onClick={() => void handleSave()} disabled={saving}>
            {saving ? '저장 중…' : '소개글 저장'}
          </button>
        </div>
      </div>
      <Toast toasts={toasts} />
    </div>
  )
}
