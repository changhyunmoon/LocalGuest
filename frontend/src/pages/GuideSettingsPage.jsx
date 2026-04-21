import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

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

export function GuideSettingsPage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const navigate = useNavigate()
  const { toasts, addToast } = useToast()
  const [profile, setProfile] = useState(null)
  const [summary, setSummary] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [busy, setBusy] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)

  const load = useCallback(async (id) => {
    setLoadError('')
    try {
      const [pr, sr] = await Promise.all([
        apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true }),
        apiRequest(`/guides/${id}/reviews/summary`, { method: 'GET', skipAuth: true }),
      ])
      const pt = await pr.text()
      const st = await sr.text()
      if (!pr.ok) {
        throw new Error(await readJsonError(pr, pt))
      }
      if (!sr.ok) {
        throw new Error(await readJsonError(sr, st))
      }
      setProfile(pt ? JSON.parse(pt) : null)
      setSummary(st ? JSON.parse(st) : null)
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
      setProfile(text ? JSON.parse(text) : profile)
      addToast('전환되었습니다.')
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
        <p className="g-hint">
          노출·요금은 <Link to="/guide/mypage/fees">가이드 비용</Link>, 프로필 필드는{' '}
          <Link to="/guide/mypage/profile">프로필 등록</Link>에서 수정합니다. 여기서는 서버에 있는 토글·승인·리뷰 요약만
          다룹니다.
        </p>
        <div className="gm-stack" style={{ marginTop: '1rem', maxWidth: '32rem' }}>

          {/* 활성화 섹션 */}
          <section className="gm-card">
            <h2>활성화</h2>
            <p className="gm-hint">
              <code>PATCH /api/guides/&#123;guideId&#125;/active</code> — 서버에서 활성 여부를 토글합니다.
            </p>
            <p style={{ margin: '0.5rem 0', fontSize: '0.9rem' }}>
              현재 상태:{' '}
              <strong style={{ color: profile?.isActive ? '#15803d' : '#b91c1c' }}>
                {profile?.isActive ? '활성' : '비활성'}
              </strong>
            </p>
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
                  onClick={() => { if (profile?.isActive) void toggleActive() }}
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
            <p className="gm-hint" style={{ marginTop: '0.5rem' }}>
              승인 API는 관리자용 <code>PATCH /guides/&#123;id&#125;/approve</code> 입니다. 일반 가이드 계정에서는 호출하지
              않습니다.
            </p>
          </section>

          {/* 계정 탈퇴 섹션 */}
          <section className="gm-card">
            <h2>계정 탈퇴</h2>
            <p className="gm-hint">
              <code>DELETE /members/me?role=GUIDE</code> — 가이드 자격을 탈퇴합니다. 탈퇴 후에는 가이드 기능을 사용할 수
              없습니다.
            </p>
            <button
                type="button"
                className="gm-btn gm-btn--danger"
                onClick={() => void handleWithdraw()}
                disabled={withdrawing}
            >
              {withdrawing ? '처리 중…' : '가이드 탈퇴'}
            </button>
          </section>

          {/* 리뷰 요약 섹션 */}
          <section className="gm-card">
            <h2>리뷰 요약</h2>
            <p className="gm-hint">
              <code>GET /api/guides/&#123;guideId&#125;/reviews/summary</code>
            </p>
            <p style={{ margin: '0.5rem 0 0', fontSize: '0.95rem' }}>
              평균 평점: <strong>{summary?.averageRating != null ? Number(summary.averageRating).toFixed(2) : '—'}</strong>{' '}
              / 리뷰 수: <strong>{summary?.reviewCount ?? '—'}</strong>
            </p>
          </section>

        </div>
        <Toast toasts={toasts} />
      </div>
  )
}