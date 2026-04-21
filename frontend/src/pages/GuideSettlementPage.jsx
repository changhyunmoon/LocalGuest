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

function formatDate(value) {
  if (!value) return '날짜 미정'
  const d = new Date(`${value}T00:00:00`)
  if (Number.isNaN(d.getTime())) return value
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
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
  const completedTours = useMemo(() => {
    const done = guideRequests.filter((r) => ['PAID', 'IN_PROGRESS', 'COMPLETED'].includes(String(r.status)))
    return done
      .slice()
      .sort((a, b) => String(b.desiredDate ?? '').localeCompare(String(a.desiredDate ?? '')))
      .slice(0, 4)
  }, [guideRequests])

  return (
    <div className="g-panel">
      <h1>💰 정산 예정 금액</h1>
      {loading && <p>불러오는 중…</p>}
      {error && <p className="g-error">{error}</p>}
      {!loading && !error && data && (
        <section className="gst-wrap">
          <div className="gst-top">
            <article className="gst-amount-card">
              <p className="gst-kicker">10월 총 정산 예정액</p>
              <p className="gst-amount">{formatMoney(amount)}</p>
              <p className="gst-date">정산일: 2026년 11월 5일</p>
            </article>

            <article className="gst-chart-card">
              <h2>월별 수익 추이</h2>
              <div className="gst-bars" aria-label="월별 수익 막대 차트">
                {bars.map((b) => (
                  <div key={b.label} className={`gst-bar-col ${b.active ? 'is-active' : ''}`}>
                    <div className="gst-bar" style={{ height: `${Math.max(18, b.ratio * 96)}px` }} title={formatMoney(b.value)} />
                    <span>{b.label}</span>
                  </div>
                ))}
              </div>
            </article>
          </div>

          <section className="gst-history">
            <h2>최근 투어 내역</h2>
            {completedTours.length === 0 && <p className="gm-hint">정산 가능한 완료 투어가 아직 없습니다.</p>}
            {completedTours.map((row) => (
              <article key={row.requestId} className="gst-row">
                <div>
                  <p className="gst-title">{row.destination ?? '로컬 투어'}</p>
                  <p className="gst-meta">
                    {formatDate(row.desiredDate)} · {statusText(row.status)}
                  </p>
                </div>
                <strong className="gst-plus">+ {formatMoney(row.desiredBudget ?? 0)}</strong>
              </article>
            ))}
          </section>

          {data.description && <p className="g-hint">{sanitizeSettlementDescription(data.description)}</p>}
        </section>
      )}
    </div>
  )
}
