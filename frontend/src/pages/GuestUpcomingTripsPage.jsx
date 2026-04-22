import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import {
  daysUntil,
  fetchGuestMatchRequests,
  fetchGuestPayments,
  loadGuideNicknames,
  pickLatestCompletedPaymentIdForRequest,
} from '../lib/matchingGuest.js'

import './GuestUpcomingTripsPage.css'

const UPCOMING = new Set(['PENDING', 'ACCEPTED', 'PAID', 'IN_PROGRESS'])

function moodEmoji(status, d) {
  if (status === 'IN_PROGRESS') return '🧳'
  if (status === 'ACCEPTED') return '🧾'
  if (d === 0) return '🚀'
  if (d === 1) return '⏳'
  return '✨'
}

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

function groupRows(rows) {
  const paymentPending = []
  const inProgress = []
  const dday = []
  const d1 = []
  const upcoming = []

  for (const row of rows) {
    if (row.status === 'ACCEPTED') {
      paymentPending.push(row)
      continue
    }
    const d = daysUntil(row.desiredDate)
    if (row.status === 'IN_PROGRESS') inProgress.push(row)
    else if (d === 0) dday.push(row)
    else if (d === 1) d1.push(row)
    else upcoming.push(row)
  }

  return [
    { key: 'payment', title: '결제 전 (확정 필요)', rows: paymentPending },
    { key: 'inprogress', title: '진행 중인 여행', rows: inProgress },
    { key: 'dday', title: '오늘 출발 (D-Day)', rows: dday },
    { key: 'd1', title: '내일 출발 (D-1)', rows: d1 },
    { key: 'upcoming', title: '다가오는 여행 일정', rows: upcoming },
  ].filter((section) => section.rows.length > 0)
}

export function GuestUpcomingTripsPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [names, setNames] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [paymentIdByRequest, setPaymentIdByRequest] = useState(() => ({}))

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
                  const dest = row.destination ?? '로컬 투어'
                  const budget = formatBudgetRangeKrw(row.budgetMinWon, row.budgetMaxWon, row.desiredBudget)
                  return (
                    <article key={row.requestId} className="gut-card gut-card--timeline">
                      <div className="gut-card-top">
                        <span className="gut-spot" aria-hidden="true">{moodEmoji(row.status, d)}</span>
                        <div className="gut-pills">
                          <span className="gut-pill gut-pill--dest">{dest}</span>
                          <span className="gut-pill gut-pill--status">{statusText(row.status)}</span>
                        </div>
                      </div>
                      <div className="gut-meta">
                        <span className={`gut-dday${d === 0 ? ' is-today' : d === 1 ? ' is-soon' : ''}`}>
                          {row.status === 'ACCEPTED' ? '결제 필요' : ddayLabel(d, row.status)}
                        </span>
                        <h3>{nick}</h3>
                        <p className="gut-line">{row.desiredDate ?? '—'}</p>
                        <p className="gut-line gut-line--sub">예산 {budget}</p>
                      </div>
                      <button type="button" className="gut-open" onClick={() => openMatchScreen(row)}>
                        일정 확인하기
                      </button>
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

