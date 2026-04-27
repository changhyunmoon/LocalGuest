import { useEffect, useMemo, useState } from 'react'

import { apiRequest } from '../api/client'
import { resolveGuideId } from '../lib/guideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'

function formatMoney(v) {
  if (v == null) return '₩ 0'
  return `₩ ${Number(v).toLocaleString('ko-KR')}`
}

function formatTableDate(value) {
  if (!value) return '—'
  const d = new Date(`${value}T00:00:00`)
  if (Number.isNaN(d.getTime())) return String(value)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}.${m}.${day}`
}

function nextSettlementDepositLine() {
  const now = new Date()
  const next = new Date(now.getFullYear(), now.getMonth() + 1, 5)
  return `${next.getFullYear()}년 ${next.getMonth() + 1}월 5일 입금 예정`
}

function monthLabel(offset) {
  const d = new Date()
  d.setMonth(d.getMonth() + offset)
  return `${d.getMonth() + 1}월`
}

function buildMonthlyBars(amount) {
  const total = Number(amount ?? 0)
  if (!Number.isFinite(total) || total <= 0) {
    return [
      { label: monthLabel(-3), value: 0, ratio: 0.25, active: false },
      { label: monthLabel(-2), value: 0, ratio: 0.5, active: false },
      { label: monthLabel(-1), value: 0, ratio: 0.35, active: false },
      { label: monthLabel(0), value: 0, ratio: 0.8, active: true },
    ]
  }
  const base = [0.52, 0.76, 0.38, 1]
  return base.map((r, idx) => ({
    label: monthLabel(idx - 3),
    value: Math.round(total * r),
    ratio: r,
    active: idx === 3,
  }))
}

function sanitizeSettlementDescription(raw) {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  return text
    .replace(/\(COMPLETED\)/gi, '')
    .replace(/COMPLETED/gi, '')
    .replace(/\(플랫폼\s*수수료·정산\s*규칙\s*적용\s*전\)/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

function statusText(status) {
  const s = String(status ?? '').toUpperCase()
  if (s === 'PAID') return '결제 완료'
  if (s === 'IN_PROGRESS') return '투어 진행 중'
  if (s === 'COMPLETED') return '여행 완료'
  if (s === 'ACCEPTED') return '예약 확정'
  if (s === 'PENDING') return '요청 대기'
  return s || '상태 확인중'
}

export function GuideSettlementPage() {
  const [data, setData] = useState(null)
  const [guideRequests, setGuideRequests] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const id = await resolveGuideId(apiRequest)
        if (!id) {
          throw new Error('가이드 ID를 확인할 수 없습니다.')
        }
        const [res, guideReqs] = await Promise.all([
          apiRequest(`/guides/${id}/settlement/expected`, { method: 'GET' }),
          fetchGuideMatchRequests(apiRequest),
        ])
        const text = await res.text()
        if (!res.ok) {
          try {
            const j = JSON.parse(text)
            throw new Error(j.message ?? '조회 실패')
          } catch (e) {
            if (e instanceof Error && e.message !== '조회 실패') throw e
            throw new Error(text || '조회 실패')
          }
        }
        const body = text ? JSON.parse(text) : null
        if (!cancelled) setData(body)
        if (!cancelled) setGuideRequests(Array.isArray(guideReqs) ? guideReqs : [])
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const amount = data?.expectedAmount != null ? Number(data.expectedAmount) : 0
  const bars = useMemo(() => buildMonthlyBars(amount), [amount])
  const currentMonthLabel = useMemo(() => `${new Date().getMonth() + 1}월`, [])
  const cumulativeTourSum = useMemo(
    () =>
      guideRequests
        .filter((r) => ['PAID', 'IN_PROGRESS', 'COMPLETED'].includes(String(r.status)))
        .reduce((acc, r) => acc + Number(r.desiredBudget ?? 0), 0),
    [guideRequests],
  )
  const settlementRows = useMemo(() => {
    const statuses = ['PAID', 'IN_PROGRESS', 'COMPLETED', 'ACCEPTED']
    return guideRequests
      .filter((r) => statuses.includes(String(r.status)))
      .slice()
      .sort((a, b) => String(b.desiredDate ?? '').localeCompare(String(a.desiredDate ?? '')))
      .slice(0, 12)
  }, [guideRequests])

  return (
    <div className="g-panel">
      <h1 className="gst-page-h1">💰 정산 · 수익</h1>
      {loading && <p>불러오는 중…</p>}
      {error && <p className="g-error">{error}</p>}
      {!loading && !error && data && (
        <section className="gst-wrap gst-ledger">
          <header className="gst-ledger-head gst-paper">
            <div>
              <h2 className="gst-ledger-title">이번 달 정산부</h2>
              <p className="gst-ledger-lead">활동으로 쌓인 매칭·투어 기준의 수익 현황입니다.</p>
            </div>
          </header>

          <div className="gst-summary">
            <article className="gst-sum-card gst-sum-card--rose gst-paper">
              <span className="gst-sum-deco" aria-hidden>
                ↗
              </span>
              <p className="gst-sum-kicker">{currentMonthLabel} 정산 예정액</p>
              <p className="gst-sum-amount">{formatMoney(amount)}</p>
              <p className="gst-sum-foot">
                <span className="gst-sum-foot-ico" aria-hidden>
                  ◷
                </span>
                {nextSettlementDepositLine()}
              </p>
            </article>

            <article className="gst-sum-card gst-sum-card--emerald gst-paper">
              <p className="gst-sum-kicker gst-sum-kicker--em">누적 투어 예산 합계</p>
              <p className="gst-sum-amount gst-sum-amount--dark">{formatMoney(cumulativeTourSum)}</p>
              <p className="gst-sum-foot">완료·진행·결제 완료 매칭의 희망 예산 합산</p>
            </article>

            <article className="gst-sum-card gst-sum-card--slate gst-paper">
              <p className="gst-sum-kicker gst-sum-kicker--muted">플랫폼 정산 안내</p>
              <p className="gst-sum-amount gst-sum-amount--sm">수수료 10%</p>
              <p className="gst-sum-foot gst-sum-foot--accent">실입금은 규정에 따라 공제 후 지급됩니다.</p>
            </article>
          </div>

          <section className="gst-chart-strip gst-paper" aria-labelledby="gst-chart-heading">
            <h3 id="gst-chart-heading" className="gst-chart-strip-title">
              월별 수익 추이
            </h3>
            <div className="gst-bars gst-bars--ledger" aria-label="월별 수익 막대 차트">
              {bars.map((b) => (
                <div key={b.label} className={`gst-bar-col ${b.active ? 'is-active' : ''}`}>
                  <div className="gst-bar" style={{ height: `${Math.max(18, b.ratio * 96)}px` }} title={formatMoney(b.value)} />
                  <span>{b.label}</span>
                </div>
              ))}
            </div>
          </section>

          <section className="gst-detail" aria-labelledby="gst-detail-heading">
            <h3 id="gst-detail-heading" className="gst-detail-title">
              <span className="gst-detail-ico" aria-hidden>
                ≡
              </span>
              상세 정산 내역
            </h3>

            <div className="gst-table-shell gst-paper">
              {settlementRows.length === 0 ? (
                <p className="gst-table-empty gm-hint">표시할 매칭·투어 내역이 아직 없습니다.</p>
              ) : (
                <div className="gst-table-scroll">
                  <table className="gst-table">
                    <thead>
                      <tr>
                        <th scope="col">날짜</th>
                        <th scope="col">항목</th>
                        <th scope="col" className="gst-th-num">
                          금액
                        </th>
                        <th scope="col" className="gst-th-status">
                          상태
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {settlementRows.map((row) => (
                        <tr key={row.requestId}>
                          <td className="gst-td-date">{formatTableDate(row.desiredDate)}</td>
                          <td className="gst-td-title">{row.destination ?? '로컬 투어'}</td>
                          <td className="gst-td-amt">{formatMoney(row.desiredBudget ?? 0)}</td>
                          <td className="gst-td-status">
                            <span className="gst-chip">{statusText(row.status)}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="gst-callout" role="note">
              <span className="gst-callout-ico" aria-hidden>
                !
              </span>
              <p>
                <strong>플랫폼 이용료(10%)가 공제된 금액일 수 있습니다.</strong>
                <br />
                {data.description
                  ? sanitizeSettlementDescription(data.description)
                  : '정산 관련 문의는 고객 센터 1:1 문의를 이용해 주세요.'}
              </p>
            </div>
          </section>
        </section>
      )}
    </div>
  )
}
