import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { fetchGuestMatchRequests, fetchGuestPayments, parseMatchingApiError } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

async function tryFetchExtension(requestId) {
  const res = await apiRequest(`/matching/extensions/${requestId}`, { method: 'GET' })
  const text = await res.text()
  if (res.status === 204) {
    return {
      data: null,
      reason: res.headers.get('X-Extension-Reason') || '',
    }
  }
  if (!res.ok) return { data: null, reason: '' }
  return { data: text ? JSON.parse(text) : null, reason: '' }
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

function todayKey() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function dateKey(v) {
  if (v == null) return ''
  const s = String(v)
  const isoLike = s.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (isoLike) return `${isoLike[1]}-${isoLike[2]}-${isoLike[3]}`
  const compact = s.match(/(\d{4})\D+(\d{1,2})\D+(\d{1,2})/)
  if (compact) {
    const y = compact[1]
    const m = String(compact[2]).padStart(2, '0')
    const d = String(compact[3]).padStart(2, '0')
    return `${y}-${m}-${d}`
  }
  return s.length >= 10 ? s.slice(0, 10) : s
}

export function MypageTourPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [requests, setRequests] = useState([])
  const [payments, setPayments] = useState([])
  const [extMap, setExtMap] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')
  const [actionApiErr, setActionApiErr] = useState('')
  const [refundPaymentId, setRefundPaymentId] = useState(null)
  const [refundReason, setRefundReason] = useState('')
  const [refundEvidence, setRefundEvidence] = useState('')
  const [routeHint, setRouteHint] = useState('')
  const [extensionEmptyReason, setExtensionEmptyReason] = useState('')
  const [reasonGuideNames, setReasonGuideNames] = useState({
    booked: '',
    blocked: '',
    unavailable: '',
  })

  useEffect(() => {
    const h = location.state?.hint
    if (typeof h === 'string' && h.trim()) setRouteHint(h.trim())
  }, [location.state])

  const load = useCallback(async () => {
    const [req, pay] = await Promise.all([fetchGuestMatchRequests(apiRequest), fetchGuestPayments(apiRequest)])
    const allRequests = Array.isArray(req) ? req : []
    setRequests(allRequests)
    setPayments(Array.isArray(pay) ? pay : [])

    // 연장 레코드는 "투어 당일" 대상만 생성되므로, 같은 날 요청만 조회해 404 노이즈를 줄인다.
    const tk = todayKey()
    const baseCandidates = allRequests.filter((r) =>
      ['ACCEPTED', 'PAID', 'IN_PROGRESS', 'COMPLETED'].includes(String(r.status ?? '')),
    )
    const candidates = baseCandidates.filter((r) => dateKey(r.desiredDate) === tk)
    const resolvedCandidates = candidates.length > 0 ? candidates : baseCandidates
    const entries = await Promise.all(
      resolvedCandidates.map(async (r) => {
        const guideName = r?.guideNickname?.trim() || (r?.guideId != null ? `가이드 #${r.guideId}` : '가이드')
        if (r?.requestId == null) {
          return { entry: null, reason: 'MISSING_REQUEST_ID', guideName }
        }
        const ex = await tryFetchExtension(r.requestId)
        if (!ex?.data) return { entry: null, reason: ex?.reason || '', guideName }
        const status = String(ex.data.status ?? '')
        if (!['REQUESTED', 'GUIDE_APPROVED'].includes(status)) {
          const closedReason =
            status === 'PAID'
              ? 'ALREADY_EXTENDED_PAID'
              : status === 'REJECTED'
                ? 'ALREADY_DECLINED'
                : status === 'AUTO_CANCELLED'
                  ? 'AUTO_CANCELLED'
                  : ''
          return { entry: null, reason: closedReason || ex?.reason || '', guideName }
        }
        return { entry: [r.requestId, ex.data], reason: ex?.reason || '', guideName }
      }),
    )
    const m = {}
    let hasGuideUnavailableReason = false
    let hasGuideBookedReason = false
    let hasGuideBlockedReason = false
    let hasAlreadyDecidedReason = false
    let hasMissingRequestId = false
    let hasNoContentWithoutReason = false
    let bookedGuideName = ''
    let blockedGuideName = ''
    let unavailableGuideName = ''
    for (const wrapped of entries) {
      if (!wrapped) continue
      if (wrapped.reason === 'GUIDE_NEXT_DAY_UNAVAILABLE') {
        hasGuideUnavailableReason = true
        if (!unavailableGuideName) unavailableGuideName = wrapped.guideName || '가이드'
      }
      if (wrapped.reason === 'GUIDE_NEXT_DAY_BOOKED') {
        hasGuideBookedReason = true
        if (!bookedGuideName) bookedGuideName = wrapped.guideName || '가이드'
      }
      if (wrapped.reason === 'GUIDE_NEXT_DAY_BLOCKED') {
        hasGuideBlockedReason = true
        if (!blockedGuideName) blockedGuideName = wrapped.guideName || '가이드'
      }
      if (
        wrapped.reason === 'ALREADY_EXTENDED_PAID' ||
        wrapped.reason === 'ALREADY_DECLINED' ||
        wrapped.reason === 'AUTO_CANCELLED'
      ) {
        hasAlreadyDecidedReason = true
      }
      if (wrapped.reason === 'MISSING_REQUEST_ID') {
        hasMissingRequestId = true
      }
      if (!wrapped.reason && !wrapped.entry) {
        hasNoContentWithoutReason = true
      }
      if (wrapped.entry) m[wrapped.entry[0]] = wrapped.entry[1]
    }
    setReasonGuideNames({
      booked: bookedGuideName,
      blocked: blockedGuideName,
      unavailable: unavailableGuideName,
    })
    setExtMap(m)
    if (Object.keys(m).length === 0) {
      if (hasGuideBookedReason) {
        setExtensionEmptyReason('GUIDE_NEXT_DAY_BOOKED')
      } else if (hasGuideBlockedReason) {
        setExtensionEmptyReason('GUIDE_NEXT_DAY_BLOCKED')
      } else if (hasGuideUnavailableReason) {
        setExtensionEmptyReason('GUIDE_NEXT_DAY_UNAVAILABLE')
      } else if (hasAlreadyDecidedReason) {
        setExtensionEmptyReason('ALREADY_DECIDED')
      } else if (hasMissingRequestId) {
        setExtensionEmptyReason('MISSING_REQUEST_ID')
      } else if (hasNoContentWithoutReason) {
        setExtensionEmptyReason('UNSPECIFIED_NOT_AVAILABLE')
      } else if (candidates.length === 0 && baseCandidates.length > 0) {
        setExtensionEmptyReason('NO_TODAY_ELIGIBLE_TOUR')
      } else if (baseCandidates.length === 0 && allRequests.length > 0) {
        setExtensionEmptyReason('NO_ELIGIBLE_STATUS')
      } else if (allRequests.length === 0) {
        setExtensionEmptyReason('NO_REQUESTS')
      } else {
        setExtensionEmptyReason('')
      }
      return
    }
    setExtensionEmptyReason('')
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
    setActionApiErr('')
    try {
      const res = await apiRequest(`/matching/extensions/${requestId}/select`, {
        method: 'PATCH',
        json: { extend },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseMatchingApiError(text))
      await load()
    } catch (e) {
      setActionApiErr(e instanceof Error ? e.message : '연장 처리 실패')
    } finally {
      setBusy(false)
    }
  }

  const startExtensionPayment = async (requestId, extension) => {
    const amount = Number(extension?.extendedPrice)
    if (!Number.isFinite(amount) || amount <= 0) {
      setActionApiErr('연장 결제 금액을 확인할 수 없습니다. 가이드 비용 설정 후 다시 시도해 주세요.')
      return
    }
    setBusy(true)
    setToast('')
    setActionApiErr('')
    try {
      const selected = await apiRequest(`/matching/extensions/${requestId}/select`, {
        method: 'PATCH',
        json: { extend: true },
      })
      const selectedText = await selected.text()
      if (!selected.ok) throw new Error(parseMatchingApiError(selectedText))

      const res = await apiRequest('/matching/payments', {
        method: 'POST',
        json: {
          matchRequestId: requestId,
          amount,
          paymentType: 'EXTENSION',
        },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseMatchingApiError(text))
      const data = text ? JSON.parse(text) : {}
      const mr = requests.find((r) => r.requestId === requestId)
      const qs = new URLSearchParams()
      if (data.paymentId != null) qs.set('paymentId', String(data.paymentId))
      if (data.amount != null) qs.set('amount', String(data.amount))
      if (data.pgOrderNo) qs.set('pgOrderNo', String(data.pgOrderNo))
      if (data.paymentType) qs.set('paymentType', String(data.paymentType))
      qs.set('requestId', String(requestId))
      if (mr?.guideId != null) qs.set('guideId', String(mr.guideId))
      const redirect = data?.redirectUrl
      if (redirect && String(redirect).trim() !== '') {
        navigate(`/pay/kakao-stub?${qs.toString()}`, { state: { externalRedirect: String(redirect) } })
        return
      }
      navigate(`/pay/kakao-stub?${qs.toString()}`)
    } catch (e) {
      setActionApiErr(e instanceof Error ? e.message : '연장 결제 요청 실패')
      await load()
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
    setActionApiErr('')
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
      setActionApiErr(e instanceof Error ? e.message : '환불 실패')
    } finally {
      setBusy(false)
    }
  }

  const refundRows = payments
    .filter((p) => p.status === 'COMPLETED' || p.status === 'REFUNDED')
    .sort((a, b) => Number(b.paymentId || 0) - Number(a.paymentId || 0))

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
      {actionApiErr && (
        <PageError
          message={actionApiErr}
          onRetry={() => {
            setActionApiErr('')
            void refetch()
          }}
          retryLabel="목록 새로고침"
        >
          <Link to="/mypage/payments">결제 내역 보기</Link>
          {' · '}
          같은 오류가 반복되면 팀에 공유된 고객 지원 채널로 문의해 주세요.
        </PageError>
      )}
      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void refetch()} />}

      {!loading && !error && (
        <div className="mp-tour-shell">
          <section className="mp-tour-block">
            <h2>진행 예정 투어 연장</h2>
            <p className="sub">투어 당일 밤 9시부터 자정 전까지, 아쉬움이 남는다면 하루 더 로컬과 함께해요.</p>
            {Object.keys(extMap).length === 0 && (
              <PageEmpty title="진행 중인 연장 요청이 없습니다">
                {extensionEmptyReason === 'GUIDE_NEXT_DAY_UNAVAILABLE'
                  ? <span style={{ color: '#dc2626', fontWeight: 700 }}>{reasonGuideNames.unavailable || '가이드'}가 다음날 예약되어있거나 예약불가입니다.</span>
                  : extensionEmptyReason === 'GUIDE_NEXT_DAY_BOOKED'
                    ? <span style={{ color: '#dc2626', fontWeight: 700 }}>{reasonGuideNames.booked || '가이드'}가 다음날 예약되어있습니다.</span>
                    : extensionEmptyReason === 'GUIDE_NEXT_DAY_BLOCKED'
                      ? <span style={{ color: '#dc2626', fontWeight: 700 }}>{reasonGuideNames.blocked || '가이드'}가 다음날 예약불가입니다.</span>
                  : extensionEmptyReason === 'ALREADY_DECIDED'
                    ? '이미 연장 여부를 선택한 투어입니다. 연장 결제를 완료했거나, 연장 안 함을 선택해 다시 표시되지 않습니다.'
                    : extensionEmptyReason === 'NO_TODAY_ELIGIBLE_TOUR'
                      ? '오늘 날짜의 진행 대상 투어가 없어 연장 카드가 생성되지 않았습니다.'
                      : extensionEmptyReason === 'NO_ELIGIBLE_STATUS'
                        ? '현재 상태에서는 연장을 열 수 없습니다. (ACCEPTED/PAID/IN_PROGRESS/COMPLETED 상태만 대상)'
                        : extensionEmptyReason === 'MISSING_REQUEST_ID'
                          ? '요청 식별자(requestId) 정보가 누락되어 연장 정보를 조회할 수 없습니다.'
                          : extensionEmptyReason === 'NO_REQUESTS'
                            ? '게스트 계정에 연결된 매칭 요청이 아직 없어 연장 대상이 없습니다.'
                            : extensionEmptyReason === 'UNSPECIFIED_NOT_AVAILABLE'
                              ? '현재 투어는 연장 대상이 아니거나 이미 마감/정리되어 연장 카드를 표시할 수 없습니다.'
                  : '연장 안내가 오면 이 영역에 표시됩니다.'}
              </PageEmpty>
            )}
            <div className="mp-cards">
              {Object.entries(extMap).map(([requestId, ex]) => {
                const rid = Number(requestId)
                const mr = requests.find((r) => r.requestId === rid)
                const open = ex.status === 'REQUESTED' || ex.status === 'GUIDE_APPROVED'
                const left = hoursLeft(ex.deadlineAt)
                return (
                  <article key={rid} className="mp-tour-card">
                    <div className="mp-tour-row">
                      <div>
                        <p className="mp-tour-name">{mr?.guideNickname ?? '가이드'}와 함께하는 {mr?.destination ?? '로컬'} 투어</p>
                        <p className="mp-tour-meta">결제 일시: {formatDate(mr?.desiredDate)} · 상태: {ex.status}</p>
                        <p className="mp-tour-extension">연장 선택 가능</p>
                        <p className="mp-tour-fee">추가 비용: {formatMoney(ex.extendedPrice)} (선택 후 결제, 마감 {formatDateTime(ex.deadlineAt)})</p>
                      </div>
                      {open && (
                        <button type="button" className="mp-btn" disabled={busy} onClick={() => void startExtensionPayment(rid, ex)}>
                          연장 및 추가 결제하기
                        </button>
                      )}
                    </div>
                    {ex.status === 'REQUESTED' && (
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
            {refundRows.length === 0 && (
              <PageEmpty title="환불 가능한 결제가 없습니다">조건을 만족하는 결제가 있으면 여기에서 신청할 수 있어요.</PageEmpty>
            )}
            <div className="mp-cards">
              {refundRows.map((p) => {
                const isRefunded = p.status === 'REFUNDED'
                const canRefundNow = p.status === 'COMPLETED' && refundDeadlineOk(p)
                return (
                  <article key={p.paymentId} className="mp-tour-card">
                    <div className="mp-tour-row">
                      <div>
                        <p className="mp-tour-name">결제 #{p.paymentId} {isRefunded ? '환불 완료' : '완료 건 환불'}</p>
                        <p className="mp-tour-meta">결제 일시: {formatDateTime(p.paidAt)} · 주문 번호: {p.pgOrderNo ?? `ORD-${p.paymentId}`}</p>
                        {isRefunded ? (
                          <p className="mp-tour-extension" style={{ color: '#059669' }}>환불 완료</p>
                        ) : canRefundNow ? (
                          <p className="mp-tour-extension">🔵 환불 가능 (남은 시간: {timeLeftText(p.refundDeadline)})</p>
                        ) : (
                          <p className="mp-tour-extension">환불 가능 시간이 종료되었습니다.</p>
                        )}
                      </div>
                      {canRefundNow ? (
                        <button type="button" className="mp-btn mp-btn--danger" onClick={() => setRefundPaymentId(p.paymentId)}>
                          즉시 환불하기
                        </button>
                      ) : (
                        <button type="button" className="mp-btn mp-btn--line" disabled>
                          {isRefunded ? '환불 완료' : '환불 불가'}
                        </button>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          </section>

          <article className="mp-tour-notice">
            <p className="mp-tour-notice-title">📍 투어 관리 정책 안내</p>
            <ul>
              <li>연장 선택: 투어 당일 21:00~23:59 사이 선택 가능하며, 추가 결제 완료 시 가이드에게 최종 전달됩니다.</li>
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
