import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createPortal } from 'react-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'

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

const HOURS = 8
const LS_PUSH = 'guide_pref_push_notif'
const LS_EMAIL = 'guide_pref_email_notif'

function loadBool(key, fallback) {
  const v = localStorage.getItem(key)
  if (v === '1') return true
  if (v === '0') return false
  return fallback
}

export function GuideSettingsPage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const navigate = useNavigate()
  const { toasts, addToast } = useToast()
  const [profile, setProfile] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [busy, setBusy] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [pushOn, setPushOn] = useState(() => loadBool(LS_PUSH, true))
  const [emailOn, setEmailOn] = useState(() => loadBool(LS_EMAIL, false))
  const [packageWon, setPackageWon] = useState('')
  const [deactivateConfirmOpen, setDeactivateConfirmOpen] = useState(false)

  const load = useCallback(async (id) => {
    setLoadError('')
    try {
      const pr = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
      const pt = await pr.text()
      if (!pr.ok) {
        throw new Error(await readJsonError(pr, pt))
      }
      setProfile(pt ? JSON.parse(pt) : null)
      const loadedProfile = pt ? JSON.parse(pt) : null
      const hourly = loadedProfile?.pricePerHour != null ? Number(loadedProfile.pricePerHour) : 0
      setPackageWon(hourly > 0 ? String(Math.round(hourly * HOURS)) : '')
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '불러오기 실패')
    }
  }, [])

  useEffect(() => {
    if (!guideId) return
    void load(guideId)
  }, [guideId, load])

  const toggleActive = async () => {
    if (!guideId) return
    setBusy(true)
    try {
      const res = await apiRequest(`/guides/${guideId}/active`, { method: 'PATCH' })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      const next = text ? JSON.parse(text) : profile
      setProfile(next)
      addToast(next?.isActive ? '활성화되었습니다.' : '비활성화되어 게스트에게 보이지 않습니다.')
    } catch {
      addToast('요청 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const handleWithdraw = async () => {
    if (!window.confirm('가이드 계정을 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return
    setWithdrawing(true)
    try {
      const res = await apiRequest('/members/me?role=GUIDE', { method: 'DELETE' })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      navigate('/auth/login', { replace: true })
    } catch {
      addToast('탈퇴 요청 실패', 'error')
    } finally {
      setWithdrawing(false)
    }
  }

  const savePrefs = () => {
    localStorage.setItem(LS_PUSH, pushOn ? '1' : '0')
    localStorage.setItem(LS_EMAIL, emailOn ? '1' : '0')
  }

  const handleSaveFees = async () => {
    if (!guideId || !profile) return
    setSaving(true)
    savePrefs()
    try {
      const pkg = Number(packageWon.replace(/[^\d]/g, ''))
      if (!Number.isFinite(pkg) || pkg <= 0) {
        addToast('종일 패키지 금액을 올바르게 입력해 주세요.', 'error')
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
        isActive: profile?.isActive ?? true,
        residenceYears: profile.residenceYears ?? undefined,
        localStory: profile.localStory ?? undefined,
        keywords: profile.keywords ?? undefined,
        defaultCourse: profile.defaultCourse ?? undefined,
        guideStyle: profile.guideStyle ?? undefined,
      }
      const res = await apiRequest(`/guides/${guideId}`, { method: 'PUT', json: body })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      setProfile(text ? JSON.parse(text) : profile)
      addToast('설정이 반영됐어요.')
    } catch {
      addToast('네트워크 오류가 발생했습니다.', 'error')
    } finally {
      setSaving(false)
    }
  }

  if (idLoading) {
    return (
        <div className="g-panel">
          <PageLoading />
        </div>
    )
  }

  if (idError) {
    return (
        <div className="g-panel">
          <PageError message={idError} />
        </div>
    )
  }

  if (loadError) {
    return (
        <div className="g-panel">
          <PageError message={loadError} onRetry={() => guideId != null && void load(guideId)} />
        </div>
    )
  }

  return (
      <div className="g-panel">
        <h1>🔧 가이드 설정</h1>
        <p className="g-hint">가이드 운영에 필요한 노출/비용/알림/리뷰/계정 설정을 한 곳에서 관리합니다.</p>
        <div className="gm-stack" style={{ marginTop: '1rem', maxWidth: '32rem' }}>

          <section className="gm-card">
            <h2>가이드 비용 및 알림</h2>
            <p className="gm-hint">종일 패키지 금액(8시간 기준)과 로컬 알림 옵션을 함께 저장합니다.</p>
            <div className="g-row">
              <div>
                <strong>새로운 예약 요청 알림 (Push)</strong>
              </div>
              <label className="g-toggle">
                <input type="checkbox" checked={pushOn} onChange={(e) => setPushOn(e.target.checked)} />
                <span className="g-toggle-ui" />
              </label>
            </div>
            <div className="g-row">
              <div>
                <strong>메시지 수신 알림 (Email)</strong>
              </div>
              <label className="g-toggle">
                <input type="checkbox" checked={emailOn} onChange={(e) => setEmailOn(e.target.checked)} />
                <span className="g-toggle-ui" />
              </label>
            </div>
            <div className="g-price-row" style={{ marginTop: '0.85rem' }}>
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
            <div className="g-save-row" style={{ marginTop: '0.9rem' }}>
              <button type="button" className="g-save" onClick={() => void handleSaveFees()} disabled={saving}>
                {saving ? '저장 중…' : '비용/알림 저장'}
              </button>
            </div>
          </section>

          {/* 활성화 섹션 */}
          <section className="gm-card">
            <h2>게스트 노출 설정</h2>
            <p className="gm-hint">비활성화 시 게스트에게 뜨지 않아요.</p>
            <div style={{ display: 'flex', gap: '0.6rem', marginTop: '0.5rem' }}>
              {/* 활성화 버튼 */}
              <button
                  type="button"
                  onClick={() => { if (!profile?.isActive) void toggleActive() }}
                  disabled={busy || !!profile?.isActive}
                  style={{
                    padding: '0.5rem 1.2rem',
                    borderRadius: 8,
                    border: 'none',
                    cursor: profile?.isActive ? 'default' : 'pointer',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                    background: profile?.isActive ? '#15803d' : '#e5e7eb',
                    color: profile?.isActive ? '#fff' : '#6b7280',
                    opacity: busy ? 0.6 : 1,
                    transition: 'background 0.2s',
                  }}
              >
                ✅ 활성화
              </button>
              {/* 비활성화 버튼 */}
              <button
                  type="button"
                  onClick={() => {
                    if (profile?.isActive) setDeactivateConfirmOpen(true)
                  }}
                  disabled={busy || !profile?.isActive}
                  style={{
                    padding: '0.5rem 1.2rem',
                    borderRadius: 8,
                    border: 'none',
                    cursor: !profile?.isActive ? 'default' : 'pointer',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                    background: !profile?.isActive ? '#b91c1c' : '#e5e7eb',
                    color: !profile?.isActive ? '#fff' : '#6b7280',
                    opacity: busy ? 0.6 : 1,
                    transition: 'background 0.2s',
                  }}
              >
                ⛔ 비활성화
              </button>
            </div>
          </section>

          {/* 관리자 승인 섹션 */}
          <section className="gm-card">
            <h2>관리자 승인</h2>
            <p style={{ margin: 0, fontSize: '0.9rem' }}>
              승인 여부: <strong>{profile?.isApproved ? '승인됨' : '미승인'}</strong>
            </p>
          </section>

          {/* 계정 탈퇴 섹션 */}
          <section className="gm-card">
            <h2>계정 탈퇴</h2>
            <p className="gm-hint">가이드 자격을 탈퇴합니다. 탈퇴 후에는 가이드 기능을 사용할 수 없습니다.</p>
            <button
                type="button"
                className="gm-btn gm-btn--danger"
                onClick={() => void handleWithdraw()}
                disabled={withdrawing}
            >
              {withdrawing ? '처리 중…' : '가이드 탈퇴'}
            </button>
          </section>

        </div>
        {deactivateConfirmOpen && (
          <div className="gm-confirm-overlay" role="dialog" aria-modal="true" aria-label="비활성화 확인" onClick={() => !busy && setDeactivateConfirmOpen(false)}>
            <div className="gm-confirm" onClick={(e) => e.stopPropagation()}>
              <p className="gm-confirm-kicker">노출 숨기기</p>
              <h3>정말 숨기겠어요?</h3>
              <p>비활성화 시 게스트에게 보이지 않아요.</p>
              <div className="gm-confirm-actions">
                <button type="button" className="gm-confirm-btn gm-confirm-btn--line" onClick={() => setDeactivateConfirmOpen(false)} disabled={busy}>
                  취소
                </button>
                <button
                  type="button"
                  className="gm-confirm-btn gm-confirm-btn--danger"
                  disabled={busy}
                  onClick={() => {
                    setDeactivateConfirmOpen(false)
                    void toggleActive()
                  }}
                >
                  확인
                </button>
              </div>
            </div>
          </div>
        )}
        <Toast toasts={toasts} />
      </div>
  )
}