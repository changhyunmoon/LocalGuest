import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useGuidePendingRequests } from '../context/GuidePendingRequestsProvider.jsx'
import { useAuth } from '../context/useAuth.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import './GuideInboxPage.css'

async function readJsonError(res, text) {
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function statusLabel(status) {
  const map = {
    PENDING:     { label: '대기중',      bg: '#fef9c3', color: '#854d0e' },
    ACCEPTED:    { label: '제시안 전송됨', bg: '#dbeafe', color: '#1e40af' },
    REJECTED:    { label: '거절됨',      bg: '#fee2e2', color: '#991b1b' },
    PAID:        { label: '결제완료',    bg: '#ede9fe', color: '#6d28d9' },
    COMPLETED:   { label: '완료',        bg: '#d1fae5', color: '#065f46' },
    IN_PROGRESS: { label: '투어 진행중', bg: '#fef9c3', color: '#854d0e' },
    CANCELLED:   { label: '취소됨',      bg: '#f3f4f6', color: '#6b7280' },
  }
  const s = map[status] ?? { label: status, bg: '#f3f4f6', color: '#374151' }
  return (
    <span style={{ fontSize: '0.76rem', fontWeight: 700, padding: '0.2rem 0.55rem', borderRadius: 999, background: s.bg, color: s.color, whiteSpace: 'nowrap' }}>
      {s.label}
    </span>
  )
}

function formatScheduleTime(t) {
  if (t == null) return '00:00'
  if (typeof t === 'string') return t.length >= 5 ? t.slice(0, 5) : t
  return String(t)
}

function formatBudgetRange(minWon, maxWon) {
  const min = minWon != null && minWon !== '' && !Number.isNaN(Number(minWon)) ? Number(minWon) : null
  const max = maxWon != null && maxWon !== '' && !Number.isNaN(Number(maxWon)) ? Number(maxWon) : null
  if (min == null && max == null) return null
  if (min != null && max != null) {
    return `₩${min.toLocaleString('ko-KR')}~${max.toLocaleString('ko-KR')}`
  }
  const only = min ?? max
  return `₩${Number(only).toLocaleString('ko-KR')}`
}

function formatBudget(value) {
  return value ? `₩${Number(value).toLocaleString('ko-KR')}` : '—'
}

const STATUS_SECTIONS = [
  { key: 'PENDING', label: '대기중', statuses: ['PENDING'] },
  { key: 'IN_PROGRESS', label: '투어 진행중', statuses: ['IN_PROGRESS'] },
  { key: 'ACCEPTED', label: '제시안 전송', statuses: ['ACCEPTED'] },
  { key: 'PAID', label: '결제완료', statuses: ['PAID'] },
  { key: 'REJECTED', label: '거절', statuses: ['REJECTED', 'CANCELLED'] },
]

export function GuideInboxPage() {
  const navigate = useNavigate()
  const { isGuide } = useAuth()
  const { refresh: refreshPendingBadge } = useGuidePendingRequests()
  const { guideId } = useResolvedGuideId()
  const [rows, setRows] = useState([])
  const [schedulesById, setSchedulesById] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [toast, setToast] = useState('')

  const fetchList = useCallback(async () => {
    setError('')
    setToast('')
    const data = await fetchGuideMatchRequests(apiRequest)
    setRows(Array.isArray(data) ? data : [])
    void refreshPendingBadge()
  }, [refreshPendingBadge])

  useEffect(() => {
    if (!isGuide) {
      setLoading(false)
      setRows([])
      return
    }
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await fetchList()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [isGuide, fetchList])

  const reject = async (requestId) => {
    if (!window.confirm('이 예약 요청을 거절할까요?')) return
    setBusyId(requestId)
    setToast('')
    try {
      const res = await apiRequest(`/matching/requests/${requestId}/reject`, { method: 'PATCH' })
      const text = await res.text()
      if (!res.ok) {
        setToast(await readJsonError(res, text))
        return
      }
      await fetchList()
    } catch {
      setToast('거절 요청 실패')
    } finally {
      setBusyId(null)
    }
  }

  const loadSchedules = useCallback(async () => {
    if (!guideId) {
      setSchedulesById({})
      return
    }
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) {
        setSchedulesById({})
        return
      }
      const schedules = text ? JSON.parse(text) : []
      const map = {}
      for (const schedule of Array.isArray(schedules) ? schedules : []) {
        if (schedule?.scheduleId != null) {
          map[Number(schedule.scheduleId)] = schedule
        }
      }
      setSchedulesById(map)
    } catch {
      setSchedulesById({})
    }
  }, [guideId])

  useEffect(() => {
    void loadSchedules()
  }, [loadSchedules])

  const groupedRows = useMemo(() => {
    const sections = STATUS_SECTIONS.map((section) => ({
      ...section,
      rows: rows.filter((row) => section.statuses.includes(String(row.status ?? ''))),
    }))
    const included = new Set(STATUS_SECTIONS.flatMap((section) => section.statuses))
    const others = rows.filter((row) => !included.has(String(row.status ?? '')))
    const rejectedSection = sections.find((section) => section.key === 'REJECTED')
    const mainSections = sections.filter((section) => section.key !== 'REJECTED' && section.rows.length > 0)
    if (others.length > 0) mainSections.push({ key: 'OTHER', label: '기타', statuses: [], rows: others })
    if (rejectedSection?.rows.length) mainSections.push(rejectedSection)
    return mainSections
  }, [rows])

  const openCourseWriter = async (r) => {
    if (guideId == null) {
      setToast('가이드 프로필 정보를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.')
      return
    }
    const sid = Number(r.scheduleId)
    if (Number.isNaN(sid)) {
      setToast('연결된 스케줄이 없어 코스를 작성할 수 없습니다.')
      return
    }
    const linkedSchedule = schedulesById[sid]
    if (linkedSchedule?.status === 'PENDING') {
      setBusyId(r.requestId)
      try {
        const acceptRes = await apiRequest(`/guides/${guideId}/schedules/${sid}/accept`, { method: 'POST' })
        const acceptText = await acceptRes.text()
        if (!acceptRes.ok) {
          if (acceptRes.status !== 409) {
            setToast(await readJsonError(acceptRes, acceptText))
            return
          }
        }
        await loadSchedules()
      } catch {
        setToast('코스 작성 준비 중 오류가 발생했습니다.')
        return
      } finally {
        setBusyId(null)
      }
    }
    const schedulePayload = {
      scheduleId: sid,
      availableDate: linkedSchedule?.availableDate ?? r.desiredDate ?? '',
      startTime: formatScheduleTime(linkedSchedule?.startTime ?? '00:00'),
      endTime: formatScheduleTime(linkedSchedule?.endTime ?? '23:59'),
      destination: r.destination ?? '',
    }
    const params = new URLSearchParams()
    params.set('scheduleId', String(sid))
    params.set('propose', '1')
    if (schedulePayload.availableDate) params.set('date', String(schedulePayload.availableDate))
    params.set('startTime', schedulePayload.startTime)
    params.set('endTime', schedulePayload.endTime)
    if (schedulePayload.destination) params.set('destination', String(schedulePayload.destination))
    navigate(`/guide/requests/${r.requestId}/course-editor?${params.toString()}`, {
      state: { schedule: schedulePayload, proposeAfterSave: true },
    })
  }

  if (!isGuide) {
    return (
      <div className="inbox">
        <h1>매칭 요청</h1>
        <p className="inbox-warn">
          이 화면은 JWT 역할이 <strong>GUIDE</strong>일 때만 API가 허용합니다. 가이드 신청 직후라면{' '}
          <strong>다시 로그인</strong>한 뒤 이용해 주세요.
        </p>
        <p>
          <Link to="/auth/login">로그인</Link> · <Link to="/guide/register">가이드 신청</Link>
        </p>
      </div>
    )
  }

  return (
    <div className="inbox">
      <h1>매칭 요청</h1>

      {loading && <p>불러오는 중…</p>}
      {error && <p className="inbox-error">{error}</p>}
      {toast && <p className="inbox-error">{toast}</p>}

      {!loading && !error && rows.length === 0 && (
        <div className="inbox-empty">
          <span className="inbox-empty-icon">🤝</span>
          <p className="inbox-empty-title">받은 예약 요청이 없습니다</p>
          <p className="inbox-empty-desc">게스트가 매칭을 요청하면 여기에 표시됩니다</p>
        </div>
      )}

      {!loading && !error && rows.length > 0 && (
        <div className="inbox-status-sections">
          {groupedRows.map((section) => (
            <section
              key={section.key}
              className={`inbox-status-section${section.key === 'IN_PROGRESS' ? ' inbox-status-section--inprogress' : ''}${section.key === 'PENDING' ? ' inbox-status-section--pending' : ''}`}
              aria-label={`${section.label} 요청`}
            >
              <header className="inbox-status-head">
                <h2 className="inbox-section-title">{section.label}</h2>
                <span className="inbox-status-count">{section.rows.length}건</span>
              </header>

              <div className="inbox-table-wrap">
                <table className="inbox-table">
                  <thead>
                    <tr>
                      <th>상태</th>
                      <th>게스트</th>
                      <th>목적지</th>
                      <th>희망일</th>
                      <th>예산</th>
                      <th>동작</th>
                    </tr>
                  </thead>
                  <tbody>
                    {section.rows.map((r) => (
                      <tr key={r.requestId}>
                        <td>{statusLabel(r.status)}</td>
                        <td>{`게스트 #${r.guestId}`}</td>
                        <td>{r.destination ?? '—'}</td>
                        <td>{r.desiredDate ?? '—'}</td>
                        <td>{formatBudgetRange(r.budgetMinWon, r.budgetMaxWon) ?? formatBudget(r.desiredBudget)}</td>
                        <td className="inbox-actions">
                          {r.status === 'PENDING' && (
                            <>
                              <button type="button" className="inbox-btn" onClick={() => openCourseWriter(r)} disabled={busyId != null}>
                                코스 작성하기
                              </button>
                              <button
                                type="button"
                                className="inbox-btn inbox-btn--danger"
                                onClick={() => void reject(r.requestId)}
                                disabled={busyId != null}
                              >
                                거절
                              </button>
                            </>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="inbox-cards" aria-label={`${section.label} 카드 목록`}>
                {section.rows.map((r) => (
                  <article
                    key={`card-${r.requestId}`}
                    className="inbox-card"
                    style={['REJECTED', 'CANCELLED', 'COMPLETED'].includes(r.status) ? { opacity: 0.55 } : undefined}
                  >
                    <header className="inbox-card-head">
                      <span className="inbox-card-id">#{r.requestId}</span>
                      {statusLabel(r.status)}
                    </header>
                    <p className="inbox-card-line">👤 <strong>게스트</strong> {`게스트 #${r.guestId}`}</p>
                    <p className="inbox-card-line">📍 <strong>목적지</strong> {r.destination ?? '—'}</p>
                    <p className="inbox-card-line">📅 <strong>희망일</strong> {r.desiredDate ?? '—'}</p>
                    <p className="inbox-card-line">
                      💰 <strong>예산</strong> {formatBudgetRange(r.budgetMinWon, r.budgetMaxWon) ?? formatBudget(r.desiredBudget)}
                    </p>
                    {r.conceptSummary && (
                      <p className="inbox-card-line">🗺️ <strong>여행 컨셉</strong> {r.conceptSummary}</p>
                    )}
                    {r.createdAt && (
                      <p className="inbox-card-line">🕐 <strong>요청일</strong> {new Date(r.createdAt).toLocaleDateString('ko-KR')}</p>
                    )}
                    {r.status === 'ACCEPTED' && (
                      <p className="inbox-proposed-hint">✉️ 제시안을 전송했습니다. 게스트가 수락하면 결제로 진행됩니다.</p>
                    )}
                    {r.status === 'PENDING' && (
                      <div className="inbox-card-actions">
                        <button type="button" className="inbox-btn" onClick={() => openCourseWriter(r)} disabled={busyId != null}>
                          코스 작성하기
                        </button>
                        <button
                          type="button"
                          className="inbox-btn inbox-btn--danger"
                          onClick={() => void reject(r.requestId)}
                          disabled={busyId != null}
                        >
                          거절
                        </button>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

    </div>
  )
}
