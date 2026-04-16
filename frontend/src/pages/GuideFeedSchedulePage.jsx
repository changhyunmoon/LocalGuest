import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'

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

function ymd(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function monthTitle(date) {
  return `${date.getFullYear()}. ${String(date.getMonth() + 1).padStart(2, '0')}`
}

function nextStatus(s) {
  if (s === 'BOOKED') return 'booked'
  if (s === 'PENDING') return 'pending'
  if (s === 'BLOCKED') return 'blocked'
  return 'plain'
}

async function readJsonError(res, text) {
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '')
    reader.onerror = () => reject(new Error('파일을 읽을 수 없습니다.'))
    reader.readAsDataURL(file)
  })
}

const MAX_LOCAL_IMAGE_BYTES = 1024 * 1024 * 2 // 2MB

export function GuideFeedSchedulePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { guideId, loading: idLoading, error: idError, reload: reloadId } = useResolvedGuideId()
  const [feeds, setFeeds] = useState([])
  const [schedules, setSchedules] = useState([])
  const [pendingSchedules, setPendingSchedules] = useState([])
  const [guideRequests, setGuideRequests] = useState([])
  const [loadError, setLoadError] = useState('')
  const [feedContent, setFeedContent] = useState('')
  const [feedImageUrl, setFeedImageUrl] = useState('')
  const [feedExtraImageUrl, setFeedExtraImageUrl] = useState('')
  const [feedMainFileName, setFeedMainFileName] = useState('')
  const [feedExtraFileName, setFeedExtraFileName] = useState('')
  const [schDate, setSchDate] = useState('')
  const [schStart, setSchStart] = useState('')
  const [schEnd, setSchEnd] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const mainFileInputRef = useRef(null)
  const extraFileInputRef = useRef(null)
  const currentTab = searchParams.get('tab') === 'schedule' ? 'schedule' : 'feed'
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })

  const loadAll = useCallback(async (id) => {
    setLoadError('')
    setMsg('')
    try {
      const [fr, sr, pr, guideReq] = await Promise.all([
        apiRequest(`/guides/${id}/feeds`, { method: 'GET', skipAuth: true }),
        apiRequest(`/guides/${id}/schedules`, { method: 'GET', skipAuth: true }),
        apiRequest(`/guides/${id}/schedules/pending`, { method: 'GET' }),
        fetchGuideMatchRequests(apiRequest),
      ])
      const ft = await fr.text()
      const st = await sr.text()
      const pt = await pr.text()
      if (!fr.ok) {
        throw new Error(await readJsonError(fr, ft))
      }
      if (!sr.ok) {
        throw new Error(await readJsonError(sr, st))
      }
      setFeeds(ft ? JSON.parse(ft) : [])
      setSchedules(st ? JSON.parse(st) : [])
      if (pr.ok) {
        setPendingSchedules(pt ? JSON.parse(pt) : [])
      } else {
        setPendingSchedules([])
      }
      setGuideRequests(Array.isArray(guideReq) ? guideReq : [])
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '불러오기 실패')
    }
  }, [])

  const requestsByScheduleId = useMemo(() => {
    const map = new Map()
    for (const r of guideRequests) {
      if (r?.scheduleId != null) {
        map.set(Number(r.scheduleId), r)
      }
    }
    return map
  }, [guideRequests])

  const upcomingTours = useMemo(() => {
    const today = new Date()
    const begin = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    return schedules
      .filter((s) => s?.matchRequestId != null || s?.status === 'BOOKED')
      .map((s) => {
        const request = requestsByScheduleId.get(Number(s.scheduleId))
        const d = parseDateOnly(s.availableDate)
        return {
          scheduleId: s.scheduleId,
          status: s.status,
          availableDate: s.availableDate,
          startTime: formatTime(s.startTime),
          endTime: formatTime(s.endTime),
          isPaid: !!s.isPaid,
          matchRequestId: s.matchRequestId ?? request?.requestId ?? null,
          destination: request?.destination ?? '로컬 투어',
          desiredDate: request?.desiredDate ?? s.availableDate,
          dateObj: d,
        }
      })
      .filter((s) => s.dateObj && s.dateObj >= begin)
      .sort((a, b) => a.dateObj - b.dateObj)
  }, [schedules, requestsByScheduleId])

  const calendarCells = useMemo(() => {
    const y = calendarMonth.getFullYear()
    const m = calendarMonth.getMonth()
    const first = new Date(y, m, 1)
    const startWeekday = first.getDay()
    const daysInMonth = new Date(y, m + 1, 0).getDate()
    const prevDays = new Date(y, m, 0).getDate()
    const cells = []

    for (let i = 0; i < startWeekday; i += 1) {
      const day = prevDays - startWeekday + i + 1
      const d = new Date(y, m - 1, day)
      cells.push({ date: d, inMonth: false, key: `p-${ymd(d)}` })
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
      const d = new Date(y, m, day)
      cells.push({ date: d, inMonth: true, key: `c-${ymd(d)}` })
    }
    while (cells.length < 42) {
      const day = cells.length - (startWeekday + daysInMonth) + 1
      const d = new Date(y, m + 1, day)
      cells.push({ date: d, inMonth: false, key: `n-${ymd(d)}` })
    }
    return cells
  }, [calendarMonth])

  const scheduleByDate = useMemo(() => {
    const map = new Map()
    for (const s of schedules) {
      if (!s?.availableDate) continue
      if (!map.has(s.availableDate)) map.set(s.availableDate, [])
      map.get(s.availableDate).push(s)
    }
    return map
  }, [schedules])

  useEffect(() => {
    if (!guideId) return
    void loadAll(guideId)
  }, [guideId, loadAll])

  const addFeed = async () => {
    if (!guideId || !feedContent.trim()) return
    setBusy(true)
    setMsg('')
    try {
      const res = await apiRequest(`/guides/${guideId}/feeds`, {
        method: 'POST',
        json: {
          content: feedContent.trim(),
          imageUrl: feedImageUrl.trim() || undefined,
        },
      })
      const text = await res.text()
      if (!res.ok) {
        setMsg(await readJsonError(res, text))
        return
      }
      setFeedContent('')
      setFeedImageUrl('')
      setFeedExtraImageUrl('')
      setFeedMainFileName('')
      setFeedExtraFileName('')
      await loadAll(guideId)
    } catch {
      setMsg('피드 등록 실패')
    } finally {
      setBusy(false)
    }
  }

  const removeFeed = async (feedId) => {
    if (!guideId || !window.confirm('이 피드를 삭제할까요?')) return
    setBusy(true)
    setMsg('')
    try {
      const res = await apiRequest(`/guides/${guideId}/feeds/${feedId}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        setMsg(await readJsonError(res, text))
        return
      }
      await loadAll(guideId)
    } catch {
      setMsg('삭제 실패')
    } finally {
      setBusy(false)
    }
  }

  const addSchedule = async () => {
    if (!guideId || !schDate || !schStart || !schEnd) {
      setMsg('날짜·시작·종료 시간을 모두 입력해 주세요.')
      return
    }
    setBusy(true)
    setMsg('')
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules`, {
        method: 'POST',
        json: {
          availableDate: schDate,
          startTime: schStart.length === 5 ? `${schStart}:00` : schStart,
          endTime: schEnd.length === 5 ? `${schEnd}:00` : schEnd,
        },
      })
      const text = await res.text()
      if (!res.ok) {
        setMsg(await readJsonError(res, text))
        return
      }
      setSchDate('')
      setSchStart('')
      setSchEnd('')
      await loadAll(guideId)
    } catch {
      setMsg('스케줄 등록 실패')
    } finally {
      setBusy(false)
    }
  }

  const acceptPendingSchedule = async (scheduleId) => {
    if (!guideId) return
    setBusy(true)
    setMsg('')
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/${scheduleId}/accept`, { method: 'POST' })
      const text = await res.text()
      if (!res.ok) {
        setMsg(await readJsonError(res, text))
        return
      }
      await loadAll(guideId)
    } catch {
      setMsg('스케줄 수락 실패')
    } finally {
      setBusy(false)
    }
  }

  const rejectPendingSchedule = async (scheduleId) => {
    if (!guideId || !window.confirm('이 예약 대기 스케줄을 거절할까요?')) return
    setBusy(true)
    setMsg('')
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/${scheduleId}/reject`, { method: 'POST' })
      const text = await res.text()
      if (!res.ok) {
        setMsg(await readJsonError(res, text))
        return
      }
      await loadAll(guideId)
    } catch {
      setMsg('스케줄 거절 실패')
    } finally {
      setBusy(false)
    }
  }

  const removeSchedule = async (scheduleId) => {
    if (!guideId || !window.confirm('이 스케줄을 삭제할까요?')) return
    setBusy(true)
    setMsg('')
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/${scheduleId}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        setMsg(await readJsonError(res, text))
        return
      }
      await loadAll(guideId)
    } catch {
      setMsg('스케줄 삭제 실패')
    } finally {
      setBusy(false)
    }
  }

  const onPickMainFile = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > MAX_LOCAL_IMAGE_BYTES) {
      setMsg('이미지 용량이 너무 큽니다. 2MB 이하 파일을 선택해 주세요.')
      e.target.value = ''
      return
    }
    try {
      const dataUrl = await readFileAsDataUrl(file)
      setFeedImageUrl(dataUrl)
      setFeedMainFileName(file.name)
      setMsg('')
    } catch {
      setMsg('메인 사진을 읽지 못했습니다.')
    } finally {
      e.target.value = ''
    }
  }

  const onPickExtraFile = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > MAX_LOCAL_IMAGE_BYTES) {
      setMsg('이미지 용량이 너무 큽니다. 2MB 이하 파일을 선택해 주세요.')
      e.target.value = ''
      return
    }
    try {
      const dataUrl = await readFileAsDataUrl(file)
      setFeedExtraImageUrl(dataUrl)
      setFeedExtraFileName(file.name)
      setMsg('')
    } catch {
      setMsg('추가 사진을 읽지 못했습니다.')
    } finally {
      e.target.value = ''
    }
  }

  if (idLoading) {
    return (
      <div className="g-panel">
        <p>불러오는 중…</p>
      </div>
    )
  }

  if (idError) {
    return (
      <div className="g-panel">
        <p className="g-error">{idError}</p>
        <button type="button" className="gm-btn gm-btn--ghost" onClick={() => reloadId()}>
          다시 시도
        </button>
      </div>
    )
  }

  return (
    <div className="g-panel">
      <h1>💼 피드 및 스케줄 관리</h1>
      <div className="gfs-tabs" role="tablist" aria-label="피드·스케줄 탭">
        <button
          type="button"
          className={`gfs-tab ${currentTab === 'feed' ? 'is-on' : ''}`}
          onClick={() => setSearchParams({ tab: 'feed' })}
        >
          피드 등록
        </button>
        <button
          type="button"
          className={`gfs-tab ${currentTab === 'schedule' ? 'is-on' : ''}`}
          onClick={() => setSearchParams({ tab: 'schedule' })}
        >
          스케줄 관리
        </button>
      </div>
      {loadError && <p className="g-error">{loadError}</p>}
      {msg && <p className="g-error">{msg}</p>}
      {currentTab === 'feed' ? (
        <section className="gfs-feed">
          <div className="gfs-badge">📸 피드 등록</div>

          <div className="gfs-upload-grid">
            <button
              type="button"
              className="gfs-upload-box"
              onClick={() => mainFileInputRef.current?.click()}
              style={feedImageUrl ? { backgroundImage: `url(${feedImageUrl})` } : undefined}
            >
              <span className="gfs-tape gfs-tape--pink" />
              <span className="gfs-plus">+</span>
              <span className="gfs-upload-label">메인 사진 업로드</span>
              {feedMainFileName && <span className="gfs-file-name">{feedMainFileName}</span>}
              <input
                ref={mainFileInputRef}
                type="file"
                accept="image/*"
                className="gfs-file-hidden"
                onChange={(e) => void onPickMainFile(e)}
              />
              <input
                className="gfs-url-input"
                onClick={(e) => e.stopPropagation()}
                placeholder="메인 이미지 URL"
                value={feedImageUrl}
                onChange={(e) => setFeedImageUrl(e.target.value)}
              />
            </button>
            <button
              type="button"
              className="gfs-upload-box"
              onClick={() => extraFileInputRef.current?.click()}
              style={feedExtraImageUrl ? { backgroundImage: `url(${feedExtraImageUrl})` } : undefined}
            >
              <span className="gfs-tape gfs-tape--green" />
              <span className="gfs-plus">+</span>
              <span className="gfs-upload-label">추가 사진 업로드</span>
              {feedExtraFileName && <span className="gfs-file-name">{feedExtraFileName}</span>}
              <input
                ref={extraFileInputRef}
                type="file"
                accept="image/*"
                className="gfs-file-hidden"
                onChange={(e) => void onPickExtraFile(e)}
              />
              <input
                className="gfs-url-input"
                onClick={(e) => e.stopPropagation()}
                placeholder="추가 이미지 URL(옵션)"
                value={feedExtraImageUrl}
                onChange={(e) => setFeedExtraImageUrl(e.target.value)}
              />
            </button>
          </div>

          <div className="gfs-input-row">
            <div className="gfs-content-wrap">
              <textarea
                id="fd-content"
                className="gfs-content"
                value={feedContent}
                onChange={(e) => setFeedContent(e.target.value)}
                rows={2}
                placeholder="피드 내용과 장소를 입력해주세요..."
              />
              <p className="gfs-tag-hint">📍 장소 태그 추가</p>
            </div>
            <button type="button" className="gfs-upload-btn" onClick={() => void addFeed()} disabled={busy || !feedContent.trim()}>
              업로드 🚀
            </button>
          </div>

          {!!feedExtraImageUrl && <p className="gm-hint">추가 사진 URL은 현재 미리보기 전용입니다. (백엔드 단일 imageUrl 저장)</p>}

          <ul className="gm-list" style={{ marginTop: '0.95rem' }}>
            {feeds.length === 0 && <li>등록된 피드가 없습니다.</li>}
            {feeds.map((f) => (
              <li key={f.feedId}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span>{f.content}</span>
                  <button type="button" className="gm-danger" onClick={() => void removeFeed(f.feedId)} disabled={busy}>
                    삭제
                  </button>
                </div>
                {f.imageUrl && (
                  <p className="gm-hint" style={{ marginTop: '0.35rem' }}>
                    {f.imageUrl}
                  </p>
                )}
              </li>
            ))}
          </ul>
        </section>
      ) : (
        <section className="gss-wrap">
          <header className="gss-head">
            <h2>🗓️ 스케줄 관리</h2>
            <p>투어 가능 일정을 관리하고 예약 현황을 확인하세요.</p>
          </header>
          <div className="gss-grid">
            <article className="gss-cal-card">
              <div className="gss-cal-top">
                <strong>{monthTitle(calendarMonth)}</strong>
                <div className="gss-month-nav">
                  <button
                    type="button"
                    className="gss-nav-btn"
                    onClick={() => setCalendarMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                    aria-label="이전 달"
                  >
                    ‹
                  </button>
                  <button
                    type="button"
                    className="gss-nav-btn"
                    onClick={() => setCalendarMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                    aria-label="다음 달"
                  >
                    ›
                  </button>
                </div>
              </div>
              <div className="gss-week">
                <span>SUN</span>
                <span>MON</span>
                <span>TUE</span>
                <span>WED</span>
                <span>THU</span>
                <span>FRI</span>
                <span>SAT</span>
              </div>
              <div className="gss-days">
                {calendarCells.map((cell) => {
                  const dayKey = ymd(cell.date)
                  const daySchedules = scheduleByDate.get(dayKey) ?? []
                  const primary = daySchedules[0]
                  const kind = primary ? nextStatus(primary.status) : 'plain'
                  const today = ymd(new Date()) === dayKey
                  return (
                    <button
                      key={cell.key}
                      type="button"
                      className={`gss-day ${cell.inMonth ? '' : 'is-out'} ${kind !== 'plain' ? `is-${kind}` : ''} ${today ? 'is-today' : ''}`}
                      title={daySchedules.length ? `${dayKey} · ${daySchedules.length}건` : dayKey}
                    >
                      <span>{cell.date.getDate()}</span>
                      {daySchedules.length > 0 && <small>{daySchedules.length}</small>}
                    </button>
                  )
                })}
              </div>
              <div className="gss-legend">
                <span className="gss-dot gss-dot--today">오늘</span>
                <span className="gss-dot gss-dot--booked">투어 예약 있음</span>
                <span className="gss-dot gss-dot--pending">예약 대기</span>
                <span className="gss-dot gss-dot--blocked">예약 불가</span>
              </div>
            </article>

            <article className="gss-upcoming">
              <h3>다가오는 투어 🚩</h3>
              <div className="gss-upcoming-list">
                {upcomingTours.length === 0 && <p className="gm-hint">예약된 투어가 아직 없습니다.</p>}
                {upcomingTours.slice(0, 6).map((tour) => (
                  <section key={tour.scheduleId} className="gss-tour-card">
                    <p className="gss-tour-time">
                      {tour.availableDate} ({tour.startTime})
                    </p>
                    <p className="gss-tour-title">{tour.destination}</p>
                    <p className="gss-tour-meta">
                      시간: {tour.startTime} ~ {tour.endTime} · 상태: {tour.status}
                      {tour.isPaid ? ' · 결제완료' : ''}
                    </p>
                    <p className="gss-tour-meta">
                      예약번호: {tour.matchRequestId ?? '미연결'} · 스케줄 #{tour.scheduleId}
                    </p>
                  </section>
                ))}
              </div>
            </article>
          </div>

          <div className="gm-card" style={{ marginTop: '1rem' }}>
            <h2>스케줄 편집</h2>
          {pendingSchedules.length > 0 && (
            <div style={{ marginBottom: '1rem' }}>
              <p className="gm-hint" style={{ marginBottom: '0.5rem' }}>
                수락 대기(PENDING) — <code>GET /api/guides/&#123;id&#125;/schedules/pending</code> ·{' '}
                <code>POST …/&#123;scheduleId&#125;/accept|reject</code>
              </p>
              <ul className="gm-list">
                {pendingSchedules.map((s) => (
                  <li key={`p-${s.scheduleId}`}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', justifyContent: 'space-between' }}>
                      <span>
                        <strong>{s.availableDate}</strong> {formatTime(s.startTime)}–{formatTime(s.endTime)}
                        {s.matchRequestId != null ? ` · 매칭 #${s.matchRequestId}` : ''}
                      </span>
                      <span style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                        <button
                          type="button"
                          className="gm-btn"
                          style={{ padding: '0.35rem 0.75rem' }}
                          onClick={() => void acceptPendingSchedule(s.scheduleId)}
                          disabled={busy}
                        >
                          수락
                        </button>
                        <button type="button" className="gm-danger" onClick={() => void rejectPendingSchedule(s.scheduleId)} disabled={busy}>
                          거절
                        </button>
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="gm-grid" style={{ maxWidth: 'none' }}>
            <div className="gm-field">
              <label htmlFor="sc-date">가능 날짜</label>
              <input id="sc-date" type="date" value={schDate} onChange={(e) => setSchDate(e.target.value)} />
            </div>
            <div className="gm-field">
              <label htmlFor="sc-start">시작</label>
              <input id="sc-start" type="time" value={schStart} onChange={(e) => setSchStart(e.target.value)} />
            </div>
            <div className="gm-field">
              <label htmlFor="sc-end">종료</label>
              <input id="sc-end" type="time" value={schEnd} onChange={(e) => setSchEnd(e.target.value)} />
            </div>
          </div>
          <div className="gm-actions">
            <button type="button" className="gm-btn" onClick={() => void addSchedule()} disabled={busy}>
              스케줄 추가
            </button>
          </div>
          <ul className="gm-list" style={{ marginTop: '0.85rem' }}>
            {schedules.length === 0 && <li>등록된 스케줄이 없습니다.</li>}
            {schedules.map((s) => (
              <li key={s.scheduleId}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span>
                    <strong>{s.availableDate}</strong> {formatTime(s.startTime)}–{formatTime(s.endTime)} · {s.status}
                    {s.isPaid ? ' · 결제됨' : ''}
                  </span>
                  <button
                    type="button"
                    className="gm-danger"
                    onClick={() => void removeSchedule(s.scheduleId)}
                    disabled={busy}
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
          </div>
        </section>
      )}
    </div>
  )
}
