import { useCallback, useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { fetchGuestMatchRequests, fetchGuestPayments, parseMatchingApiError } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

async function tryFetchExtension(requestId) {
  const res = await apiRequest(`/matching/extensions/${requestId}`, { method: 'GET' })
  const text = await res.text()
  if (!res.ok) return null
  return text ? JSON.parse(text) : null
}

function refundDeadlineOk(p) {
  if (!p?.refundDeadline) return false
  try {
    return new Date(p.refundDeadline).getTime() > Date.now()
  } catch {
    return false
  }
}

function formatDateTime(v) {
  if (!v) return '정보 없음'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatDate(v) {
  if (!v) return '날짜 미정'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' })
}

function formatMoney(n) {
  if (n == null || Number.isNaN(Number(n))) return '0원'
  return `${Number(n).toLocaleString('ko-KR')}원`
}

function hoursLeft(v) {
  if (!v) return null
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return null
  const diff = Math.max(0, t - Date.now())
  return Math.floor(diff / (1000 * 60 * 60))
}

function timeLeftText(v) {
  if (!v) return '0시간'
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return '0시간'
  const diff = Math.max(0, t - Date.now())
  const h = Math.floor(diff / (1000 * 60 * 60))
  const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  return `${h}시간 ${m}분`
}

function dday(v) {
  if (!v) return 0
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return 0
  const diff = Math.max(0, t - Date.now())
  return Math.floor(diff / (1000 * 60 * 60 * 24))
}

export function MypageTourPage() {
  const location = useLocation()
  const [requests, setRequests] = useState([])
  const [payments, setPayments] = useState([])
  const [extMap, setExtMap] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')
  const [refundPaymentId, setRefundPaymentId] = useState(null)
  const [refundReason, setRefundReason] = useState('')
  const [refundEvidence, setRefundEvidence] = useState('')
  const [routeHint, setRouteHint] = useState('')

  useEffect(() => {
    const h = location.state?.hint
    if (typeof h === 'string' && h.trim()) setRouteHint(h.trim())
  }, [location.state])

  const load = useCallback(async () => {
    const [req, pay] = await Promise.all([fetchGuestMatchRequests(apiRequest), fetchGuestPayments(apiRequest)])
    setRequests(Array.isArray(req) ? req : [])
    setPayments(Array.isArray(pay) ? pay : [])

    const candidates = (Array.isArray(req) ? req : []).filter((r) => ['PAID', 'IN_PROGRESS', 'COMPLETED'].includes(r.status))
    const entries = await Promise.all(
      candidates.map(async (r) => {
        const ex = await tryFetchExtension(r.requestId)
        return ex ? [r.requestId, ex] : null
      }),
    )
    const m = {}
    for (const e of entries) {
      if (e) m[e[0]] = e[1]
    }
    setExtMap(m)
  }, [])

  const refetch = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '불러오기 실패')
    } finally {
      setLoading(false)
    }
  }, [load])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await load()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [load])

  const selectExtension = async (requestId, extend) => {
    setBusy(true)
    setToast('')
    try {
      const res = await apiRequest(`/matching/extensions/${requestId}/select`, {
        method: 'PATCH',
        json: { extend },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseMatchingApiError(text))
      await load()
    } catch (e) {
      setToast(e instanceof Error ? e.message : '연장 처리 실패')
    } finally {
      setBusy(false)
    }
  }

  const submitRefund = async () => {
    if (refundPaymentId == null) return
    const reason = refundReason.trim()
    if (!reason) {
      setToast('환불 사유를 입력해 주세요.')
      return
    }
    setBusy(true)
    setToast('')
    try {
      const res = await apiRequest('/matching/payments/refunds', {
        method: 'POST',
        json: {
          paymentId: refundPaymentId,
          reason,
          evidenceUrl: refundEvidence.trim() || undefined,
        },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseMatchingApiError(text))
      setRefundPaymentId(null)
      setRefundReason('')
      setRefundEvidence('')
      await load()
    } catch (e) {
      setToast(e instanceof Error ? e.message : '환불 실패')
    } finally {
      setBusy(false)
    }
  }

  const refundable = payments.filter((p) => p.status === 'COMPLETED' && refundDeadlineOk(p))

  return (
    <div className="mp-member">
      <h1>투어 연장 및 환불 관리 🔄</h1>
      <p className="sub">투어 종료 직전/직후의 연장 선택과 환불 처리를 한 번에 관리합니다.</p>
      {routeHint && (
        <p
          className="sub"
          role="status"
          style={{
            padding: '0.75rem 0.95rem',
            borderRadius: 12,
            background: '#eff6ff',
            border: '1px solid #bfdbfe',
            color: '#1e3a8a',
            fontWeight: 600,
          }}
        >
          {routeHint}
        </p>
      )}
      {toast && <p className="err">{toast}</p>}
      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void refetch()} />}

      {!loading && !error && (
        <div className="mp-tour-shell">
          <section className="mp-tour-block">
            <h2>진행 예정 투어 연장</h2>
            <p className="sub">투어 전날 저녁, 아쉬움이 남는다면 하루 더 로컬과 함께해요.</p>
            {Object.keys(extMap).length === 0 && (
              <PageEmpty title="진행 중인 연장 요청이 없습니다">연장 안내가 오면 이 영역에 표시됩니다.</PageEmpty>
            )}
            <div className="mp-cards">
              {Object.entries(extMap).map(([requestId, ex]) => {
                const rid = Number(requestId)
                const mr = requests.find((r) => r.requestId === rid)
                const open = ex.status === 'REQUESTED'
                const left = hoursLeft(ex.deadlineAt)
                return (
                  <article key={rid} className="mp-tour-card">
                    <div className="mp-tour-row">
                      <div>
                        <p className="mp-tour-name">{mr?.guideNickname ?? '가이드'}와 함께하는 {mr?.destination ?? '로컬'} 투어</p>
                        <p className="mp-tour-meta">결제 일시: {formatDate(mr?.desiredDate)} · 상태: {ex.status}</p>
                        <p className="mp-tour-extension">연장 선택 가능 (D-{dday(ex.deadlineAt)})</p>
                        <p className="mp-tour-fee">추가 비용: {formatMoney(ex.additionalFee)} (선택 후 결제, 마감 {formatDateTime(ex.deadlineAt)})</p>
                      </div>
                      {open && (
                        <button type="button" className="mp-btn" disabled={busy} onClick={() => void selectExtension(rid, true)}>
                          연장 및 추가 결제하기
                        </button>
                      )}
                    </div>
                    {open && (
                      <button type="button" className="mp-tour-inline-link" disabled={busy} onClick={() => void selectExtension(rid, false)}>
                        연장 안 함
                      </button>
                    )}
                    {!open && left != null && <p className="mp-pay-muted">현재 상태: {ex.status} · 남은 가능 시간 {left}시간</p>}
                  </article>
                )
              })}
            </div>
          </section>

          <section className="mp-tour-block">
            <h2>투어 취소 및 환불 관리</h2>
            <p className="sub">결제 후 2시간 이내에는 조건에 따라 즉시 환불이 가능합니다.</p>
            {refundable.length === 0 && (
              <PageEmpty title="환불 가능한 결제가 없습니다">조건을 만족하는 결제가 있으면 여기에서 신청할 수 있어요.</PageEmpty>
            )}
            <div className="mp-cards">
              {refundable.map((p) => (
                <article key={p.paymentId} className="mp-tour-card">
                  <div className="mp-tour-row">
                    <div>
                      <p className="mp-tour-name">결제 #{p.paymentId} 완료 건 환불</p>
                      <p className="mp-tour-meta">결제 일시: {formatDateTime(p.paidAt)} · 주문 번호: {p.pgOrderNo ?? `ORD-${p.paymentId}`}</p>
                      <p className="mp-tour-extension">🔵 환불 가능 (남은 시간: {timeLeftText(p.refundDeadline)})</p>
                    </div>
                    <button type="button" className="mp-btn mp-btn--danger" onClick={() => setRefundPaymentId(p.paymentId)}>
                      즉시 환불하기
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <article className="mp-tour-notice">
            <p className="mp-tour-notice-title">📍 투어 관리 정책 안내</p>
            <ul>
              <li>연장 선택: 투어 시작 전날 밤 22:00까지 결제가 완료되어야 가이드에게 최종 전달됩니다.</li>
              <li>환불 규정: 결제 시점으로부터 2시간 이내 신청 시 100% 즉시 환불되며, 이후에는 환불 정책 규정이 적용됩니다.</li>
            </ul>
          </article>
        </div>
      )}

      {refundPaymentId != null && (
        <div className="mp-modal-overlay" role="dialog" aria-modal="true">
          <div className="mp-modal">
            <h2 style={{ marginTop: 0 }}>환불 신청</h2>
            <p className="sub">paymentId: {refundPaymentId}</p>
            <label className="mp-modal-label" htmlFor="rr">
              사유 (필수)
            </label>
            <textarea id="rr" className="mp-modal-text" value={refundReason} onChange={(e) => setRefundReason(e.target.value)} rows={3} />
            <label className="mp-modal-label" htmlFor="ev">
              증빙 URL (선택)
            </label>
            <input id="ev" className="mp-modal-text" value={refundEvidence} onChange={(e) => setRefundEvidence(e.target.value)} />
            <div className="mp-modal-actions">
              <button type="button" className="mp-btn mp-btn--line" disabled={busy} onClick={() => setRefundPaymentId(null)}>
                취소
              </button>
              <button type="button" className="mp-btn" disabled={busy} onClick={() => void submitRefund()}>
                {busy ? '처리 중…' : '제출'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
