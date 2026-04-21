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
    { key: 'payment', title: '결제하고 확정 필요', rows: paymentPending },
    { key: 'inprogress', title: '지금 진행 중', rows: inProgress },
    { key: 'dday', title: '오늘 출발 (D-Day)', rows: dday },
    { key: 'd1', title: '내일 출발 (D-1)', rows: d1 },
    { key: 'upcoming', title: '다가오는 일정', rows: upcoming },
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

  return (
    <div className="gut-page">
      <header className="gut-hero">
        <h1>📆 앞으로의 여행 일정</h1>
        <p>다가오는 투어를 D-Day 기준으로 빠르게 확인하고, 바로 매칭 상세로 이동할 수 있어요.</p>
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
                  return (
                    <article key={row.requestId} className="gut-card">
                      <div className="gut-meta">
                        <span className={`gut-dday${d === 0 ? ' is-today' : d === 1 ? ' is-soon' : ''}`}>
                          {row.status === 'ACCEPTED' ? '결제 필요' : ddayLabel(d, row.status)}
                        </span>
                        <h3>{nick}</h3>
                        <p>{row.destination ?? '로컬 투어'} · {row.desiredDate ?? '—'}</p>
                        <p>
                          상태: {row.status} · 예산: {formatBudgetRangeKrw(row.budgetMinWon, row.budgetMaxWon, row.desiredBudget)}
                        </p>
                      </div>
                      <button type="button" className="gut-open" onClick={() => openMatchScreen(row)}>
                        상세 보기
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

