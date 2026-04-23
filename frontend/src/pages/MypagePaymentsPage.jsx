import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'

import { PaymentDetailPanel } from '../components/PaymentDetailPanel.jsx'
import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { fetchGuestMatchRequests, fetchGuestPayments, parseMatchingApiError } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

function statusLabel(s) {
  const m = {
    PENDING: '결제 대기',
    COMPLETED: '결제완료',
    REFUNDED: '환불완료',
    FAILED: '실패',
    CANCELLED: '취소',
  }
  return m[s] ?? s
}

function tabFilter(tab, p) {
  const s = p.status
  if (tab === 'done') return s === 'COMPLETED'
  if (tab === 'cancel') return s === 'REFUNDED' || s === 'CANCELLED' || s === 'FAILED'
  return true
}

export function MypagePaymentsPage() {
  const location = useLocation()
  const [payments, setPayments] = useState([])
  const [requestsById, setRequestsById] = useState({})
  const [tab, setTab] = useState('all')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [detailModal, setDetailModal] = useState(
    /** @type {{ open: boolean, paymentId: number | null, data: Record<string, unknown> | null, err: string, loading: boolean }} */ ({
      open: false,
      paymentId: null,
      data: null,
      err: '',
      loading: false,
    }),
  )
  const [routeHint, setRouteHint] = useState('')

  useEffect(() => {
    const h = location.state?.hint
    if (typeof h === 'string' && h.trim()) {
      setRouteHint(h.trim())
    }
  }, [location.state])

  const loadPayments = useCallback(async () => {
    const [pay, req] = await Promise.all([fetchGuestPayments(apiRequest), fetchGuestMatchRequests(apiRequest)])
    setPayments(Array.isArray(pay) ? pay : [])
    const map = {}
    for (const r of Array.isArray(req) ? req : []) {
      map[r.requestId] = r
    }
    setRequestsById(map)
  }, [])

  const refetch = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      await loadPayments()
    } catch (e) {
      setError(e instanceof Error ? e.message : '불러오기 실패')
    } finally {
      setLoading(false)
    }
  }, [loadPayments])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await loadPayments()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [loadPayments])

  const filtered = useMemo(() => payments.filter((p) => tabFilter(tab, p)), [payments, tab])

  const closeDetail = () => {
    setDetailModal({ open: false, paymentId: null, data: null, err: '', loading: false })
  }

  const openDetail = async (paymentId) => {
    setDetailModal({ open: true, paymentId, data: null, err: '', loading: true })
    try {
      const res = await apiRequest(`/matching/payments/${paymentId}`, { method: 'GET' })
      const text = await res.text()
      if (!res.ok) throw new Error(parseMatchingApiError(text))
      const raw = text ? JSON.parse(text) : null
      const data = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : {}
      setDetailModal({ open: true, paymentId, data, err: '', loading: false })
    } catch (e) {
      const msg = e instanceof Error ? e.message : '상세 조회 실패'
      setDetailModal({ open: true, paymentId, data: null, err: msg, loading: false })
    }
  }

  return (
    <div className="mp-member">
      <h1>💳 나의 결제 내역</h1>
      <p className="sub">결제·환불 상태를 확인하고, 영수증 상세를 열어 볼 수 있어요.</p>
      {routeHint && (
        <p
          className="sub"
          role="status"
          style={{
            padding: '0.75rem 0.95rem',
            borderRadius: 12,
            background: '#ecfdf5',
            border: '1px solid #a7f3d0',
            color: '#065f46',
            fontWeight: 600,
          }}
        >
          {routeHint}
        </p>
      )}
      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void refetch()} />}

      {!loading && !error && (
        <>
          <div className="mp-tabs" role="tablist">
            <button type="button" className={tab === 'all' ? 'is-on' : ''} onClick={() => setTab('all')}>
              전체
            </button>
            <button type="button" className={tab === 'done' ? 'is-on' : ''} onClick={() => setTab('done')}>
              결제완료
            </button>
            <button type="button" className={tab === 'cancel' ? 'is-on' : ''} onClick={() => setTab('cancel')}>
              취소/환불
            </button>
          </div>

          {filtered.length === 0 ? (
            <PageEmpty title="해당하는 결제 내역이 없습니다">다른 탭을 선택하거나 매칭 결제를 완료해 보세요.</PageEmpty>
          ) : (
            <div className="mp-pay-moodboard" aria-label="결제 포스트잇 목록">
              {filtered.map((p, idx) => {
                const mr = requestsById[p.requestId]
                const when = p.paidAt ? new Date(p.paidAt).toLocaleString('ko-KR') : '—'
                const ord = p.pgOrderNo ?? `PAY-${p.paymentId}`
                const tone = ['mint', 'sun', 'sky', 'blossom'][idx % 4]
                return (
                  <article key={p.paymentId} className={`mp-pay-note mp-pay-note--${tone}`}>
                    <span className="mp-pay-note__pin" aria-hidden />
                    <p className="mp-pay-note__kicker">결제 {statusLabel(p.status)}</p>
                    <h2 className="mp-pay-note__title">{mr?.destination ?? `요청 #${p.requestId}`}</h2>
                    <p className="mp-pay-note__line">
                      <span className="mp-pay-note__amount">
                        ₩{p.amount != null ? Number(p.amount).toLocaleString('ko-KR') : '—'}
                      </span>
                    </p>
                    <p className="mp-pay-note__meta">
                      {when}
                      <br />
                      {ord} · 투어 {mr?.desiredDate ?? '—'}
                    </p>
                    {mr && (
                      <p className="mp-pay-note__type">
                        {String(p.paymentType ?? '—')} · 요청 #{p.requestId}
                      </p>
                    )}
                    <div className="mp-pay-note__actions">
                      <button type="button" className="mp-btn mp-btn--line" onClick={() => void openDetail(p.paymentId)}>
                        영수증 / 상세
                      </button>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </>
      )}

      {detailModal.open && (
        <div className="mp-modal-overlay" role="dialog" aria-modal="true">
          <div className="mp-modal">
            <h2 style={{ marginTop: 0 }}>결제 상세</h2>
            {detailModal.loading ? (
              <PageLoading label="상세 불러오는 중…" />
            ) : detailModal.err ? (
              <PageError
                message={detailModal.err}
                onRetry={
                  detailModal.paymentId != null
                    ? () => {
                        void openDetail(detailModal.paymentId)
                      }
                    : undefined
                }
                retryLabel="다시 시도"
              />
            ) : (
              <PaymentDetailPanel detail={detailModal.data ?? {}} />
            )}
            <div className="mp-modal-actions">
              <button type="button" className="mp-btn mp-btn--line" onClick={closeDetail}>
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
