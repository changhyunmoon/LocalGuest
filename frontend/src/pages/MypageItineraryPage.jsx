import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import {
  daysUntil,
  fetchGuestMatchRequests,
  fetchGuestPayments,
  loadGuideNicknames,
  parseMatchingApiError,
  pickLatestCompletedPaymentIdForRequest,
} from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

const UPCOMING = new Set(['PENDING', 'ACCEPTED', 'PAID', 'IN_PROGRESS'])

function formatBudgetRangeKrw(minWon, maxWon, fallbackSingleWon) {
  const min = minWon != null && minWon !== '' && !Number.isNaN(Number(minWon)) ? Number(minWon) : null
  const max = maxWon != null && maxWon !== '' && !Number.isNaN(Number(maxWon)) ? Number(maxWon) : null
  if (min != null && max != null) {
    return `₩${min.toLocaleString('ko-KR')}~${max.toLocaleString('ko-KR')}`
  }
  if (min != null || max != null) {
    const only = min ?? max
    return `₩${Number(only).toLocaleString('ko-KR')}`
  }
  if (fallbackSingleWon != null && fallbackSingleWon !== '' && !Number.isNaN(Number(fallbackSingleWon))) {
    return `₩${Number(fallbackSingleWon).toLocaleString('ko-KR')}`
  }
  return '—'
}

export function MypageItineraryPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [names, setNames] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [toast, setToast] = useState('')
  const [modal, setModal] = useState(null)
  const [reason, setReason] = useState('')
  const [paymentIdByRequest, setPaymentIdByRequest] = useState(() => ({}))

  const reload = useCallback(async () => {
    const [all, paysRaw] = await Promise.all([
      fetchGuestMatchRequests(apiRequest),
      fetchGuestPayments(apiRequest).catch(() => []),
    ])
    const pays = Array.isArray(paysRaw) ? paysRaw : []
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
    const payMap = {}
    for (const r of list) {
      const pid = pickLatestCompletedPaymentIdForRequest(pays, r.requestId)
      if (pid != null) payMap[r.requestId] = pid
    }
    setPaymentIdByRequest(payMap)
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

  /** 결제 전·대기·수락: 매칭(옵션) 화면 / 결제 후·진행: 상세 코스(지도) */
  const openMatchScreen = useCallback(
    (row) => {
      if (!row?.guideId || !row?.requestId) return
      const st = String(row.status ?? '')
      if (st === 'PAID' || st === 'IN_PROGRESS') {
        const qs = new URLSearchParams()
        qs.set('requestId', String(row.requestId))
        const pid = paymentIdByRequest[row.requestId]
        if (pid != null) qs.set('paymentId', String(pid))
        navigate(`/guides/${row.guideId}/match/complete?${qs.toString()}`)
        return
      }
      navigate(`/guides/${row.guideId}/match`, { state: { requestId: row.requestId } })
    },
    [navigate, paymentIdByRequest],
  )

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
        진행 중이거나 예정된 매칭만 보여 줍니다. <strong>일정 제목</strong> 또는 <strong>Preview</strong>를 누르면, 결제가 끝난 일정은{' '}
        <strong>상세 코스(카카오 지도)</strong> 화면으로, 아직이면 <strong>매칭·결제</strong> 화면으로 이동합니다. 거절·취소는 각 카드 버튼에서
        진행할 수 있어요.
      </p>
      <MypageDevHint>
        PAID/IN_PROGRESS → <code>/guides/&#123;id&#125;/match/complete?requestId=…</code> · 그 외 →{' '}
        <code>/guides/&#123;id&#125;/match</code>
      </MypageDevHint>
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
                  <button type="button" className="mp-trip-title-btn" onClick={() => openMatchScreen(r)}>
                    {nick}와(과) 함께하는 {r.destination}
                  </button>
                  <p className="mp-trip-detail">
                    {r.desiredDate ?? '—'} | 제시: {r.proposedSchedule ?? '—'}
                  </p>
                  <p className="mp-trip-detail">상태: {r.status}</p>
                  <p className="mp-trip-detail">
                    예산: {formatBudgetRangeKrw(r.budgetMinWon, r.budgetMaxWon, r.desiredBudget)}
                  </p>
                </div>
                <div className="mp-trip-actions">
                  <button type="button" className="mp-thumb mp-thumb--match" style={{ minHeight: '4.5rem' }} onClick={() => openMatchScreen(r)}>
                    Preview
                  </button>
                  {r.status === 'ACCEPTED' && (
                    <>
                      <button type="button" className="mp-btn" disabled={busyId != null} onClick={() => openMatchScreen(r)}>
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

    </div>
  )
}
