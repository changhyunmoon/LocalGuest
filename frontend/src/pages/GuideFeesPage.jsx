import { useCallback, useEffect, useState } from 'react'

import { apiRequest } from '../api/client'
import { resolveGuideId } from '../lib/guideId.js'

import '../layouts/GuideDashboardLayout.css'

const HOURS = 8
const LS_PUSH = 'guide_pref_push_notif'
const LS_EMAIL = 'guide_pref_email_notif'

function loadBool(key, fallback) {
  const v = localStorage.getItem(key)
  if (v === '1') return true
  if (v === '0') return false
  return fallback
}

export function GuideFeesPage() {
  const [guideId, setGuideId] = useState(null)
  const [profile, setProfile] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const [pushOn, setPushOn] = useState(() => loadBool(LS_PUSH, true))
  const [emailOn, setEmailOn] = useState(() => loadBool(LS_EMAIL, false))
  const [profileVisible, setProfileVisible] = useState(true)
  const [packageWon, setPackageWon] = useState('')

  const loadProfile = useCallback(async (id) => {
    setLoadError('')
    try {
      const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) {
        throw new Error(text || '프로필을 불러오지 못했습니다.')
      }
      const p = text ? JSON.parse(text) : null
      setProfile(p)
      setProfileVisible(p?.isActive !== false)
      const hourly = p?.pricePerHour != null ? Number(p.pricePerHour) : 0
      setPackageWon(hourly > 0 ? String(Math.round(hourly * HOURS)) : '')
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '오류')
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const id = await resolveGuideId(apiRequest)
      if (cancelled) return
      if (!id) {
        setLoadError(
          '가이드 프로필 ID를 찾을 수 없습니다. 가이드 신청 직후에는 브라우저에 저장된 ID가 필요하고, 또는 예약 요청 목록에서 추론합니다.',
        )
        return
      }
      setGuideId(id)
      await loadProfile(id)
    })()
    return () => {
      cancelled = true
    }
  }, [loadProfile])

  const savePrefs = () => {
    localStorage.setItem(LS_PUSH, pushOn ? '1' : '0')
    localStorage.setItem(LS_EMAIL, emailOn ? '1' : '0')
  }

  const handleSave = async () => {
    if (!guideId || !profile) return
    setError('')
    setSaving(true)
    savePrefs()
    try {
      const pkg = Number(packageWon.replace(/[^\d]/g, ''))
      if (!Number.isFinite(pkg) || pkg <= 0) {
        setError('종일 패키지 금액을 올바르게 입력해 주세요.')
        setSaving(false)
        return
      }
      const hourly = Math.max(1, Math.round(pkg / HOURS))
      const body = {
        nickname: profile.nickname,
        profileImage: profile.profileImage ?? undefined,
        bio: profile.bio ?? undefined,
        region: profile.region,
        language: profile.language,
        pricePerHour: hourly,
        isActive: profileVisible,
        residenceYears: profile.residenceYears ?? undefined,
        localStory: profile.localStory ?? undefined,
        keywords: profile.keywords ?? undefined,
        defaultCourse: profile.defaultCourse ?? undefined,
        guideStyle: profile.guideStyle ?? undefined,
      }
      const res = await apiRequest(`/guides/${guideId}`, { method: 'PUT', json: body })
      const text = await res.text()
      if (!res.ok) {
        try {
          const j = JSON.parse(text)
          setError(j.message ?? '저장에 실패했습니다.')
        } catch {
          setError(text || '저장에 실패했습니다.')
        }
        return
      }
      const updated = text ? JSON.parse(text) : null
      setProfile(updated)
    } catch {
      setError('네트워크 오류가 발생했습니다.')
    } finally {
      setSaving(false)
    }
  }

  if (loadError) {
    return (
      <div className="g-panel">
        <p className="g-error">{loadError}</p>
        <p className="g-hint">가이드 신청이 완료된 계정에서 다시 시도해 주세요.</p>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="g-panel">
        <p>불러오는 중…</p>
      </div>
    )
  }

  return (
    <div className="g-panel">
      <h1>⚙️ 가이드 비용</h1>

      {error && <p className="g-error">{error}</p>}

      <section className="g-section">
        <h2>알림 설정</h2>
        <div className="g-row">
          <div>
            <strong>새로운 예약 요청 알림 (Push)</strong>
            <p>푸시 알림 API 연동 전 — 이 기기에만 저장됩니다.</p>
          </div>
          <label className="g-toggle">
            <input type="checkbox" checked={pushOn} onChange={(e) => setPushOn(e.target.checked)} />
            <span className="g-toggle-ui" />
          </label>
        </div>
        <div className="g-row">
          <div>
            <strong>메시지 수신 알림 (Email)</strong>
            <p>이메일 알림 API 연동 전 — 이 기기에만 저장됩니다.</p>
          </div>
          <label className="g-toggle">
            <input type="checkbox" checked={emailOn} onChange={(e) => setEmailOn(e.target.checked)} />
            <span className="g-toggle-ui" />
          </label>
        </div>
      </section>

      <section className="g-section">
        <h2>프로필 공개 설정</h2>
        <div className="g-row">
          <div>
            <strong>여행자에게 내 프로필 노출하기</strong>
            <p>서버 필드 <code>isActive</code> 와 연동됩니다.</p>
          </div>
          <label className="g-toggle">
            <input type="checkbox" checked={profileVisible} onChange={(e) => setProfileVisible(e.target.checked)} />
            <span className="g-toggle-ui" />
          </label>
        </div>
      </section>

      <section className="g-section">
        <h2>가이드 비용 설정</h2>
        <p className="g-hint">여행자에게 노출될 종일 가이드 비용을 설정하세요. (서버는 시간당 단가만 저장 — 입력 금액을 8시간으로 나눈 값이 저장됩니다.)</p>
        <div className="g-price-row">
          <label htmlFor="pkg-won">종일 패키지</label>
          <input
            id="pkg-won"
            inputMode="numeric"
            value={packageWon}
            onChange={(e) => setPackageWon(e.target.value.replace(/[^\d]/g, ''))}
            placeholder="140000"
          />
          <span style={{ fontWeight: 600, color: '#374151' }}>원</span>
        </div>
      </section>

      <div className="g-save-row">
        <button type="button" className="g-save" onClick={() => void handleSave()} disabled={saving}>
          {saving ? '저장 중…' : '비용 저장'}
        </button>
      </div>
    </div>
  )
}
