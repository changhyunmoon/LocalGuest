import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'

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
  const [profile, setProfile] = useState(null)
  const [summary, setSummary] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async (id) => {
    setLoadError('')
    setActionError('')
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
    setActionError('')
    try {
      const res = await apiRequest(`/guides/${guideId}/active`, { method: 'PATCH' })
      const text = await res.text()
      if (!res.ok) {
        setActionError(await readJsonError(res, text))
        return
      }
      setProfile(text ? JSON.parse(text) : profile)
    } catch {
      setActionError('요청 실패')
    } finally {
      setBusy(false)
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

  return (
    <div className="g-panel">
      <h1>🔧 가이드 설정</h1>
      <p className="g-hint">
        노출·요금은 <Link to="/guide/mypage/fees">가이드 비용</Link>, 프로필 필드는{' '}
        <Link to="/guide/mypage/profile">프로필 등록</Link>에서 수정합니다. 여기서는 서버에 있는 토글·승인·리뷰 요약만
        다룹니다.
      </p>
      {actionError && <p className="g-error">{actionError}</p>}

      <div className="gm-stack" style={{ marginTop: '1rem', maxWidth: '32rem' }}>
        <section className="gm-card">
          <h2>활성화</h2>
          <p className="gm-hint">
            <code>PATCH /api/guides/&#123;guideId&#125;/active</code> — 서버에서 활성 여부를 토글합니다.
          </p>
          <p style={{ margin: '0.5rem 0', fontSize: '0.9rem' }}>
            현재 상태: <strong>{profile?.isActive ? '활성' : '비활성'}</strong>
          </p>
          <button type="button" className="gm-btn" onClick={() => void toggleActive()} disabled={busy}>
            {busy ? '처리 중…' : '활성 / 비활성 전환'}
          </button>
        </section>

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
    </div>
  )
}
