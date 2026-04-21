import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useGuidePendingRequests } from '../context/GuidePendingRequestsProvider.jsx'
import { useAuth } from '../context/useAuth.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'
import { GuideCoursePanel, parseCourseDetail } from './GuideCoursePanel.jsx'

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

export function GuideInboxPage() {
  const { isGuide } = useAuth()
  const { refresh: refreshPendingBadge } = useGuidePendingRequests()
  const { guideId } = useResolvedGuideId()
  const [rows, setRows] = useState([])
  const [schedulesById, setSchedulesById] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [toast, setToast] = useState('')
  const [courseTarget, setCourseTarget] = useState(null)

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
    setCourseTarget({
      requestId: r.requestId,
      scheduleId: sid,
      availableDate: linkedSchedule?.availableDate ?? r.desiredDate ?? '',
      startTime: linkedSchedule?.startTime ?? '00:00',
      endTime: linkedSchedule?.endTime ?? '23:59',
      destination: r.destination ?? '',
    })
  }

  const buildProposedSchedule = (courseDetail) => {
    const spots = parseCourseDetail(courseDetail).filter((spot) => String(spot.name ?? '').trim())
    if (spots.length === 0) return ''
    return spots.map((spot) => spot.name.trim()).join(' -> ')
  }

  const submitProposalFromForm = async (requestId, savedForm) => {
    const proposedSchedule = buildProposedSchedule(savedForm?.courseDetail ?? '')
    const proposeMessage = String(savedForm?.guideMessage ?? '').trim()
    const body = {
      proposedSchedule,
      proposeMessage: proposeMessage || undefined,
    }
    if (!proposedSchedule) {
      setToast('코스 스팟을 1개 이상 작성해야 제시안을 보낼 수 있습니다.')
      return
    }
    setBusyId(requestId)
    setToast('')
    try {
      const res = await apiRequest(`/matching/requests/${requestId}/propose`, { method: 'PATCH', json: body })
      const text = await res.text()
      if (!res.ok) {
        setToast(await readJsonError(res, text))
        return
      }
      await fetchList()
      await loadSchedules()
      setCourseTarget(null)
    } catch {
      setToast('제시안 전송 실패')
    } finally {
      setBusyId(null)
    }
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
        <>
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
                {rows.map((r) => (
                  <tr key={r.requestId}>
                    <td>{statusLabel(r.status)}</td>
                    <td>{`게스트 #${r.guestId}`}</td>
                    <td>{r.destination}</td>
                    <td>{r.desiredDate ?? '—'}</td>
                    <td>{r.desiredBudget ? '₩' + Number(r.desiredBudget).toLocaleString('ko-KR') : '—'}</td>
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

          <div className="inbox-cards" aria-label="예약 요청 카드 목록">
            {rows.map((r) => (
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
                <p className="inbox-card-line">💰 <strong>예산</strong> {r.desiredBudget ? '₩' + Number(r.desiredBudget).toLocaleString('ko-KR') : '—'}</p>
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
        </>
      )}

      {courseTarget && guideId != null && (
        <GuideCoursePanel
          guideId={guideId}
          schedule={courseTarget}
          onClose={() => setCourseTarget(null)}
          onSaved={(savedForm) => void submitProposalFromForm(courseTarget.requestId, savedForm)}
        />
      )}
    </div>
  )
}
