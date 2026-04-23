import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

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
    PENDING: { label: '대기중', tone: 'pending' },
    ACCEPTED: { label: '제시안 전송됨', tone: 'accepted' },
    REJECTED: { label: '거절됨', tone: 'rejected' },
    PAID: { label: '결제완료', tone: 'paid' },
    COMPLETED: { label: '완료', tone: 'completed' },
    IN_PROGRESS: { label: '투어 진행중', tone: 'progress' },
    CANCELLED: { label: '취소됨', tone: 'cancelled' },
  }
  const s = map[status] ?? { label: status, tone: 'default' }
  return (
    <span className={`inbox-status-chip inbox-status-chip--${s.tone}`}>
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
  if (value == null || value === '' || Number.isNaN(Number(value))) return '—'
  return `₩${Number(value).toLocaleString('ko-KR')}`
}

function compactDnaSummary(value) {
  if (value == null) return '—'
  const text = String(value).replace(/\s+/g, ' ').trim()
  return text || '—'
}

async function tryFetchExtension(requestId) {
  if (requestId == null) return null
  try {
    const res = await apiRequest(`/matching/extensions/${requestId}`, { method: 'GET' })
    const text = await res.text()
    if (!res.ok) return null
    return text ? JSON.parse(text) : null
  } catch {
    return null
  }
}

function extensionStatusLabel(status) {
  const map = {
    REQUESTED: '연장 요청',
    GUIDE_APPROVED: '연장 결제 대기',
    PAID: '연장 결제 완료',
  }
  return (
    <span className="inbox-status-chip inbox-status-chip--extension">
      {map[String(status)] ?? '연장'}
    </span>
  )
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
  const [extensionsByRequestId, setExtensionsByRequestId] = useState({})
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

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!isGuide || rows.length === 0) {
        if (!cancelled) setExtensionsByRequestId({})
        return
      }
      const entries = await Promise.all(
        rows.map(async (r) => {
          const ext = await tryFetchExtension(r.requestId)
          if (!ext) return null
          return [r.requestId, ext]
        }),
      )
      if (cancelled) return
      const m = {}
      for (const e of entries) {
        if (e) m[e[0]] = e[1]
      }
      setExtensionsByRequestId(m)
    })()
    return () => {
      cancelled = true
    }
  }, [isGuide, rows])

  const groupedRows = useMemo(() => {
    const extensionRows = rows.filter((row) => {
      const extension = extensionsByRequestId[row.requestId]
      return extension && (extension.status === 'REQUESTED' || extension.status === 'GUIDE_APPROVED')
    })
    const extensionRequestIds = new Set(extensionRows.map((row) => Number(row.requestId)))
    const sections = STATUS_SECTIONS.map((section) => ({
      ...section,
      rows: rows.filter((row) => {
        if (extensionRequestIds.has(Number(row.requestId))) return false
        return section.statuses.includes(String(row.status ?? ''))
      }),
    }))
    const included = new Set(STATUS_SECTIONS.flatMap((section) => section.statuses))
    const others = rows.filter((row) => {
      if (extensionRequestIds.has(Number(row.requestId))) return false
      return !included.has(String(row.status ?? ''))
    })
    const rejectedSection = sections.find((section) => section.key === 'REJECTED')
    const mainSections = sections.filter((section) => section.key !== 'REJECTED' && section.rows.length > 0)
    if (extensionRows.length > 0) {
      mainSections.unshift({ key: 'EXTENSION', label: '연장 요청', statuses: [], rows: extensionRows })
    }
    if (others.length > 0) mainSections.push({ key: 'OTHER', label: '기타', statuses: [], rows: others })
    if (rejectedSection?.rows.length) mainSections.push(rejectedSection)
    return mainSections
  }, [rows, extensionsByRequestId])

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

  return (
    <div className="inbox">
      <header className="inbox-head">
        <h1>매칭 요청 관리</h1>
        <p className="inbox-sub">요청 상태를 한눈에 확인하고, 대기 요청은 바로 코스를 작성해 제시안으로 전환하세요.</p>
      </header>

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
              className={`inbox-status-section${section.key === 'IN_PROGRESS' ? ' inbox-status-section--inprogress' : ''}${section.key === 'PENDING' ? ' inbox-status-section--pending' : ''}${section.key === 'EXTENSION' ? ' inbox-status-section--extension' : ''}`}
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
                      <th>여행 성향</th>
                      <th>동작</th>
                    </tr>
                  </thead>
                  <tbody>
                    {section.rows.map((r) => {
                      const dnaSummary = compactDnaSummary(r.conceptSummary)
                      return (
                        <tr key={r.requestId}>
                          <td>{section.key === 'EXTENSION' ? extensionStatusLabel(extensionsByRequestId[r.requestId]?.status) : statusLabel(r.status)}</td>
                          <td>{`게스트 #${r.guestId}`}</td>
                          <td>{r.destination ?? '—'}</td>
                          <td>{r.desiredDate ?? '—'}</td>
                          <td>{formatBudgetRange(r.budgetMinWon, r.budgetMaxWon) ?? formatBudget(r.desiredBudget)}</td>
                          <td className="inbox-dna-cell" title={dnaSummary !== '—' ? dnaSummary : undefined}>
                            <span className="inbox-dna-text">{dnaSummary}</span>
                          </td>
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
                      )
                    })}
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
                      {section.key === 'EXTENSION' ? extensionStatusLabel(extensionsByRequestId[r.requestId]?.status) : statusLabel(r.status)}
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
                    {section.key === 'EXTENSION' && (
                      <p className="inbox-proposed-hint">🔔 게스트가 투어 연장을 요청했습니다. 결제 상태를 확인해 주세요.</p>
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
