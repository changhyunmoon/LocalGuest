/**
 * GuideScheduleSection — 가이드 스케줄 탭 (캘린더 + 인라인 드로어 + 예약 관리)
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

function ymd(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function formatTime(t) {
  if (t == null) return '—'
  if (typeof t === 'string') return t.length >= 5 ? t.slice(0, 5) : t
  return String(t)
}

function parseDateOnly(value) {
  if (!value) return null
  const d = new Date(`${value}T00:00:00`)
  return Number.isNaN(d.getTime()) ? null : d
}

function isPast(date) {
  const today = new Date()
  const begin = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return date < begin
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const WEEKDAYS_SHORT = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
const MONTHS_KO = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월']

function addDaysKey(key, deltaDays) {
  if (!key) return null
  try {
    const base = new Date(`${String(key)}T00:00:00`)
    if (Number.isNaN(base.getTime())) return null
    base.setDate(base.getDate() + Number(deltaDays || 0))
    return ymd(base)
  } catch {
    return null
  }
}

function getDayStatus(dayKey, scheduleByDate, blockedDates) {
  if (blockedDates.has(dayKey)) return 'blocked'
  const daySchedules = scheduleByDate.get(dayKey) ?? []
  if (daySchedules.some((s) => s.status === 'BOOKED')) return 'booked'
  if (daySchedules.some((s) => s.status === 'PENDING')) return 'pending'
  if (daySchedules.some((s) => s.status === 'AVAILABLE')) return 'receiving'
  return 'receiving'
}

/** 캘린더 셀 한 줄 캡션: 확정·대기 일정이 있으면 목적지 축약 또는 건수 */
function calendarDayCaption(daySchedules, requestsByScheduleId) {
  if (!Array.isArray(daySchedules) || daySchedules.length === 0) return ''
  const rows = daySchedules.filter((s) => s?.status === 'BOOKED' || s?.status === 'PENDING')
  if (rows.length === 0) return ''
  if (rows.length > 1) return `${rows.length}건`
  const s = rows[0]
  const req = requestsByScheduleId?.get?.(Number(s.scheduleId)) ?? null
  const raw = String(req?.destination ?? '투어').trim()
  if (!raw) return '투어'
  const max = 5
  return raw.length > max ? `${raw.slice(0, max)}…` : raw
}

function mergeSchedules(schedules, pendingSchedules) {
  const byId = new Map()
  const extra = []
  for (const s of schedules) {
    if (s?.scheduleId != null) {
      byId.set(Number(s.scheduleId), s)
      continue
    }
    // 백엔드가 연속 일정 차단을 위해 scheduleId 없는 가상 PENDING을 내려줄 수 있다.
    // 이런 엔트리는 scheduleId로 dedupe가 불가능하므로 별도로 유지한다.
    if (s?.availableDate && String(s?.status ?? '').toUpperCase() === 'PENDING') {
      extra.push(s)
    }
  }
  for (const s of pendingSchedules) {
    const id = Number(s.scheduleId)
    if (!Number.isNaN(id) && !byId.has(id)) byId.set(id, s)
  }
  if (extra.length === 0) return [...byId.values()]
  return [...byId.values(), ...extra]
}

function ScheduleDrawer({
  selectedKey,
  scheduleByDate,
  blockedDates,
  requestsByScheduleId,
  onActivateReceiving,
  onSetBlocked,
  onOpenCourse,
  onCancelRequest,
  busy,
}) {
  const navigate = useNavigate()
  /** 'receive' | 'block' — 적용 전 선택만 반영 */
  const [pick, setPick] = useState('receive')
  const receiveRadioRef = useRef(null)
  const blockRadioRef = useRef(null)

  const status = selectedKey ? getDayStatus(selectedKey, scheduleByDate, blockedDates) : 'receiving'
  const daySchedules = selectedKey ? (scheduleByDate.get(selectedKey) ?? []) : []

  useEffect(() => {
    if (!selectedKey) return
    if (status === 'blocked') setPick('block')
    else setPick('receive')
  }, [selectedKey, status])

  useEffect(() => {
    if (!selectedKey) return
    const target = pick === 'block' ? blockRadioRef.current : receiveRadioRef.current
    if (!target) return
    const t = window.setTimeout(() => {
      target.focus()
    }, 0)
    return () => window.clearTimeout(t)
  }, [selectedKey, pick])

  if (!selectedKey) return null

  const d = parseDateOnly(selectedKey)
  const dateLabel = d
    ? `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]})`
    : selectedKey

  const badgeClass = {
    receiving: 'gss3-badge--available',
    booked: 'gss3-badge--booked',
    pending: 'gss3-badge--pending',
    blocked: 'gss3-badge--blocked',
  }[status]

  const badgeLabel = {
    receiving: '예약 받는 중',
    booked: '예약 확정',
    pending: '수락 대기',
    blocked: '예약 안 받음',
  }[status]

  const unchanged =
    (status === 'receiving' && pick === 'receive') || (status === 'blocked' && pick === 'block')

  const handleApply = async () => {
    if (busy || unchanged) return
    if (pick === 'receive') await onActivateReceiving(selectedKey)
    else await onSetBlocked(selectedKey)
  }

  return (
    <div className="gss3-drawer">
      <p className="gss3-drawer-date">{dateLabel}</p>
      <span className={`gss3-badge ${badgeClass}`}>{badgeLabel}</span>

      {(status === 'receiving' || status === 'blocked') && (
        <div className="gss3-drawer-avail">
          <p className="gss3-drawer-desc">
            {status === 'receiving' &&
              '기본값으로 이 날은 예약을 받는 상태예요. 필요하면 아래에서 이 날짜만 예약 안 받기로 바꿀 수 있어요.'}
            {status === 'blocked' && '이 날은 요청이 막혀 있어요. 다시 받으려면 옵션을 바꾼 뒤 적용하기를 눌러 주세요.'}
          </p>

          <div className="gss3-pick" role="radiogroup" aria-label="예약 수신 방식">
            <label className={`gss3-pick__row ${pick === 'receive' ? 'gss3-pick__row--on' : ''}`}>
              <input ref={receiveRadioRef} type="radio" name="gss3-day-pick" checked={pick === 'receive'} onChange={() => setPick('receive')} />
              <div className="gss3-pick__text">
                <span className="gss3-pick__label">예약 받기</span>
                <span className="gss3-pick__hint">종일 슬롯 · 게스트가 요청 가능</span>
              </div>
            </label>
            <label className={`gss3-pick__row ${pick === 'block' ? 'gss3-pick__row--on' : ''}`}>
              <input ref={blockRadioRef} type="radio" name="gss3-day-pick" checked={pick === 'block'} onChange={() => setPick('block')} />
              <div className="gss3-pick__text">
                <span className="gss3-pick__label">예약 안 받기</span>
                <span className="gss3-pick__hint">이 날짜 요청 차단</span>
              </div>
            </label>
          </div>

          <button type="button" className="gss3-apply" disabled={busy || unchanged} onClick={() => { void handleApply() }}>
            {busy ? '적용 중…' : '적용하기'}
          </button>

          <p className="gss3-drawer-footnote">
            &quot;예약 받기&quot;는 종일(00:00~23:59) 예약 가능 슬롯을 만들어요. 매일 투어가 있는 뜻은 아니에요.
          </p>
        </div>
      )}

      {(status === 'pending' || status === 'booked') &&
        daySchedules
          .filter((s) => s.status === 'BOOKED' || s.status === 'PENDING')
          .map((s) => {
            const req = requestsByScheduleId.get(Number(s.scheduleId))
            const isPending = s.status === 'PENDING'
            return (
              <div key={s.scheduleId} className="gss3-drawer-schedule">
                <div className="gss3-drawer-rows">
                  {req?.guestId && (
                    <div className="gss3-drawer-row">
                      <span className="gss3-dk">게스트</span>
                      <span className="gss3-dv">게스트 #{req.guestId}</span>
                    </div>
                  )}
                  <div className="gss3-drawer-row">
                    <span className="gss3-dk">목적지</span>
                    <span className="gss3-dv">{req?.destination ?? '로컬 투어'}</span>
                  </div>
                  <div className="gss3-drawer-row">
                    <span className="gss3-dk">시간</span>
                    <span className="gss3-dv">
                      {formatTime(s.startTime)} ~ {formatTime(s.endTime)}
                    </span>
                  </div>
                  {s.matchRequestId != null && (
                    <div className="gss3-drawer-row">
                      <span className="gss3-dk">예약번호</span>
                      <span className="gss3-dv">#{s.matchRequestId}</span>
                    </div>
                  )}
                  {!isPending && (
                    <div className="gss3-drawer-row">
                      <span className="gss3-dk">코스</span>
                      <span className={`gss3-dv ${s.hasCourse ? 'gss3-dv--ok' : 'gss3-dv--warn'}`}>
                        {s.hasCourse ? '작성 완료' : '미작성 — 투어 전 작성 필요'}
                      </span>
                    </div>
                  )}
                </div>
                {isPending ? (
                  <>
                    <p className="gss3-drawer-desc">수락·거절은 매칭 요청에서 처리할 수 있습니다.</p>
                    <div className="gss3-drawer-actions">
                      <button type="button" className="gss3-dact gss3-dact--primary" onClick={() => navigate('/guide/inbox')}>
                        매칭 요청으로 이동 →
                      </button>
                    </div>
                  </>
                ) : (
                  <div className="gss3-drawer-actions">
                    <button
                      type="button"
                      className={`gss3-dact ${s.hasCourse ? 'gss3-dact--primary' : 'gss3-dact--warn'}`}
                      onClick={() =>
                        onOpenCourse({
                          requestId: s.matchRequestId ?? req?.requestId ?? null,
                          scheduleId: s.scheduleId,
                          availableDate: s.availableDate,
                          startTime: formatTime(s.startTime),
                          endTime: formatTime(s.endTime),
                          destination: req?.destination ?? '로컬 투어',
                        })
                      }
                    >
                      {s.hasCourse ? '코스 수정하기 →' : '코스 작성하기 →'}
                    </button>
                    <button
                      type="button"
                      className="gss3-dact gss3-dact--danger"
                      disabled={busy}
                      onClick={() =>
                        onCancelRequest?.({
                          requestId: s.matchRequestId ?? req?.requestId ?? null,
                          scheduleId: s.scheduleId,
                          availableDate: s.availableDate,
                        })
                      }
                    >
                      일정 취소하기
                    </button>
                  </div>
                )}
              </div>
            )
          })}
    </div>
  )
}

function BookingCard({ dateKey, info, req, isSelected, onClickCard, onOpenCourse, onCancelRequest, navigate, busy }) {
  const d = parseDateOnly(dateKey)
  if (!d) return null

  const past = isPast(d)
  const isPending = info.status === 'PENDING'
  const isBooked = info.status === 'BOOKED'
  const hasCourse = !!info.hasCourse

  let cardCls = 'gss3-bcard'
  if (isSelected) cardCls += ' gss3-bcard--highlighted'
  if (isBooked && hasCourse) cardCls += ' gss3-bcard--done'
  else if (isBooked && !hasCourse) cardCls += ' gss3-bcard--warn'
  if (past) cardCls += ' gss3-bcard--past'

  return (
    <div
      className={cardCls}
      data-key={dateKey}
      onClick={() => onClickCard(dateKey)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && onClickCard(dateKey)}
    >
      <div className="gss3-date-col">
        <div className="gss3-dc-month">{MONTHS_KO[d.getMonth()]}</div>
        <div className="gss3-dc-day">{d.getDate()}</div>
        <div className="gss3-dc-wd">{WEEKDAYS[d.getDay()]}</div>
      </div>

      <div className="gss3-bcard-body">
        <div className="gss3-bcard-top">
          <span
            className={`gss3-bcard-dest gss3-bcard-dest--marker gss3-bcard-dest--${isPending ? 'pending' : 'booked'}`}
          >
            {req?.destination ?? '로컬 투어'}
          </span>
          <span
            className={`gss3-badge ${isPending ? 'gss3-badge--pending' : 'gss3-badge--booked'}`}
            style={{ fontSize: '10px', padding: '2px 8px' }}
          >
            {isPending ? '수락 대기' : '예약 확정'}
          </span>
          {isBooked && hasCourse && <span className="gss3-course-tag gss3-course-tag--done">코스 완료</span>}
          {isBooked && !hasCourse && <span className="gss3-course-tag gss3-course-tag--warn">코스 미작성</span>}
        </div>
        <div className="gss3-bcard-meta">
          {formatTime(info.startTime)} ~ {formatTime(info.endTime)}
          {req?.guestId ? ` · 게스트 #${req.guestId}` : ''}
          {info.matchRequestId ? ` · 예약번호 #${info.matchRequestId}` : ''}
        </div>

        <div className="gss3-bcard-actions" onClick={(e) => e.stopPropagation()}>
          {isBooked && (
            <button
              type="button"
              className={`gss3-bact ${hasCourse ? 'gss3-bact--primary' : 'gss3-bact--warn'}`}
              onClick={() =>
                onOpenCourse({
                  requestId: info.matchRequestId ?? req?.requestId ?? null,
                  scheduleId: info.scheduleId,
                  availableDate: info.availableDate,
                  startTime: formatTime(info.startTime),
                  endTime: formatTime(info.endTime),
                  destination: req?.destination ?? '로컬 투어',
                })
              }
            >
              {hasCourse ? '코스 수정' : '코스 작성 (필요)'}
            </button>
          )}
          {isBooked && (
            <button
              type="button"
              className="gss3-bact gss3-bact--danger"
              disabled={busy}
              onClick={() =>
                onCancelRequest?.({
                  requestId: info.matchRequestId ?? req?.requestId ?? null,
                  scheduleId: info.scheduleId,
                  availableDate: info.availableDate,
                })
              }
            >
              일정 취소
            </button>
          )}
          {isPending && (
            <button type="button" className="gss3-bact gss3-bact--primary" onClick={() => navigate('/guide/inbox')}>
              매칭 요청에서 처리 →
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export function GuideScheduleSection({
  schedules = [],
  pendingSchedules = [],
  blockedDates = new Set(),
  busy = false,
  onActivateReceiving,
  onSetBlocked,
  onOpenCourse,
  onCancelRequest,
  requestsByScheduleId = new Map(),
}) {
  const navigate = useNavigate()
  const cardListRef = useRef(null)
  const scrollToCardTimerRef = useRef(null)

  const mergedSchedules = useMemo(() => mergeSchedules(schedules, pendingSchedules), [schedules, pendingSchedules])

  const [calendarMonth, setCalendarMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [selectedKey, setSelectedKey] = useState(null)
  const [curFilter, setCurFilter] = useState('all')

  /** 캘린더용: 메인 schedules에 BLOCKED 등 모든 행 포함 + pending 병합 */
  const scheduleByDate = useMemo(() => {
    const map = new Map()
    const merged = mergeSchedules(schedules, pendingSchedules)
    for (const s of merged) {
      if (!s?.availableDate) continue
      if (!map.has(s.availableDate)) map.set(s.availableDate, [])
      map.get(s.availableDate).push(s)
    }
    return map
  }, [schedules, pendingSchedules])

  /** 같은 matchRequestId의 연속 PENDING을 캘린더에서 범위로 표시 */
  const pendingGroupByDate = useMemo(() => {
    const map = new Map()
    for (const [dateKey, list] of scheduleByDate.entries()) {
      if (!dateKey || !Array.isArray(list) || list.length === 0) continue
      const pending = list.find((s) => String(s?.status ?? '').toUpperCase() === 'PENDING' && s?.matchRequestId != null)
      if (pending?.matchRequestId != null) {
        map.set(dateKey, String(pending.matchRequestId))
      }
    }
    return map
  }, [scheduleByDate])

  const monthKey = `${calendarMonth.getFullYear()}-${String(calendarMonth.getMonth() + 1).padStart(2, '0')}`
  const thisMonthBooked = mergedSchedules.filter((s) => s.availableDate?.startsWith(monthKey) && s.status === 'BOOKED').length
  const thisMonthPending = pendingSchedules.filter((s) => s.availableDate?.startsWith(monthKey)).length

  const calendarCells = useMemo(() => {
    const y = calendarMonth.getFullYear()
    const m = calendarMonth.getMonth()
    const first = new Date(y, m, 1).getDay()
    const daysInMonth = new Date(y, m + 1, 0).getDate()
    const prevDays = new Date(y, m, 0).getDate()
    const cells = []
    for (let i = 0; i < first; i += 1) {
      const day = prevDays - first + i + 1
      const d = new Date(y, m - 1, day)
      cells.push({ date: d, inMonth: false, key: `p-${ymd(d)}` })
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
      const d = new Date(y, m, day)
      cells.push({ date: d, inMonth: true, key: `c-${ymd(d)}` })
    }
    while (cells.length < 42) {
      const day = cells.length - (first + daysInMonth) + 1
      const d = new Date(y, m + 1, day)
      cells.push({ date: d, inMonth: false, key: `n-${ymd(d)}` })
    }
    return cells
  }, [calendarMonth])

  const bookingEntries = useMemo(() => {
    return mergedSchedules
      .filter((s) => s.status === 'BOOKED' || s.status === 'PENDING')
      .filter((s) => {
        if (curFilter === 'pending') return s.status === 'PENDING'
        if (curFilter === 'booked') return s.status === 'BOOKED'
        return true
      })
      .sort((a, b) => (a.availableDate ?? '').localeCompare(b.availableDate ?? ''))
  }, [mergedSchedules, curFilter])

  const todayKey = ymd(new Date())

  const handleDayClick = (cell) => {
    if (!cell.inMonth || isPast(cell.date)) return
    const key = ymd(cell.date)
    setSelectedKey((prev) => (prev === key ? null : key))
    if (scrollToCardTimerRef.current) clearTimeout(scrollToCardTimerRef.current)
    scrollToCardTimerRef.current = setTimeout(() => {
      scrollToCardTimerRef.current = null
      const el = cardListRef.current?.querySelector(`[data-key="${key}"]`)
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }, 150)
  }

  const handleCardClick = (key) => {
    const d = parseDateOnly(key)
    if (!d) return
    if (calendarMonth.getFullYear() !== d.getFullYear() || calendarMonth.getMonth() !== d.getMonth()) {
      setCalendarMonth(new Date(d.getFullYear(), d.getMonth(), 1))
    }
    setSelectedKey((prev) => (prev === key ? null : key))
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <section className="gss3-wrap">
      <div className="gss3-summary">
        <div className="gss3-sum-card">
          <p className="gss3-sum-label">이번 달 예약</p>
          <p className="gss3-sum-value gss3-sum-value--blue">{thisMonthBooked}</p>
        </div>
        <div className="gss3-sum-card">
          <p className="gss3-sum-label">수락 대기</p>
          <p className="gss3-sum-value gss3-sum-value--orange">{thisMonthPending}</p>
        </div>
      </div>

      <div className="gss3-cal-card">
        <div className="gss3-cal-header">
          <button
            type="button"
            className="gss3-nav-btn"
            onClick={() => {
              setCalendarMonth((p) => new Date(p.getFullYear(), p.getMonth() - 1, 1))
              setSelectedKey(null)
            }}
            aria-label="이전 달"
          >
            ‹
          </button>
          <span className="gss3-cal-title">
            {calendarMonth.getFullYear()}년 {calendarMonth.getMonth() + 1}월
          </span>
          <button
            type="button"
            className="gss3-nav-btn"
            onClick={() => {
              setCalendarMonth((p) => new Date(p.getFullYear(), p.getMonth() + 1, 1))
              setSelectedKey(null)
            }}
            aria-label="다음 달"
          >
            ›
          </button>
        </div>

        <div className="gss3-cal-grid">
          <div className="gss3-week">
            {WEEKDAYS_SHORT.map((d, i) => (
              <span
                key={d}
                className={`gss3-weekday ${i === 0 ? 'gss3-weekday--sun' : ''} ${i === 6 ? 'gss3-weekday--sat' : ''}`}
              >
                {d}
              </span>
            ))}
          </div>

          <div className="gss3-days">
          {calendarCells.map((cell) => {
            const key = cell.inMonth ? ymd(cell.date) : null
            const past = isPast(cell.date)
            const isToday = key === todayKey
            const status = key != null ? getDayStatus(key, scheduleByDate, blockedDates) : null
            const isSelected = key === selectedKey
            const daySchedules = key ? (scheduleByDate.get(key) ?? []) : []
            const hasUnwrittenCourse = daySchedules.some((s) => s.status === 'BOOKED' && !s.hasCourse)
            const dayCap = key && cell.inMonth && !past ? calendarDayCaption(daySchedules, requestsByScheduleId) : ''
            const showHighlighter =
              cell.inMonth &&
              !past &&
              !isSelected &&
              status != null &&
              (status === 'booked' || status === 'pending')
            const hlTone =
              status === 'blocked'
                ? 'blocked'
                : status === 'pending'
                  ? 'pending'
                  : status === 'booked'
                    ? 'booked'
                    : status === 'receiving'
                      ? 'open'
                      : null

            let cls = 'gss3-day'
            if (!cell.inMonth || past) cls += ' gss3-day--dim'
            if (cell.inMonth && !past && status != null) {
              if (status === 'booked') cls += ' gss3-day--booked'
              else if (status === 'pending') cls += ' gss3-day--pending'
              else if (status === 'blocked') cls += ' gss3-day--blocked'
              else if (isToday && status === 'receiving') cls += ' gss3-day--today'
              else if (status === 'receiving') cls += ' gss3-day--open'
              else if (status === 'neutral') cls += ' gss3-day--neutral'
            }
            if (isSelected && key != null) cls += ' gss3-day--selected'

            // 연속 일정이라도 캘린더는 "해당 날짜들이 예약(대기) 상태"임을 각 칸으로 명확히 보여준다.

            return (
              <button
                key={cell.key}
                type="button"
                className={cls}
                onClick={() => handleDayClick(cell)}
                disabled={!cell.inMonth || past || busy}
              >
                <span className="gss3-day-stack">
                  <span className="gss3-day-numwrap">
                    {showHighlighter && hlTone ? (
                      <span className={`gss3-day-highlighter gss3-day-highlighter--${hlTone}`} aria-hidden />
                    ) : null}
                    <span className="gss3-day-num">{cell.date.getDate()}</span>
                  </span>
                  {dayCap ? (
                    <span className="gss3-day-cap" title={dayCap}>
                      {dayCap}
                    </span>
                  ) : null}
                </span>
                {cell.inMonth && !past && status === 'blocked' && <span className="gss3-day-x">✕</span>}
                {cell.inMonth && !past && hasUnwrittenCourse && <span className="gss3-warn-dot" />}
              </button>
            )
          })}
          </div>
        </div>

        <div className="gss3-legend">
          <span className="gss3-leg">
            <span className="gss3-leg-today" aria-hidden />
            오늘
          </span>
          <span className="gss3-leg">
            <span className="gss3-leg-strip gss3-leg-strip--open" aria-hidden />
            예약 받는 날
          </span>
          <span className="gss3-leg">
            <span className="gss3-leg-strip gss3-leg-strip--booked" aria-hidden />
            예약 확정
          </span>
          <span className="gss3-leg">
            <span className="gss3-leg-strip gss3-leg-strip--pending" aria-hidden />
            수락 대기
          </span>
          <span className="gss3-leg">
            <span className="gss3-leg-strip gss3-leg-strip--blocked" aria-hidden />
            예약 안 받음
          </span>
          <span className="gss3-leg">
            <span className="gss3-leg-dot" style={{ background: '#f59e0b' }} aria-hidden />
            코스 미작성
          </span>
        </div>
        <p className="gss3-cal-hint">
          지난 날짜는 선택할 수 없어요 · 기본은 예약 가능이며, 원하지 않는 날짜만 &quot;예약 안 받기&quot;로 바꾸면 됩니다 · 같은 날짜를 다시 누르면
          선택이 해제되고 아래 패널이 닫혀요
        </p>

        <div className={`gss3-drawer-wrap${selectedKey ? ' gss3-drawer-wrap--open' : ''}`}>
          {selectedKey && (
            <ScheduleDrawer
              selectedKey={selectedKey}
              scheduleByDate={scheduleByDate}
              blockedDates={blockedDates}
              requestsByScheduleId={requestsByScheduleId}
              onActivateReceiving={onActivateReceiving}
              onSetBlocked={onSetBlocked}
              onOpenCourse={onOpenCourse}
              busy={busy}
            />
          )}
        </div>
      </div>

      <div className="gss3-mgmt-card">
        <div className="gss3-mgmt-head">
          <span className="gss3-mgmt-title">예약 관리</span>
          <div className="gss3-filter-tabs">
            {[
              { key: 'all', label: '전체' },
              { key: 'pending', label: '대기' },
              { key: 'booked', label: '확정' },
            ].map(({ key, label }) => (
              <button
                key={key}
                type="button"
                className={`gss3-ftab ${curFilter === key ? 'gss3-ftab--on' : ''}`}
                onClick={() => setCurFilter(key)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="gss3-booking-list" ref={cardListRef}>
          {bookingEntries.length === 0 ? (
            <p className="gss3-empty">해당하는 예약이 없습니다</p>
          ) : (
            bookingEntries.map((s) => {
              const req = requestsByScheduleId.get(Number(s.scheduleId))
              return (
                <BookingCard
                  key={s.scheduleId}
                  dateKey={s.availableDate}
                  info={s}
                  req={req}
                  isSelected={selectedKey === s.availableDate}
                  onClickCard={handleCardClick}
                  onOpenCourse={onOpenCourse}
                  onCancelRequest={onCancelRequest}
                  navigate={navigate}
                  busy={busy}
                />
              )
            })
          )}
        </div>
      </div>
    </section>
  )
}
