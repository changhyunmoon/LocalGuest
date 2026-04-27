import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import {
  daysUntil,
  fetchGuestMatchRequests,
  fetchGuestPayments,
  GUEST_CANCEL_WINDOW_MS,
  guestCanCancelByPolicy,
  latestCompletedPaymentPaidAtMs,
  loadGuideNicknames,
  pickLatestCompletedPaymentIdForRequest,
} from '../lib/matchingGuest.js'

import './GuestUpcomingTripsPage.css'

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

function ddayLabel(days, status) {
  if (status === 'IN_PROGRESS') return '진행중'
  if (days == null) return '일정 미정'
  if (days === 0) return 'D-Day'
  if (days > 0) return `D-${days}`
  return `${Math.abs(days)}일 지남`
}

/**
 * @param {{ status?: string }} row
 * @param {number | null} _paidAtMs
 * @param {boolean} canCancel
 */
function guestCancelDisabledTitle(row, _paidAtMs, canCancel) {
  if (canCancel) return undefined
  const st = String(row?.status ?? '').toUpperCase()
  if (st === 'IN_PROGRESS') return '진행 중인 투어는 이 화면에서 취소할 수 없어요.'
  if (st === 'PAID') return '예약 취소는 결제 완료 후 2시간 이내에만 가능해요.'
  return '취소할 수 없는 상태예요.'
}

function statusText(status) {
  const s = String(status ?? '').toUpperCase()
  if (s === 'PENDING') return '요청 대기'
  if (s === 'ACCEPTED') return '결제 전'
  if (s === 'PAID') return '결제 완료'
  if (s === 'IN_PROGRESS') return '진행 중'
  if (s === 'COMPLETED') return '여행 완료'
  if (s === 'CANCELLED') return '취소됨'
  if (s === 'REJECTED') return '거절됨'
  return '상태 확인 필요'
}

function statusBadgeClass(status) {
  const s = String(status ?? '').toUpperCase()
  if (s === 'PENDING') return 'gut-status-badge--pending'
  if (s === 'ACCEPTED') return 'gut-status-badge--pay'
  if (s === 'PAID') return 'gut-status-badge--paid'
  if (s === 'IN_PROGRESS') return 'gut-status-badge--live'
  return 'gut-status-badge--muted'
}

/** 짧은 한글 뱃지(카드 푸터) */
function statusBadgeLabel(status) {
  const s = String(status ?? '').toUpperCase()
  if (s === 'PENDING') return '가이드 응답 대기'
  if (s === 'ACCEPTED') return '결제 전'
  if (s === 'PAID') return '예약 확정'
  if (s === 'IN_PROGRESS') return '여행 진행 중'
  return statusText(status)
}

/** 한글 음절 받침 — 조사 와/과 */
function hasBatchimKo(char) {
  if (!char) return false
  const c = char.charCodeAt(0)
  if (c < 0xac00 || c > 0xd7a3) return false
  return (c - 0xac00) % 28 !== 0
}

function nicknameWithWaParticle(nick) {
  const n = String(nick ?? '').trim()
  if (!n) return '가이드와'
  const last = n[n.length - 1]
  return `${n}${hasBatchimKo(last) ? '과' : '와'}`
}

function shortDestinationLabel(dest) {
  const s = String(dest ?? '').trim()
  if (!s) return '로컬'
  if (/제주/i.test(s)) return '제주도'
  if (/강릉|속초|동해/i.test(s)) return '동해안'
  if (/부산/i.test(s)) return '부산'
  if (/서울/i.test(s)) return '서울'
  if (/경주/i.test(s)) return '경주'
  const first = s.split(/[\s,，/|]+/).filter(Boolean)[0] ?? s
  let cleaned = first.replace(/특별시|광역시|특별자치도/g, '').trim()
  if (cleaned.endsWith('시') && cleaned.length > 2) cleaned = cleaned.slice(0, -1)
  const w = cleaned || first
  return w.length > 10 ? `${w.slice(0, 10)}…` : w
}

const ACTIVITY_HINTS = [
  [/맛집|미식|먹거리|식도락|음식|식사/i, '맛집'],
  [/카페|커피|브런치/i, '카페'],
  [/야경|야경명소/i, '야경'],
  [/역사|유적|문화재|박물관/i, '역사'],
  [/한옥|전통\s*마을/i, '한옥마을'],
  [/숲|산책|둘레길|트레킹/i, '숲길'],
  [/해변|바다|해안/i, '바다'],
  [/쇼핑|마켓|시장/i, '쇼핑'],
  [/포토|사진|스냅/i, '포토'],
  [/골목|골목길/i, '골목'],
  [/힐링|여유|느긋/i, '힐링'],
]

function activityKeywordFromRow(row) {
  const pool = [row.conceptSummary, row.concept, row.proposeMessage, row.destination]
    .map((x) => String(x ?? ''))
    .join('\n')
  for (const [re, label] of ACTIVITY_HINTS) {
    if (re.test(pool)) return label
  }
  return '로컬'
}

/** @param {Record<string, unknown>} row */
function buildTripCardTitle(row, guideNickname) {
  const nick = String(guideNickname ?? '').trim() || '가이드'
  const dest = shortDestinationLabel(row.destination)
  const act = activityKeywordFromRow(row)
  return `${nicknameWithWaParticle(nick)} 함께하는 ${dest} ${act} 투어`
}

function formatCancelRemain(endsAtMs) {
  const ms = endsAtMs - Date.now()
  if (ms <= 0) return '0:00'
  const s = Math.ceil(ms / 1000)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

function groupRows(rows) {
  const paymentPending = []
  const inProgress = []
  const dday = []
  const d1 = []
  const upcoming = []
  const expired = []

  for (const row of rows) {
    const d = daysUntil(row.desiredDate)
    const st = String(row?.status ?? '').toUpperCase()
    const expiredPrePay = (st === 'PENDING' || st === 'ACCEPTED') && d != null && d < 0
    if (expiredPrePay) {
      expired.push(row)
      continue
    }
    if (row.status === 'ACCEPTED') {
      paymentPending.push(row)
      continue
    }
    if (row.status === 'IN_PROGRESS') inProgress.push(row)
    else if (d === 0) dday.push(row)
    else if (d === 1) d1.push(row)
    else upcoming.push(row)
  }

  return [
    { key: 'expired', title: '일정 지남 (결제 불가)', rows: expired },
    { key: 'payment', title: '결제 전 (확정 필요)', rows: paymentPending },
    { key: 'inprogress', title: '진행 중인 여행', rows: inProgress },
    { key: 'dday', title: '오늘 출발 (D-Day)', rows: dday },
    { key: 'd1', title: '내일 출발 (D-1)', rows: d1 },
    { key: 'upcoming', title: '예정된 로컬 만남', rows: upcoming },
  ].filter((section) => section.rows.length > 0)
}

export function GuestUpcomingTripsPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [names, setNames] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [paymentIdByRequest, setPaymentIdByRequest] = useState(() => ({}))
  const [payments, setPayments] = useState(/** @type {unknown[]} */ ([]))
  const [, setCancelTick] = useState(0)

  const reload = useCallback(async () => {
    const [all, paysRaw] = await Promise.all([
      fetchGuestMatchRequests(apiRequest),
      fetchGuestPayments(apiRequest).catch(() => []),
    ])
    const pays = Array.isArray(paysRaw) ? paysRaw : []
    const list = (Array.isArray(all) ? all : [])
      .filter((r) => UPCOMING.has(String(r.status ?? '')))
      .sort((a, b) => {
        const da = String(a.desiredDate ?? '')
        const db = String(b.desiredDate ?? '')
        if (da !== db) return da.localeCompare(db)
        return (a.requestId ?? 0) - (b.requestId ?? 0)
      })

    setRows(list)
    const nm = await loadGuideNicknames(apiRequest, list.map((r) => r.guideId))
    setNames(nm)

    const payMap = {}
    for (const r of list) {
      const pid = pickLatestCompletedPaymentIdForRequest(pays, r.requestId)
      if (pid != null) payMap[r.requestId] = pid
    }
    setPaymentIdByRequest(payMap)
    setPayments(pays)
  }, [])

  useEffect(() => {
    let cancelled = false
    void (async () => {
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

  const hasCancelCountdown = useMemo(() => {
    return rows.some((r) => {
      if (String(r.status) !== 'PAID') return false
      const pm = latestCompletedPaymentPaidAtMs(payments, r.requestId)
      return guestCanCancelByPolicy(r, pm)
    })
  }, [rows, payments])

  useEffect(() => {
    if (!hasCancelCountdown) return
    const id = window.setInterval(() => setCancelTick((c) => c + 1), 1000)
    return () => window.clearInterval(id)
  }, [hasCancelCountdown])

  useEffect(() => {
    if (!location.state?.matchRequestSubmitted) return
    setToast('매칭 요청이 등록됐어요.「일정 확인하기」를 누르면 가이드 응답·코스·지도를 이어서 볼 수 있어요.')
    navigate('/mypage/itinerary', { replace: true, state: {} })
  }, [location.state, navigate])

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

  const cancelTrip = useCallback(async (row) => {
    if (!row?.requestId) return
    const rid = Number(row.requestId)
    if (!Number.isFinite(rid) || rid <= 0) return
    const st = String(row.status ?? '').toUpperCase()
    if (!(st === 'PENDING' || st === 'ACCEPTED' || st === 'PAID')) return

    const reason = window.prompt('취소 사유를 입력해 주세요.', '개인 사정으로 일정이 변경되었습니다.')
    if (!reason || !String(reason).trim()) return
    if (!window.confirm(`요청 #${rid}을(를) 취소할까요?`)) return

    setBusyId(rid)
    setToast('')
    try {
      const res = await apiRequest(`/matching/requests/${rid}/guest/cancel`, {
        method: 'PATCH',
        json: { cancelReason: String(reason).trim() },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(text || '취소 실패')
      setToast('취소했습니다.')
      await reload()
    } catch (e) {
      setToast(e instanceof Error ? e.message : '취소 실패')
    } finally {
      setBusyId(null)
    }
  }, [reload])

  const sections = useMemo(() => groupRows(rows), [rows])
  const totalCount = rows.length

  return (
    <div className="gut-page">
      <header className="gut-hero">
        <div className="gut-hero-badge">설렘 ON · {totalCount}개의 여행</div>
        <h1>내 여행 일정 한눈에 보기</h1>
        <p>가까운 일정부터 D-Day 순서로 정리했어요. 다음 여행을 눌러 바로 상세/매칭 화면으로 이동해 보세요.</p>
      </header>

      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void reload()} />}
      {!loading && !error && toast && <p className="gut-toast" role="status">{toast}</p>}

      {!loading && !error && rows.length === 0 && (
        <PageEmpty title="예정된 여행이 없습니다">진행 중이거나 예정된 매칭이 생기면 여기에 표시됩니다.</PageEmpty>
      )}

      {!loading && !error && rows.length > 0 && (
        <div className="gut-sections">
          {sections.map((section) => (
            <section key={section.key} className="gut-section">
              <div className="gut-section-head">
                <h2>{section.title}</h2>
                <span>{section.rows.length}건</span>
              </div>

              <div className="gut-cards">
                {section.rows.map((row) => {
                  const d = daysUntil(row.desiredDate)
                  const nick = names[row.guideId] ?? `가이드 #${row.guideId}`
                  const budget = formatBudgetRangeKrw(row.budgetMinWon, row.budgetMaxWon, row.desiredBudget)
                  const tripTitle = buildTripCardTitle(row, nick)
                  const paidAtMs = latestCompletedPaymentPaidAtMs(payments, row.requestId)
                  const st = String(row?.status ?? '').toUpperCase()
                  const expiredPrePay = (st === 'PENDING' || st === 'ACCEPTED') && d != null && d < 0
                  const canCancel = !expiredPrePay && guestCanCancelByPolicy(row, paidAtMs)
                  const cancelDisabledTitle = guestCancelDisabledTitle(row, paidAtMs, canCancel)
                  const cancelEnds =
                    String(row.status) === 'PAID' && paidAtMs != null
                      ? paidAtMs + GUEST_CANCEL_WINDOW_MS
                      : null
                  const cancelRemainStr =
                    cancelEnds != null && canCancel ? formatCancelRemain(cancelEnds) : null
                  return (
                    <article key={row.requestId} className="gut-card gut-card--timeline">
                      <div className="gut-card-split">
                        <div className="gut-card-text">
                          <div className="gut-card-hero-row">
                            <span className="gut-date gut-date--hero">{row.desiredDate ?? '—'}</span>
                          </div>
                          <h3 className="gut-title" title={tripTitle}>{tripTitle}</h3>
                          <p className="gut-line gut-line--sub">예산 {budget}</p>
                          {String(row.status) === 'PAID' && paidAtMs != null && canCancel && cancelRemainStr && (
                            <p className="gut-cancel-remain" role="status">
                              취소 가능 <strong>{cancelRemainStr}</strong> 남음 (결제 완료 후 2시간)
                            </p>
                          )}
                          {String(row.status) === 'PAID' && paidAtMs != null && !canCancel && (
                            <p className="gut-cancel-closed" role="status">
                              예약 취소는 결제 완료 후 2시간 이내에만 가능해요.
                            </p>
                          )}
                          <div className="gut-card-footer">
                            <span
                              className={`gut-status-badge ${expiredPrePay ? 'gut-status-badge--muted' : statusBadgeClass(row.status)}`}
                              role="status"
                            >
                              {expiredPrePay ? '일정 지남' : statusBadgeLabel(row.status)}
                            </span>
                            <div className="gut-card-actions">
                              {!expiredPrePay && (
                                <button
                                  type="button"
                                  className="gut-cancel"
                                  disabled={busyId != null || !canCancel}
                                  title={cancelDisabledTitle}
                                  onClick={() => {
                                    if (!canCancel) return
                                    void cancelTrip(row)
                                  }}
                                >
                                  {busyId === row.requestId ? '취소 중…' : '취소하기'}
                                </button>
                              )}
                              <button type="button" className="gut-open" onClick={() => openMatchScreen(row)}>
                                일정 확인하기
                              </button>
                            </div>
                          </div>
                        </div>
                        <div className="gut-card-preview-wrap">
                          <div className="gut-cal-peek" aria-hidden="true">
                            <div className="gut-cal-peek__spring" />
                            <div
                              className={`gut-trip-polaroid gut-trip-polaroid--cal${d === 1 ? ' is-d1' : d === 0 ? ' is-dday' : ''}`}
                              role="img"
                              aria-label={ddayLabel(d, row.status)}
                            >
                              <div className="gut-cal-peek__grid" />
                              <span className="gut-trip-polaroid__tape" />
                              <span
                                className={`gut-trip-polaroid__dday${d === 0 ? ' is-today' : d === 1 ? ' is-soon' : ''}`}
                              >
                                {ddayLabel(d, row.status)}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </article>
                  )
                })}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

