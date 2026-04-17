import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { daysUntil, fetchGuestMatchRequests, parseMatchingApiError } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

const UPCOMING = new Set(['PENDING', 'ACCEPTED', 'PAID', 'IN_PROGRESS'])

async function loadGuideNicknames(apiRequest, guideIds) {
  const map = {}
  for (const id of [...new Set(guideIds.filter(Boolean))]) {
    try {
      const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (res.ok && text) {
        const p = JSON.parse(text)
        map[id] = p.nickname ?? `가이드 #${id}`
      }
    } catch {
      map[id] = `가이드 #${id}`
    }
  }
  return map
}

export function MypageItineraryPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [names, setNames] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [toast, setToast] = useState('')
  const [detail, setDetail] = useState(null)
  const [modal, setModal] = useState(null)
  const [reason, setReason] = useState('')

  const reload = useCallback(async () => {
    const all = await fetchGuestMatchRequests(apiRequest)
    const list = (Array.isArray(all) ? all : []).filter((r) => UPCOMING.has(r.status))
    list.sort((a, b) => {
      const da = String(a.desiredDate ?? '')
      const db = String(b.desiredDate ?? '')
      if (da !== db) return da.localeCompare(db)
      return (a.requestId ?? 0) - (b.requestId ?? 0)
    })
    setRows(list)
    const nm = await loadGuideNicknames(
      apiRequest,
      list.map((r) => r.guideId),
    )
    setNames(nm)
  }, [])

  const refetch = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      await reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : '불러오기 실패')
    } finally {
      setLoading(false)
    }
  }, [reload])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await reload()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [reload])

  const goPay = useCallback(
    (row) => {
      if (!row?.guideId || !row?.requestId) return
      navigate(`/guides/${row.guideId}/match`, { state: { requestId: row.requestId } })
    },
    [navigate],
  )

  const detailGuideName = useMemo(() => {
    if (!detail) return ''
    return names[detail.guideId] ?? `가이드 #${detail.guideId}`
  }, [detail, names])

  const openModal = (mode, requestId) => {
    setReason('')
    setModal({ mode, requestId })
  }

  const submitModal = async () => {
    if (!modal) return
    const r = reason.trim()
    if (!r) {
      setToast('사유를 입력해 주세요.')
      return
    }
    setBusyId(modal.requestId)
    setToast('')
    try {
      if (modal.mode === 'decline') {
        const res = await apiRequest(`/matching/requests/${modal.requestId}/decline`, {
          method: 'PATCH',
          json: { reason: r },
        })
        const text = await res.text()
        if (!res.ok) throw new Error(parseMatchingApiError(text))
      } else {
        const res = await apiRequest(`/matching/requests/${modal.requestId}/guest/cancel`, {
          method: 'PATCH',
          json: { cancelReason: r },
        })
        const text = await res.text()
        if (!res.ok) throw new Error(parseMatchingApiError(text))
      }
      setModal(null)
      await reload()
    } catch (e) {
      setToast(e instanceof Error ? e.message : '실패')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="mp-member">
      <h1>📆 앞으로의 여행 일정</h1>
      <p className="sub">
        <code>GET /api/matching/requests/guest/list</code> 중 진행·예정 상태만 표시합니다. 수락/거절/취소는 매칭 API를
        호출합니다.
      </p>
      {toast && <p className="err">{toast}</p>}
      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void refetch()} />}

      {!loading && !error && rows.length === 0 && (
        <PageEmpty title="예정된 여행이 없습니다">진행 중이거나 예정된 매칭이 없을 때 표시됩니다.</PageEmpty>
      )}

      {!loading && !error && rows.length > 0 && (
        <div className="mp-cards">
          <h2 style={{ margin: 0, fontSize: '1rem', fontWeight: 800 }}>다가오는 여행의 설렘 ✈️</h2>
          {rows.map((r) => {
            const d = daysUntil(r.desiredDate)
            const nick = names[r.guideId] ?? `가이드 #${r.guideId}`
            const dday = d == null ? '—' : d === 0 ? 'D-Day' : d > 0 ? `D-${d}` : `${d}일 지남`
            return (
              <article key={r.requestId} className="mp-trip-card">
                <div className="mp-trip-card__meta">
                  <span className="mp-dday">{dday}</span>
                  <button type="button" className="mp-trip-title-btn" onClick={() => setDetail(r)}>
                    {nick}와(과) 함께하는 {r.destination}
                  </button>
                  <p className="mp-trip-detail">
                    {r.desiredDate ?? '—'} | 제시: {r.proposedSchedule ?? '—'}
                  </p>
                  <p className="mp-trip-detail">상태: {r.status}</p>
                  <p className="mp-trip-detail">예산: {r.desiredBudget != null ? `₩${Number(r.desiredBudget).toLocaleString()}` : '—'}</p>
                </div>
                <div className="mp-trip-actions">
                  <span className="mp-thumb" style={{ minHeight: '4.5rem' }}>
                    Preview
                  </span>
                  {r.status === 'ACCEPTED' && (
                    <>
                      <button type="button" className="mp-btn" disabled={busyId != null} onClick={() => goPay(r)}>
                        결제하고 확정
                      </button>
                      <button type="button" className="mp-btn mp-btn--danger" disabled={busyId != null} onClick={() => openModal('decline', r.requestId)}>
                        제안 거절
                      </button>
                    </>
                  )}
                  {(r.status === 'ACCEPTED' || r.status === 'PAID') && (
                    <button type="button" className="mp-btn mp-btn--line" disabled={busyId != null} onClick={() => openModal('cancel', r.requestId)}>
                      예약 취소
                    </button>
                  )}
                  {r.status === 'PENDING' && <span className="mp-pay-muted">가이드 응답 대기</span>}
                </div>
              </article>
            )
          })}
        </div>
      )}

      {modal && (
        <div className="mp-modal-overlay" role="dialog" aria-modal="true">
          <div className="mp-modal" style={{ maxWidth: '24rem' }}>
            <h2 style={{ marginTop: 0 }}>{modal.mode === 'decline' ? '제안 거절' : '예약 취소'}</h2>
            <p className="sub">요청 #{modal.requestId}</p>
            <label className="mp-modal-label" htmlFor="why">
              사유 (필수)
            </label>
            <textarea id="why" className="mp-modal-text" value={reason} onChange={(e) => setReason(e.target.value)} rows={4} />
            <div className="mp-modal-actions">
              <button type="button" className="mp-btn mp-btn--line" onClick={() => setModal(null)} disabled={busyId != null}>
                닫기
              </button>
              <button type="button" className="mp-btn" onClick={() => void submitModal()} disabled={busyId != null}>
                {busyId ? '처리 중…' : '확인'}
              </button>
            </div>
          </div>
        </div>
      )}

      {detail && (
        <div className="mp-modal-overlay" role="dialog" aria-modal="true">
          <div className="mp-modal" style={{ maxWidth: '28rem' }}>
            <h2 style={{ marginTop: 0 }}>여행 일정 상세</h2>
            <p className="sub">
              {detailGuideName} · 요청 #{detail.requestId}
            </p>
            <div style={{ display: 'grid', gap: '0.35rem' }}>
              <p className="mp-trip-detail" style={{ margin: 0 }}>
                목적지: <strong>{detail.destination ?? '—'}</strong>
              </p>
              <p className="mp-trip-detail" style={{ margin: 0 }}>
                여행일: {detail.desiredDate ?? '—'}
              </p>
              <p className="mp-trip-detail" style={{ margin: 0 }}>
                제시 일정: {detail.proposedSchedule ?? '—'}
              </p>
              <p className="mp-trip-detail" style={{ margin: 0 }}>
                상태: {detail.status}
              </p>
              <p className="mp-trip-detail" style={{ margin: 0 }}>
                예산:{' '}
                {detail.desiredBudget != null ? `₩${Number(detail.desiredBudget).toLocaleString()}` : '—'}
              </p>
            </div>
            <div className="mp-modal-actions">
              {detail.status === 'ACCEPTED' && (
                <button type="button" className="mp-btn" onClick={() => goPay(detail)} disabled={busyId != null}>
                  결제하고 확정
                </button>
              )}
              <button type="button" className="mp-btn mp-btn--line" onClick={() => setDetail(null)}>
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
