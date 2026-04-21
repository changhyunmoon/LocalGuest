import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'
import { GuideCoursePanel } from './GuideCoursePanel.jsx'
import { GuideScheduleSection } from './GuideScheduleSection.jsx'

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

function formatScheduleTime(t) {
  if (t == null) return ''
  if (typeof t === 'string') return t.length >= 5 ? t.slice(0, 5) : t
  return String(t)
}

function isWholeDayOpenSlot(s) {
  if (!s || s.status !== 'AVAILABLE') return false
  const st = formatScheduleTime(s.startTime)
  const en = formatScheduleTime(s.endTime)
  return st.startsWith('00:00') && (en.startsWith('23:59') || en === '23:59')
}

/** POST 직전 서버 기준으로 종일 AVAILABLE 여부 확인 (블록 해제 직후 React state와 불일치 방지) */
async function fetchSchedulesSnapshot(apiReq, guideId) {
  const res = await apiReq(`/guides/${guideId}/schedules`, { method: 'GET', skipAuth: true })
  const text = await res.text()
  if (!res.ok) return null
  return text ? JSON.parse(text) : []
}

function hasWholeDayAvailableSlot(list, dateStr) {
  return list.some((s) => s.availableDate === dateStr && s.status === 'AVAILABLE' && isWholeDayOpenSlot(s))
}

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
  const [savedCourseForms, setSavedCourseForms] = useState({})
  const { toasts, addToast } = useToast()
  const [blockedDates, setBlockedDates] = useState(() => new Set())
  const [busy, setBusy] = useState(false)
  /** 스케줄 API 연속 클릭 방지 — React state `busy`보다 먼저 막음 */
  const scheduleOpLockRef = useRef(false)
  const mainFileInputRef = useRef(null)
  const dragIdx = useRef(null)
  const currentTab = searchParams.get('tab') === 'schedule' ? 'schedule' : 'feed'

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

  /** 스케줄/블록/대기만 갱신 — 피드 재조회 없음 (달력 토글 시 렉 방지) */
  const reloadScheduleData = useCallback(async (id) => {
    try {
      const [sr, pr, br] = await Promise.all([
        apiRequest(`/guides/${id}/schedules`, { method: 'GET', skipAuth: true }),
        apiRequest(`/guides/${id}/schedules/pending`, { method: 'GET' }),
        apiRequest(`/guides/${id}/schedules/blocked`, { method: 'GET', skipAuth: true }),
      ])
      const st = await sr.text()
      const pt = await pr.text()
      const bt = await br.text()
      if (!sr.ok) {
        throw new Error(await readJsonError(sr, st))
      }
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
      console.warn('[reloadScheduleData]', e)
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

  /** 예약 요청 막기 (BLOCKED) */
  const setDayBlocked = async (dateStr) => {
    if (!guideId || scheduleOpLockRef.current || busy) return
    if (blockedDates.has(dateStr)) return
    scheduleOpLockRef.current = true
    setBusy(true)
    try {
      const res = await apiRequest(`/guides/${guideId}/schedules/block`, {
        method: 'POST',
        json: { date: dateStr },
      })
      const text = await res.text()
      if (!res.ok) {
        addToast(await readJsonError(res, text), 'error')
        return
      }
      setBlockedDates((prev) => new Set(prev).add(dateStr))
      addToast(`${dateStr} 예약 요청을 받지 않아요 🚫`)
      await reloadScheduleData(guideId)
    } catch {
      addToast('날짜 설정 실패', 'error')
    } finally {
      scheduleOpLockRef.current = false
      setBusy(false)
    }
  }

  /** 비어 있음 → 예약 받기: 종일 AVAILABLE 슬롯 생성 (막힌 날이면 먼저 해제) */
  const activateReceiving = async (dateStr) => {
    if (!guideId || scheduleOpLockRef.current || busy) return
    if (!blockedDates.has(dateStr) && hasWholeDayAvailableSlot(schedules, dateStr)) {
      addToast('이미 이 날은 예약을 받는 설정이에요')
      return
    }
    scheduleOpLockRef.current = true
    setBusy(true)
    try {
      if (blockedDates.has(dateStr)) {
        const u = await apiRequest(`/guides/${guideId}/schedules/block`, {
          method: 'DELETE',
          json: { date: dateStr },
        })
        const ut = await u.text()
        if (!u.ok) {
          addToast(await readJsonError(u, ut), 'error')
          return
        }
        setBlockedDates((prev) => {
          const next = new Set(prev)
          next.delete(dateStr)
          return next
        })
      }
      const snap = await fetchSchedulesSnapshot(apiRequest, guideId)
      const list = snap ?? schedules
      if (hasWholeDayAvailableSlot(list, dateStr)) {
        addToast('이미 이 날은 예약을 받는 설정이에요')
        await reloadScheduleData(guideId)
        return
      }
      const res = await apiRequest(`/guides/${guideId}/schedules`, {
        method: 'POST',
        json: { availableDate: dateStr, startTime: '00:00:00', endTime: '23:59:00' },
      })
      const text = await res.text()
      if (!res.ok) {
        if (res.status === 409) {
          addToast('이미 이 날은 예약을 받는 설정이에요')
          await reloadScheduleData(guideId)
          return
        }
        addToast(await readJsonError(res, text), 'error')
        return
      }
      addToast(`${dateStr} — 이 날 예약 요청을 받도록 설정했어요`)
      await reloadScheduleData(guideId)
    } catch {
      addToast('설정에 실패했어요', 'error')
    } finally {
      scheduleOpLockRef.current = false
      setBusy(false)
    }
  }

  /** 종일(00:00~23:59) 예약 받기 슬롯만 삭제 → 중립. 시간대별 일정은 유지 */
  const clearOpenDay = async (dateStr) => {
    if (!guideId || scheduleOpLockRef.current || busy) return
    const markers = schedules.filter((s) => s.availableDate === dateStr && isWholeDayOpenSlot(s))
    if (markers.length === 0) {
      addToast('종일로 켠 "예약 받기"만 여기서 끌 수 있어요. 다른 시간대는 목록에서 삭제해 주세요.', 'error')
      return
    }
    if (!window.confirm('이 날짜의 종일 예약 받기 설정을 지울까요? (예약을 막지는 않아요)')) return
    scheduleOpLockRef.current = true
    setBusy(true)
    try {
      for (const s of markers) {
        const res = await apiRequest(`/guides/${guideId}/schedules/${s.scheduleId}`, { method: 'DELETE' })
        if (!res.ok) {
          const t = await res.text()
          addToast(await readJsonError(res, t), 'error')
          return
        }
      }
      addToast('이 날을 비었습니다.')
      await reloadScheduleData(guideId)
    } catch {
      addToast('삭제에 실패했어요', 'error')
    } finally {
      scheduleOpLockRef.current = false
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
        <>
          <header className="gss-head gss-head--schedule">
            <h2>🗓️ 스케줄 관리</h2>
            <p>투어 가능 일정을 관리하고 예약 현황을 확인하세요.</p>
          </header>
          <div className="gss-banner gss-banner--schedule">
            <span className="gss-banner__icon" aria-hidden="true">💡</span>
            <span>
              달력은 기본이 <strong>비어 있음</strong>이에요. 예약 요청을 받을 날만 날짜를 눌러 <strong>예약 받기</strong>를 켜 주세요. (월을 바꿔도 자동으로 켜지지 않아요.)
            </span>
          </div>
          <GuideScheduleSection
            schedules={schedules}
            pendingSchedules={pendingSchedules}
            blockedDates={blockedDates}
            busy={busy}
            onActivateReceiving={activateReceiving}
            onSetBlocked={setDayBlocked}
            onClearOpenDay={clearOpenDay}
            onOpenCourse={(tour) => setCourseTarget(tour)}
            requestsByScheduleId={requestsByScheduleId}
          />
        </>
      )}
      {courseTarget && (
        <GuideCoursePanel
          guideId={guideId}
          schedule={courseTarget}
          initialForm={savedCourseForms[courseTarget.scheduleId] ?? null}
          onClose={() => setCourseTarget(null)}
          onSaved={(savedForm) => {
            if (courseTarget?.scheduleId != null && savedForm) {
              const sid = courseTarget.scheduleId
              setSavedCourseForms((prev) => ({
                ...prev,
                [sid]: {
                  ...prev[sid],
                  ...savedForm,
                },
              }))
              setSchedules((prev) =>
                prev.map((row) => (
                  row.scheduleId === sid
                    ? { ...row, hasCourse: true }
                    : row
                )),
              )
            }
            setCourseTarget(null)
            void reloadScheduleData(guideId)
          }}
        />
      )}
    </div>
  )
}
