import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import { GuideCoursePanel, parseCourseDetail } from './GuideCoursePanel.jsx'

import './GuideCourseEditorPage.css'

const MATCH_STATUS_KO = {
  PENDING: '대기 중',
  ACCEPTED: '수락됨',
  PAID: '결제 완료',
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
  CANCELLED: '취소됨',
  REJECTED: '거절됨',
}

function formatMatchStatus(status) {
  if (status == null || status === '') return '—'
  return MATCH_STATUS_KO[status] ?? String(status)
}

function formatBudgetKrw(n) {
  if (n == null || n === '' || Number.isNaN(Number(n))) return '—'
  return `${Number(n).toLocaleString('ko-KR')}원`
}

function formatBudgetRange(minWon, maxWon) {
  const min = minWon != null && minWon !== '' && !Number.isNaN(Number(minWon)) ? Number(minWon) : null
  const max = maxWon != null && maxWon !== '' && !Number.isNaN(Number(maxWon)) ? Number(maxWon) : null
  if (min == null && max == null) return null
  if (min != null && max != null) return `${min.toLocaleString('ko-KR')}~${max.toLocaleString('ko-KR')}원`
  const one = min ?? max
  return `${Number(one).toLocaleString('ko-KR')}원`
}

function formatDesiredDate(d) {
  if (d == null || d === '') return '—'
  return String(d)
}

function inferDurationDaysFromText(text) {
  if (!text) return null
  const s = String(text)
  const nightsDays = s.match(/(\d{1,2})\s*박\s*(\d{1,2})\s*일/)
  if (nightsDays) {
    const days = Number(nightsDays[2])
    return Number.isFinite(days) && days > 0 ? days : null
  }
  const daysOnly = s.match(/(\d{1,2})\s*일\s*일정/)
  if (daysOnly) {
    const days = Number(daysOnly[1])
    return Number.isFinite(days) && days > 0 ? days : null
  }
  return null
}

function formatDesiredDateRange(start, days) {
  if (start == null || start === '') return '—'
  const s = String(start)
  const n = days != null ? Number(days) : NaN
  if (!Number.isFinite(n) || n <= 1) return s
  try {
    const base = new Date(s)
    if (Number.isNaN(base.getTime())) return s
    const end = new Date(base)
    end.setDate(end.getDate() + Math.floor(n) - 1)
    const yyyy = end.getFullYear()
    const mm = String(end.getMonth() + 1).padStart(2, '0')
    const dd = String(end.getDate()).padStart(2, '0')
    return `${s} ~ ${yyyy}-${mm}-${dd}`
  } catch {
    return s
  }
}

function formatRequestedAt(iso) {
  if (iso == null || iso === '') return '—'
  try {
    const t = new Date(iso)
    if (Number.isNaN(t.getTime())) return String(iso)
    return t.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
  } catch {
    return String(iso)
  }
}

async function readJsonError(res, text) {
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function buildProposedSchedule(courseDetail) {
  const spots = parseCourseDetail(courseDetail ?? '').filter((spot) => String(spot.name ?? '').trim())
  if (spots.length === 0) return ''
  // 결제 전 미리보기용: 지명은 숨기고 시간/설명만 노출
  // 포맷: "SPOT 1 | 오전 10:00 | 한적한 산책 + 카페" (줄바꿈 구분)
  const clamp = (s, n) => (s.length > n ? `${s.slice(0, n)}…` : s)
  return spots
    .map((spot, idx) => {
      const time = String(spot.time ?? '').trim()
      const desc = String(spot.desc ?? '').trim()
      const safeDesc = clamp(desc.replace(/\s+/g, ' '), 44)
      const label = `SPOT ${idx + 1}`
      // time/desc가 비어도 게스트 화면에서 깨지지 않게 빈 칸 허용
      return `${label} | ${time || '시간 미정'} | ${safeDesc || '자세한 코스는 결제 후 공개됩니다'}`
    })
    .join('\n')
}

function formatTime(t) {
  if (t == null) return ''
  if (typeof t === 'string') return t.length >= 5 ? t.slice(0, 5) : t
  return String(t)
}

function fromStateSchedule(stateSchedule) {
  if (!stateSchedule || stateSchedule.scheduleId == null) return null
  return {
    scheduleId: Number(stateSchedule.scheduleId),
    availableDate: stateSchedule.availableDate ?? '',
    startTime: formatTime(stateSchedule.startTime),
    endTime: formatTime(stateSchedule.endTime),
    destination: stateSchedule.destination ?? '로컬 투어',
  }
}

export function GuideCourseEditorPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { requestId, scheduleId: scheduleIdParam } = useParams()
  const [searchParams] = useSearchParams()
  const { guideId, loading: guideIdLoading, error: guideIdError } = useResolvedGuideId()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [schedule, setSchedule] = useState(() => fromStateSchedule(location.state?.schedule))
  const [initialForm, setInitialForm] = useState(location.state?.initialForm ?? null)
  const [proposalErr, setProposalErr] = useState('')
  const [proposalBusy, setProposalBusy] = useState(false)
  const [matchRow, setMatchRow] = useState(null)

  const proposeAfterSave =
    searchParams.get('propose') === '1' || Boolean(location.state?.proposeAfterSave)

  const scheduleIdFromQuery = Number(searchParams.get('scheduleId'))
  const scheduleIdFromPath = Number(scheduleIdParam)
  const targetScheduleId = Number.isFinite(scheduleIdFromQuery)
    ? scheduleIdFromQuery
    : (Number.isFinite(scheduleIdFromPath) ? scheduleIdFromPath : null)

  const loadSchedule = useCallback(async () => {
    if (!guideId || guideIdLoading) return
    if (schedule) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      const schedulesRes = await apiRequest(`/guides/${guideId}/schedules`, { method: 'GET', skipAuth: true })
      const schedulesText = await schedulesRes.text()
      if (!schedulesRes.ok) throw new Error(schedulesText || '스케줄을 불러오지 못했습니다.')
      const schedules = schedulesText ? JSON.parse(schedulesText) : []
      const list = Array.isArray(schedules) ? schedules : []

      let pick = null
      if (targetScheduleId != null) {
        pick = list.find((s) => Number(s.scheduleId) === targetScheduleId) ?? null
      }

      let destination = '로컬 투어'
      if (pick?.scheduleId != null) {
        try {
          const reqs = await fetchGuideMatchRequests(apiRequest)
          const req = Array.isArray(reqs)
            ? reqs.find((r) => Number(r.scheduleId) === Number(pick.scheduleId))
            : null
          destination = req?.destination ?? destination
        } catch {
          destination = '로컬 투어'
        }
      }

      if (!pick) {
        throw new Error('편집할 스케줄을 찾을 수 없습니다. 스케줄 관리에서 다시 들어와 주세요.')
      }

      setSchedule({
        scheduleId: Number(pick.scheduleId),
        availableDate: pick.availableDate ?? searchParams.get('date') ?? '',
        startTime: formatTime(pick.startTime ?? searchParams.get('startTime') ?? ''),
        endTime: formatTime(pick.endTime ?? searchParams.get('endTime') ?? ''),
        destination: searchParams.get('destination') ?? destination,
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : '코스 편집 페이지를 열지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [guideId, guideIdLoading, schedule, searchParams, targetScheduleId])

  useEffect(() => {
    void loadSchedule()
  }, [loadSchedule])

  useEffect(() => {
    if (!guideId || guideIdLoading) return
    let cancelled = false
    void (async () => {
      try {
        const list = await fetchGuideMatchRequests(apiRequest)
        if (cancelled || !Array.isArray(list)) return
        const rid = requestId != null ? Number(requestId) : NaN
        const sid = schedule?.scheduleId
        let row = null
        if (!Number.isNaN(rid)) {
          row = list.find((r) => Number(r.requestId) === rid) ?? null
        }
        if (!row && sid != null) {
          row = list.find((r) => Number(r.scheduleId) === Number(sid)) ?? null
        }
        if (!cancelled) setMatchRow(row)
      } catch {
        if (!cancelled) setMatchRow(null)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [guideId, guideIdLoading, requestId, schedule?.scheduleId])

  const backTo = useMemo(
    () => (proposeAfterSave ? '/guide/inbox' : '/guide/mypage/feed-schedule?tab=schedule'),
    [proposeAfterSave],
  )

  if (guideIdLoading || loading) {
    return <PageLoading />
  }

  if (guideIdError || error || !schedule) {
    return (
      <PageError message={guideIdError || error || '코스 편집 정보를 찾을 수 없습니다.'}>
        <Link to={backTo}>{proposeAfterSave ? '매칭 요청으로 돌아가기' : '스케줄 관리로 돌아가기'}</Link>
      </PageError>
    )
  }

  const numericRequestId = requestId != null ? Number(requestId) : null

  const handleSaved = async (savedForm) => {
    setInitialForm(savedForm ?? null)
    setProposalErr('')
    if (!proposeAfterSave || numericRequestId == null || Number.isNaN(numericRequestId)) {
      return
    }
    const proposedSchedule = buildProposedSchedule(savedForm?.courseDetail ?? '')
    const proposeMessage = String(savedForm?.guideMessage ?? '').trim()
    if (!proposedSchedule) {
      setProposalErr('코스 스팟을 1개 이상 작성해야 제시안을 보낼 수 있습니다.')
      return
    }
    setProposalBusy(true)
    try {
      const res = await apiRequest(`/matching/requests/${numericRequestId}/propose`, {
        method: 'PATCH',
        json: {
          proposedSchedule,
          proposeMessage: proposeMessage || undefined,
        },
      })
      const text = await res.text()
      if (!res.ok) {
        setProposalErr(await readJsonError(res, text))
        return
      }
      navigate('/guide/inbox', { replace: true })
    } catch {
      setProposalErr('제시안 전송에 실패했습니다.')
    } finally {
      setProposalBusy(false)
    }
  }

  const showRequestHero = numericRequestId != null && !Number.isNaN(numericRequestId)

  return (
    <div className="gce-page">
      <header className="gce-header">
        <button type="button" className="gce-back" onClick={() => navigate(backTo)}>
          ← {proposeAfterSave ? '매칭 요청' : '스케줄 관리'}
        </button>
        <p className="gce-kicker">코스 작성</p>
        {showRequestHero ? (
          <h1 className="gce-title-num">요청 #{numericRequestId}</h1>
        ) : (
          <h1 className="gce-title-num">스케줄 #{schedule.scheduleId}</h1>
        )}
        <p className="gce-hero-sub">
          {schedule.availableDate}
          {schedule.startTime && schedule.endTime ? ` · ${schedule.startTime}–${schedule.endTime}` : ''}
          {schedule.destination ? ` · ${schedule.destination}` : ''}
        </p>
        {proposeAfterSave && <p className="gce-banner">저장하면 제시안이 게스트에게 함께 전송됩니다.</p>}
      </header>

      {matchRow && (
        <section className="gce-guest" aria-label="요청자 정보">
          <h2 className="gce-guest-title">요청자 정보</h2>
          <div className="gce-guest-grid">
            <div className="gce-guest-item">
              <span className="gce-label">게스트</span>
              <span className="gce-value">게스트 #{matchRow.guestId}</span>
            </div>
            <div className="gce-guest-item">
              <span className="gce-label">희망 일정</span>
              <span className="gce-value">
                {formatDesiredDateRange(
                  matchRow.desiredDate,
                  inferDurationDaysFromText(matchRow.conceptSummary) ??
                    inferDurationDaysFromText(matchRow.concept),
                )}
              </span>
            </div>
            <div className="gce-guest-item">
              <span className="gce-label">목적지</span>
              <span className="gce-value">{matchRow.destination?.trim() || '—'}</span>
            </div>
            <div className="gce-guest-item">
              <span className="gce-label">예산</span>
              <span className="gce-value">
                {formatBudgetRange(matchRow.budgetMinWon, matchRow.budgetMaxWon) ?? formatBudgetKrw(matchRow.desiredBudget)}
              </span>
            </div>
            <div className="gce-guest-item">
              <span className="gce-label">요청 상태</span>
              <span className="gce-value">{formatMatchStatus(matchRow.status)}</span>
            </div>
            <div className="gce-guest-item">
              <span className="gce-label">요청일</span>
              <span className="gce-value">{formatRequestedAt(matchRow.createdAt)}</span>
            </div>
          </div>
          {matchRow.conceptSummary?.trim() && (
            <p className="gce-concept">{matchRow.conceptSummary.trim()}</p>
          )}
          {matchRow.concept?.trim() &&
            matchRow.concept.trim() !== String(matchRow.conceptSummary ?? '').trim() && (
              <p className="gce-concept-detail">{matchRow.concept.trim()}</p>
            )}
        </section>
      )}

      {proposalErr && <p className="gce-alert">{proposalErr}</p>}
      {proposalBusy && <p className="gce-busy">제시안 전송 중…</p>}

      <div className="gce-editor">
        <GuideCoursePanel
          guideId={guideId}
          schedule={schedule}
          initialForm={initialForm}
          standalone
          hideHeader
          onClose={() => navigate(backTo)}
          onSaved={(savedForm) => void handleSaved(savedForm)}
        />
      </div>
    </div>
  )
}
