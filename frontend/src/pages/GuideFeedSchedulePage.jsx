import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'
import { GuideCoursePanel } from './GuideCoursePanel.jsx'

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

function parseFeedHeading(content) {
  if (!content || !String(content).trim()) {
    return { title: '가이드 투어 피드', body: '' }
  }
  const lines = String(content).split(/\r?\n/)
  const title = lines[0]?.trim() || '가이드 투어 피드'
  const body = lines.slice(1).join('\n').trim()
  return { title, body: body || String(content).trim() }
}

function isInstagramUrl(url) {
  return typeof url === 'string' && url.includes('instagram.com')
}

function useToast() {
  const [toasts, setToasts] = useState([])
  const addToast = useCallback((message, type = 'success') => {
    const id = Date.now() + Math.random()
    setToasts((prev) => [...prev, { id, message, type }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 2000)
  }, [])
  return { toasts, addToast }
}

function Toast({ toasts }) {
  if (toasts.length === 0) return null
  return (
    <div style={{ position: 'fixed', bottom: '1.5rem', right: '1.5rem', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '0.5rem', pointerEvents: 'none' }}>
      {toasts.map((t) => (
        <div key={t.id} style={{ padding: '0.7rem 1.2rem', borderRadius: 10, background: t.type === 'success' ? '#15803d' : '#b91c1c', color: '#fff', fontSize: '0.88rem', fontWeight: 600, boxShadow: '0 4px 16px rgba(0,0,0,0.18)', minWidth: 180, maxWidth: 320 }}>
          {t.message}
        </div>
      ))}
    </div>
  )
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
  const [feedTitle, setFeedTitle] = useState('')
  const [feedBody, setFeedBody] = useState('')
  const [feedImages, setFeedImages] = useState([])
  const [feedImageMode, setFeedImageMode] = useState('file')
  const [urlInput, setUrlInput] = useState('')
  const [feedLocationTags, setFeedLocationTags] = useState([])
  const [showLocationInput, setShowLocationInput] = useState(false)
  const [locationInput, setLocationInput] = useState('')
  const [courseTarget, setCourseTarget] = useState(null)
  const { toasts, addToast } = useToast()
  const [blockedDates, setBlockedDates] = useState(() => new Set())
  const [busy, setBusy] = useState(false)
  const mainFileInputRef = useRef(null)
  const dragIdx = useRef(null)
  const currentTab = searchParams.get('tab') === 'schedule' ? 'schedule' : 'feed'
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })

  const reloadFeeds = useCallback(async (id) => {
    try {
      const res = await apiRequest(`/guides/${id}/feeds`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (res.ok) setFeeds(text ? JSON.parse(text) : [])
      else console.error('[reloadFeeds] 피드 목록 조회 실패', res.status)
    } catch (e) {
      console.error('[reloadFeeds] 네트워크 오류:', e)
    }
  }, [])

  const loadAll = useCallback(async (id) => {
    setLoadError('')
    try {
      const [fr, sr, pr, br] = await Promise.all([
        apiRequest(`/guides/${id}/feeds`, { method: 'GET', skipAuth: true }),
        apiRequest(`/guides/${id}/schedules`, { method: 'GET', skipAuth: true }),
        apiRequest(`/guides/${id}/schedules/pending`, { method: 'GET' }),
        apiRequest(`/guides/${id}/schedules/blocked`, { method: 'GET', skipAuth: true }),
      ])
      const ft = await fr.text()
      const st = await sr.text()
      const pt = await pr.text()
      const bt = await br.text()
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
      if (br.ok) {
        const blockedList = bt ? JSON.parse(bt) : []
        setBlockedDates(new Set(blockedList.map((s) => s.availableDate)))
      }
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '불러오기 실패')
    }
  }, [])

  useEffect(() => {
    if (!guideId) return
    fetchGuideMatchRequests(apiRequest)
      .then((data) => setGuideRequests(Array.isArray(data) ? data : []))
      .catch((e) => console.warn('매칭 요청 조회 실패:', e))
  }, [guideId])

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
          hasCourse: !!s.hasCourse,
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
    if (!guideId || !feedTitle.trim()) return
    // 파일 업로드(base64 data:URL)는 백엔드 페이로드 제한 초과로 전송 불가 → URL 모드 권장
    const validUrls = feedImages.map((img) => img.src).filter((src) => !src.startsWith('data:'))
    if (feedImages.length > 0 && validUrls.length < feedImages.length) {
      addToast('파일 이미지는 서버 용량 제한으로 저장되지 않습니다. URL 입력 탭을 이용해주세요.', 'error')
    }
    setBusy(true)
    try {
      const tagLine = feedLocationTags.length > 0 ? '\n' + feedLocationTags.map((t) => `#${t}`).join(' ') : ''
      const content = (feedTitle.trim() + '\n' + feedBody.trim() + tagLine).trim()
      const res = await apiRequest(`/guides/${guideId}/feeds`, {
        method: 'POST',
        json: { content, imageUrls: validUrls.length > 0 ? validUrls : undefined },
      })
      const text = await res.text()
      if (!res.ok) {
        const msg = await readJsonError(res, text)
        console.error('[addFeed] API error', res.status, msg)
        addToast(msg, 'error')
        return
      }
      setFeedTitle('')
      setFeedBody('')
      setFeedImages([])
      setUrlInput('')
      setFeedImageMode('file')
      setFeedLocationTags([])
      setLocationInput('')
      setShowLocationInput(false)
      addToast('등록되었습니다.')
      await reloadFeeds(guideId)
    } catch (e) {
      console.error('[addFeed] 네트워크/예외 오류:', e)
      addToast('피드 등록 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const removeFeed = async (feedId) => {
    if (!guideId || !window.confirm('이 피드를 삭제할까요?')) return
    setBusy(true)
    try {
      const res = await apiRequest(`/guides/${guideId}/feeds/${feedId}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        addToast(await readJsonError(res, text), 'error')
        return
      }
      await reloadFeeds(guideId)
    } catch {
      addToast('삭제 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const toggleBlock = async (dateStr) => {
    if (!guideId || busy) return
    setBusy(true)
    try {
      const isBlocked = blockedDates.has(dateStr)
      const res = await apiRequest(`/guides/${guideId}/schedules/block`, {
        method: isBlocked ? 'DELETE' : 'POST',
        json: { date: dateStr },
      })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      setBlockedDates((prev) => {
        const next = new Set(prev)
        if (isBlocked) next.delete(dateStr)
        else next.add(dateStr)
        return next
      })
      addToast(isBlocked ? `${dateStr} 예약 가능으로 변경됐어요 ✅` : `${dateStr} 예약 불가로 설정됐어요 🚫`)
      await loadAll(guideId)
    } catch {
      addToast('날짜 설정 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const acceptPendingSchedule = async (scheduleId) => {
    if (!guideId) return
    setBusy(true)
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/${scheduleId}/accept`, { method: 'POST' })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      await loadAll(guideId)
    } catch {
      addToast('스케줄 수락 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const rejectPendingSchedule = async (scheduleId) => {
    if (!guideId || !window.confirm('이 예약 대기 스케줄을 거절할까요?')) return
    setBusy(true)
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/${scheduleId}/reject`, { method: 'POST' })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      await loadAll(guideId)
    } catch {
      addToast('스케줄 거절 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const removeSchedule = async (scheduleId) => {
    if (!guideId || !window.confirm('이 스케줄을 삭제할까요?')) return
    setBusy(true)
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/${scheduleId}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        addToast(await readJsonError(res, text), 'error')
        return
      }
      await loadAll(guideId)
    } catch {
      addToast('스케줄 삭제 실패', 'error')
    } finally {
      setBusy(false)
    }
  }

  const onPickFiles = async (e) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (!files.length) return
    const remaining = 10 - feedImages.length
    const toProcess = files.slice(0, remaining)
    const oversized = toProcess.filter((f) => f.size > MAX_LOCAL_IMAGE_BYTES)
    if (oversized.length) addToast(`${oversized.length}개 파일이 2MB를 초과해 제외됐습니다.`, 'error')
    const valid = toProcess.filter((f) => f.size <= MAX_LOCAL_IMAGE_BYTES)
    const results = await Promise.allSettled(valid.map(readFileAsDataUrl))
    const newImgs = results
      .filter((r) => r.status === 'fulfilled')
      .map((r) => ({ id: Date.now() + Math.random(), src: r.value }))
    setFeedImages((prev) => [...prev, ...newImgs].slice(0, 10))
  }

  const addUrlImage = () => {
    const src = urlInput.trim()
    if (!src || feedImages.length >= 10) return
    setFeedImages((prev) => [...prev, { id: Date.now() + Math.random(), src }])
    setUrlInput('')
  }

  const removeImage = (id) => {
    setFeedImages((prev) => prev.filter((img) => img.id !== id))
  }

  const reorderImages = (dropIdx) => {
    if (dragIdx.current === null || dragIdx.current === dropIdx) return
    setFeedImages((prev) => {
      const next = [...prev]
      const [moved] = next.splice(dragIdx.current, 1)
      next.splice(dropIdx, 0, moved)
      return next
    })
    dragIdx.current = null
  }

  const addLocationTag = (raw) => {
    const tag = raw.trim().replace(/^#/, '')
    if (!tag || feedLocationTags.includes(tag)) return
    setFeedLocationTags((prev) => [...prev, tag])
    setLocationInput('')
  }

  const removeLocationTag = (tag) => {
    setFeedLocationTags((prev) => prev.filter((t) => t !== tag))
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
      <Toast toasts={toasts} />
      {currentTab === 'feed' ? (
        <section className="gfs-feed">
          <div className="gfs-badge">📸 피드 등록</div>

          {/* 이미지 입력 방식 탭 */}
          <div className="gfi-mode-tabs">
            {[
              { key: 'file', label: '파일 업로드' },
              { key: 'url', label: 'URL 입력' },
            ].map(({ key, label }) => (
              <button
                key={key}
                type="button"
                className={`gfi-mode-tab ${feedImageMode === key ? 'is-on' : ''}`}
                onClick={() => setFeedImageMode(key)}
              >
                {label}
              </button>
            ))}
            <button type="button" className="gfi-mode-tab gfi-mode-tab--disabled" disabled>
              인스타그램 (준비 중)
            </button>
          </div>

          {/* 파일 업로드 */}
          {feedImageMode === 'file' && feedImages.length < 10 && (
            <div className="gfi-file-row">
              <button type="button" className="gm-btn gm-btn--ghost" onClick={() => mainFileInputRef.current?.click()}>
                {feedImages.length === 0 ? '파일 선택' : `+ 추가 (${feedImages.length}/10)`}
              </button>
              <input
                ref={mainFileInputRef}
                type="file"
                accept="image/*"
                multiple
                style={{ display: 'none' }}
                onChange={(e) => void onPickFiles(e)}
              />
            </div>
          )}

          {/* URL 입력 */}
          {feedImageMode === 'url' && (
            <div className="gfi-file-row" style={{ gap: '0.5rem' }}>
              <input
                className="gfi-text-input"
                placeholder="https://example.com/image.jpg"
                value={urlInput}
                onChange={(e) => setUrlInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addUrlImage() } }}
              />
              <button
                type="button"
                className="gm-btn gm-btn--ghost"
                onClick={addUrlImage}
                disabled={!urlInput.trim() || feedImages.length >= 10}
              >
                추가
              </button>
            </div>
          )}

          {/* 썸네일 그리드 */}
          {feedImages.length > 0 ? (
            <div className="gfi-thumb-grid">
              {feedImages.map((img, idx) => (
                <div
                  key={img.id}
                  className="gfi-thumb-item"
                  draggable
                  onDragStart={() => { dragIdx.current = idx }}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={() => reorderImages(idx)}
                >
                  <img src={img.src} alt="" />
                  {idx === 0 && <span className="gfi-thumb-badge">대표</span>}
                  <button type="button" className="gfi-thumb-del" onClick={() => removeImage(img.id)} aria-label="이미지 삭제">×</button>
                </div>
              ))}
              {feedImageMode === 'file' && feedImages.length < 10 && (
                <button type="button" className="gfi-thumb-add" onClick={() => mainFileInputRef.current?.click()} aria-label="이미지 추가">+</button>
              )}
            </div>
          ) : (
            <div className="gfi-preview">
              <span className="gfi-preview-empty">이미지를 추가해보세요</span>
            </div>
          )}

          {/* 피드 내용 입력 */}
          <div className="gfs-content-wrap" style={{ marginTop: '1rem' }}>
            <input
              type="text"
              className="gfi-title-input"
              placeholder="예: 해방촌 골목골목, 진짜 서울을 보여드릴게요"
              value={feedTitle}
              onChange={(e) => setFeedTitle(e.target.value)}
              maxLength={100}
            />
            <textarea
              className="gfs-content"
              rows={4}
              maxLength={1000}
              placeholder="피드 내용과 장소를 입력해주세요..."
              value={feedBody}
              onChange={(e) => setFeedBody(e.target.value)}
            />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button type="button" className="gfs-tag-hint-btn" onClick={() => setShowLocationInput((v) => !v)}>
                📍 장소 태그 {showLocationInput ? '닫기' : '추가'}
              </button>
              <span className="gfi-char-count">{feedBody.length} / 1000</span>
            </div>
            {showLocationInput && (
              <div style={{ marginTop: '0.5rem' }}>
                <input
                  className="gi-tag-input"
                  placeholder="장소명 입력 후 Enter"
                  value={locationInput}
                  onChange={(e) => setLocationInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addLocationTag(locationInput) } }}
                />
                {feedLocationTags.length > 0 && (
                  <div className="gi-tag-list" style={{ marginTop: '0.4rem' }}>
                    {feedLocationTags.map((tag) => (
                      <span key={tag} className="gi-tag">
                        #{tag}
                        <button type="button" className="gi-tag-del" onClick={() => removeLocationTag(tag)} aria-label={`${tag} 삭제`}>×</button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.75rem' }}>
            <button
              type="button"
              className="gfs-upload-btn"
              onClick={() => void addFeed()}
              disabled={busy || !feedTitle.trim()}
            >
              {busy ? <><span className="gfi-spinner" aria-hidden="true" /> 업로드 중…</> : '업로드 🚀'}
            </button>
          </div>

          {/* 피드 목록 */}
          <p className="gm-hint" style={{ marginTop: '1.25rem', marginBottom: '0.5rem' }}>등록된 피드 {feeds.length}개</p>
          <ul className="gfi-feed-list">
            {feeds.length === 0 && (
              <li style={{ padding: '0.65rem 0.75rem', fontSize: '0.84rem', color: '#9ca3af' }}>등록된 피드가 없습니다.</li>
            )}
            {feeds.map((f) => {
              const { title } = parseFeedHeading(f.content)
              const dateStr = f.createdAt ? String(f.createdAt).slice(0, 10).replace(/-/g, '.') : null
              return (
                <li key={f.feedId} className="gfi-feed-card">
                  <div className="gfi-feed-thumb">
                    {(() => {
                      const thumb = f.imageUrls?.[0] ?? f.imageUrl ?? null
                      if (!thumb) return <div className="gfi-thumb-empty" />
                      if (isInstagramUrl(thumb)) return <span className="gfi-thumb-insta">📷</span>
                      return <img src={thumb} alt="" />
                    })()}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p className="gfi-feed-title">{title}</p>
                    {dateStr && <p className="gfi-feed-date">{dateStr}</p>}
                  </div>
                  <button type="button" className="gm-danger" onClick={() => void removeFeed(f.feedId)} disabled={busy}>
                    삭제
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      ) : (
        <section className="gss-wrap">
          <header className="gss-head">
            <h2>🗓️ 스케줄 관리</h2>
            <p>투어 가능 일정을 관리하고 예약 현황을 확인하세요.</p>
          </header>
          <div className="gss-banner">
            💡 기본적으로 모든 날짜는 예약 가능입니다. 막고 싶은 날짜를 클릭하면 예약 불가로 설정됩니다.
          </div>
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
                  const isBlocked = blockedDates.has(dayKey)
                  const hasBooked = daySchedules.some((s) => s.status === 'BOOKED')
                  const hasPending = daySchedules.some((s) => s.status === 'PENDING')
                  let kind = cell.inMonth ? 'available' : 'plain'
                  if (isBlocked) kind = 'blocked'
                  else if (hasBooked) kind = 'booked'
                  else if (hasPending) kind = 'pending'
                  const today = ymd(new Date()) === dayKey
                  const canToggle = cell.inMonth && !hasBooked && !hasPending
                  return (
                    <button
                      key={cell.key}
                      type="button"
                      className={`gss-day ${cell.inMonth ? '' : 'is-out'} ${kind !== 'plain' ? `is-${kind}` : ''} ${today ? 'is-today' : ''}`}
                      title={isBlocked ? `${dayKey} · 예약 불가 (클릭 시 해제)` : canToggle ? `${dayKey} · 클릭 시 예약 불가 설정` : dayKey}
                      onClick={canToggle ? () => void toggleBlock(dayKey) : undefined}
                      disabled={!cell.inMonth || busy}
                    >
                      <span>{cell.date.getDate()}</span>
                      {isBlocked && <small className="gss-blocked-x">✕</small>}
                      {!isBlocked && daySchedules.length > 0 && <small>{daySchedules.length}</small>}
                    </button>
                  )
                })}
              </div>
              <div className="gss-legend">
                <span className="gss-dot gss-dot--available">예약 가능</span>
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
                  <section key={tour.scheduleId} className={`gss-tour-card${tour.hasCourse ? ' course-done' : ''}`}>
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
                    {tour.status === 'BOOKED' && (
                      <button
                        type="button"
                        className="gss-course-btn"
                        onClick={() => setCourseTarget({
                          scheduleId: tour.scheduleId,
                          availableDate: tour.availableDate,
                          startTime: tour.startTime,
                          endTime: tour.endTime,
                          destination: tour.destination,
                        })}
                      >
                        {tour.hasCourse ? '코스 수정하기 →' : '코스 작성하기 →'}
                      </button>
                    )}
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
          <p className="gm-hint" style={{ marginBottom: '0.5rem' }}>
            캘린더에서 날짜를 클릭해 예약 불가 설정/해제할 수 있습니다. (BOOKED·PENDING 날짜는 변경 불가)
          </p>
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
      {courseTarget && (
        <GuideCoursePanel
          guideId={guideId}
          schedule={courseTarget}
          onClose={() => setCourseTarget(null)}
          onSaved={() => { setCourseTarget(null); void loadAll(guideId) }}
        />
      )}
    </div>
  )
}
