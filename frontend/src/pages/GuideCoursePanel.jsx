import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest } from '../api/client.js'
import { DEFAULT_KOREA_CENTER, geocodeAddressOnly, getUserLatLng, resolveLatLng } from '../lib/kakaoGeocode.js'
import { loadKakaoSdk } from '../lib/kakaoMapSdk.js'
import './GuideCoursePanel.css'

const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

function createPinOverlay(kakao, map, latlng, idx, name) {
  const root = document.createElement('div')
  root.className = 'gcp-pin'
  root.innerHTML = `<span class="gcp-pin-num">${idx + 1}</span><span class="gcp-pin-label">${name}</span>`
  return new kakao.maps.CustomOverlay({ map, position: latlng, yAnchor: 1.25, content: root })
}

// ── courseDetail 직렬화/파싱 ───────────────────────────────────────────
// 형식: "1. 장소명 | 시간 | 설명\n2. ..."
export function serializeCourse(spots) {
  return spots
    .filter((s) => s.name.trim())
    .map((s, i) => `${i + 1}. ${s.name.trim()} | ${s.time.trim()} | ${s.desc.trim()}`)
    .join('\n')
}

export function parseCourseDetail(text) {
  if (!text?.trim()) return [{ name: '', time: '', desc: '' }]
  return text
    .split('\n')
    .filter(Boolean)
    .map((line) => {
      const parts = line.replace(/^\d+\.\s*/, '').split('|')
      return {
        name: parts[0]?.trim() ?? '',
        time: parts[1]?.trim() ?? '',
        desc: parts[2]?.trim() ?? '',
      }
    })
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────
/**
 * @param {{
 *   guideId: number,
 *   schedule: { scheduleId: number, availableDate: string, startTime: string, endTime: string, destination?: string },
 *   initialForm?: { meetingPoint?: string, guideMessage?: string, courseDetail?: string, isPaid?: boolean } | null,
 *   standalone?: boolean,
 *   hideHeader?: boolean,
 *   onClose: () => void,
 *   onSaved: (savedForm?: { meetingPoint?: string, guideMessage?: string, courseDetail?: string, isPaid?: boolean }) => void,
 * }} props
 */
export function GuideCoursePanel({
  guideId,
  schedule,
  initialForm = null,
  standalone = false,
  hideHeader = false,
  onClose,
  onSaved,
}) {
  const mapRef = useRef(null)
  const mapInstanceRef = useRef(null)
  const overlaysRef = useRef([])
  const polylineRef = useRef([])

  const [meetingPoint, setMeetingPoint] = useState('')
  const [guideMessage, setGuideMessage] = useState('')
  const [spots, setSpots] = useState([{ name: '', time: '', desc: '' }])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saveMsg, setSaveMsg] = useState('')
  const [mapErr, setMapErr] = useState('')
  // geocodeStatus: 'idle' | 'ok' | 'pending' | 'error' per spot index
  const [geoStatus, setGeoStatus] = useState([])
  const debounceTimerRef = useRef(null)
  const dragSpotIdx = useRef(null)
  const spotsRef = useRef(spots)

  // ── 초기 데이터 로드 (GET form) ──────────────────────────────────────
  useEffect(() => {
    if (!guideId || !schedule?.scheduleId) return
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const res = await apiRequest(
          `/guides/${guideId}/schedules/${schedule.scheduleId}/form`,
          { method: 'GET' },
        )
        const text = await res.text()
        if (!res.ok) {
          // 404면 빈 폼으로 시작 (아직 작성 안 한 케이스)
          if (res.status !== 404) throw new Error(text || '불러오기 실패')
          if (!cancelled && initialForm) {
            const mergedDetail = String(initialForm.courseDetail ?? '').trim()
            const parsed = parseCourseDetail(mergedDetail)
            setMeetingPoint(initialForm.meetingPoint ?? '')
            setGuideMessage(initialForm.guideMessage ?? '')
            setSpots(parsed.length ? parsed : [{ name: '', time: '', desc: '' }])
            setGeoStatus(parsed.map((s) => (s.name ? 'ok' : 'idle')))
          }
        } else {
          const data = text ? JSON.parse(text) : {}
          const merged = {
            ...data,
            // 결제 전 마스킹으로 courseDetail이 비어오는 경우를 대비해, 직전에 저장한 로컬 값을 우선 표시한다.
            meetingPoint: data?.meetingPoint ?? initialForm?.meetingPoint ?? '',
            guideMessage: data?.guideMessage ?? initialForm?.guideMessage ?? '',
            courseDetail: data?.courseDetail ?? initialForm?.courseDetail ?? '',
          }
          if (!cancelled) {
            setMeetingPoint(merged.meetingPoint)
            setGuideMessage(merged.guideMessage)
            const parsed = parseCourseDetail(merged.courseDetail)
            setSpots(parsed.length ? parsed : [{ name: '', time: '', desc: '' }])
            setGeoStatus(parsed.map((s) => (s.name ? 'ok' : 'idle')))
          }
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [guideId, schedule?.scheduleId, initialForm])

  useEffect(() => { spotsRef.current = spots }, [spots])

  // ── 지도 마커/경로 업데이트 ───────────────────────────────────────────
  const updateMap = useCallback(async (currentSpots) => {
    const kakao = window.kakao
    const map = mapInstanceRef.current
    if (!kakao?.maps || !map) return

    overlaysRef.current.forEach((o) => o.setMap(null))
    overlaysRef.current = []
    polylineRef.current.forEach((p) => p.setMap(null))
    polylineRef.current = []

    const namedEntries = currentSpots
      .map((s, i) => ({ spot: s, origIdx: i }))
      .filter(({ spot }) => spot.name.trim())
    if (namedEntries.length === 0) return

    const regionHint = schedule?.destination?.trim() ?? ''

    const results = await Promise.all(
      namedEntries.map(async ({ spot, origIdx }) => ({
        point: await resolveLatLng(kakao, spot.name, { regionHint }),
        origIdx,
        name: spot.name,
      })),
    )

    // geocoding 결과로 geoStatus 갱신 (ok / error) — fallback 좌표 사용 안 함
    setGeoStatus((prev) => {
      const next = [...prev]
      results.forEach(({ point, origIdx }) => { next[origIdx] = point ? 'ok' : 'error' })
      return next
    })

    const validResults = results.filter((r) => r.point !== null)
    if (validResults.length === 0) return

    const path = []
    const bounds = new kakao.maps.LatLngBounds()
    validResults.forEach(({ point, name }, pinIdx) => {
      const latlng = new kakao.maps.LatLng(point.lat, point.lng)
      path.push(latlng)
      bounds.extend(latlng)
      overlaysRef.current.push(createPinOverlay(kakao, map, latlng, pinIdx, name))
    })

    if (path.length > 1) {
      polylineRef.current.push(
        new kakao.maps.Polyline({ map, path, strokeWeight: 8, strokeColor: '#f9fafb', strokeOpacity: 0.9, strokeStyle: 'solid' }),
      )
      polylineRef.current.push(
        new kakao.maps.Polyline({ map, path, strokeWeight: 3, strokeColor: '#1f2328', strokeOpacity: 0.85, strokeStyle: 'shortdash' }),
      )
    }
    if (path.length > 0) map.setBounds(bounds)
  }, [schedule?.destination])

  // ── 카카오맵 초기화 (폼 로딩 후에만 #gcp-map DOM이 생김) ─────────────────
  useEffect(() => {
    if (loading) return
    if (!mapRef.current) return
    let cancelled = false

    overlaysRef.current.forEach((o) => o.setMap(null))
    overlaysRef.current = []
    polylineRef.current.forEach((p) => p.setMap(null))
    polylineRef.current = []
    mapInstanceRef.current = null
    if (mapRef.current) mapRef.current.innerHTML = ''

    ;(async () => {
      try {
        setMapErr('')
        const kakao = await loadKakaoSdk(KAKAO_APP_KEY)
        if (cancelled || !mapRef.current) return

        let initial = { ...DEFAULT_KOREA_CENTER }
        const userPos = await getUserLatLng()
        if (userPos) {
          initial = userPos
        } else if (schedule?.destination?.trim()) {
          const g = await geocodeAddressOnly(kakao, schedule.destination.trim())
          if (g) initial = g
          else {
            const k = await resolveLatLng(kakao, schedule.destination.trim())
            if (k) initial = k
          }
        }

        const map = new kakao.maps.Map(mapRef.current, {
          center: new kakao.maps.LatLng(initial.lat, initial.lng),
          level: 8,
        })
        mapInstanceRef.current = map
        await updateMap(spotsRef.current)
      } catch (e) {
        if (!cancelled) setMapErr(e instanceof Error ? e.message : '지도 오류')
      }
    })()

    return () => {
      cancelled = true
      overlaysRef.current.forEach((o) => o.setMap(null))
      overlaysRef.current = []
      polylineRef.current.forEach((p) => p.setMap(null))
      polylineRef.current = []
      mapInstanceRef.current = null
    }
  }, [loading, schedule?.scheduleId, schedule?.destination, updateMap])

  // ── 스팟 필드 업데이트 + name 변경 시 300ms 디바운스 geocoding ────────
  const updateSpot = (idx, field, value) => {
    setSpots((prev) => {
      const next = [...prev]
      next[idx] = { ...next[idx], [field]: value }
      spotsRef.current = next
      return next
    })
    if (field === 'name') {
      setGeoStatus((prev) => {
        const next = [...prev]
        next[idx] = 'idle'
        return next
      })
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)
      debounceTimerRef.current = setTimeout(() => {
        if (value.trim()) {
          setGeoStatus((prev) => {
            const next = [...prev]
            next[idx] = 'pending'
            return next
          })
          void updateMap(spotsRef.current)
        }
      }, 300)
    }
  }

  const addSpot = () => {
    setSpots((prev) => {
      const next = [...prev, { name: '', time: '', desc: '' }]
      spotsRef.current = next
      return next
    })
    setGeoStatus((prev) => [...prev, 'idle'])
  }

  const removeSpot = (idx) => {
    if (spots.length <= 1) return
    const newSpots = spots.filter((_, i) => i !== idx)
    spotsRef.current = newSpots
    setSpots(newSpots)
    setGeoStatus((prev) => prev.filter((_, i) => i !== idx))
    void updateMap(newSpots)
  }

  // ── 드래그 앤 드롭 순서 변경 ─────────────────────────────────────────
  const reorderSpots = (dropIdx) => {
    const from = dragSpotIdx.current
    if (from === null || from === dropIdx) return
    dragSpotIdx.current = null
    setSpots((prev) => {
      const next = [...prev]
      const [moved] = next.splice(from, 1)
      next.splice(dropIdx, 0, moved)
      spotsRef.current = next
      return next
    })
    setGeoStatus((prev) => {
      const next = [...prev]
      const [moved] = next.splice(from, 1)
      next.splice(dropIdx, 0, moved)
      return next
    })
    setTimeout(() => void updateMap(spotsRef.current), 0)
  }

  // ── 저장 (PUT form) ───────────────────────────────────────────────────
  const handleSave = async () => {
    if (!guideId || !schedule?.scheduleId) return
    setSaving(true)
    setSaveMsg('')
    setError('')
    try {
      const payload = {
        meetingPoint: meetingPoint.trim(),
        guideMessage: guideMessage.trim(),
        courseDetail: serializeCourse(spots),
      }
      let res = await apiRequest(
        `/guides/${guideId}/schedules/${schedule.scheduleId}/form`,
        {
          method: 'PUT',
          json: payload,
        },
      )
      let text = await res.text()
      if (!res.ok && res.status === 409) {
        const acceptRes = await apiRequest(
          `/guides/${guideId}/schedules/${schedule.scheduleId}/accept`,
          { method: 'POST' },
        )
        const acceptText = await acceptRes.text()
        if (!acceptRes.ok && acceptRes.status !== 409) {
          const aj = (() => { try { return JSON.parse(acceptText) } catch { return null } })()
          throw new Error(aj?.message ?? acceptText ?? '코스 저장 준비 실패')
        }
        res = await apiRequest(
          `/guides/${guideId}/schedules/${schedule.scheduleId}/form`,
          {
            method: 'PUT',
            json: payload,
          },
        )
        text = await res.text()
      }
      if (!res.ok) {
        const j = (() => { try { return JSON.parse(text) } catch { return null } })()
        throw new Error(j?.message ?? text ?? '저장 실패')
      }
      const savedForm = (() => {
        if (!text) return null
        try {
          return JSON.parse(text)
        } catch {
          return null
        }
      })()
      const mergedSavedForm = {
        ...(savedForm ?? {}),
        // 결제 전에는 응답 courseDetail이 null로 마스킹되므로, 저장 직전 원본 값을 콜백으로 전달한다.
        courseDetail: payload.courseDetail,
        meetingPoint: savedForm?.meetingPoint ?? payload.meetingPoint,
        guideMessage: savedForm?.guideMessage ?? payload.guideMessage,
      }
      setSaveMsg('저장되었습니다!')
      setTimeout(() => setSaveMsg(''), 2500)
      onSaved(mergedSavedForm)
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장 실패')
    } finally {
      setSaving(false)
    }
  }

  const panel = (
    <aside
      className={`gcp-panel${standalone ? ' gcp-panel--page' : ''}${standalone && hideHeader ? ' gcp-panel--no-header' : ''}`}
      role={standalone ? undefined : 'dialog'}
      aria-modal={standalone ? undefined : 'true'}
      aria-label="코스 작성"
    >
        {/* 헤더 — 전용 페이지에서는 상단 히어로로 대체 가능 */}
        {!hideHeader && (
          <header className="gcp-header">
            <div className="gcp-header-left">
              <span className="gcp-header-label">코스 작성</span>
              <h2 className="gcp-header-title">
                {schedule.availableDate}
                {schedule.destination ? ` · ${schedule.destination}` : ''}
              </h2>
              <p className="gcp-header-meta">
                {schedule.startTime} ~ {schedule.endTime}
              </p>
            </div>
            <button type="button" className="gcp-close" onClick={onClose} aria-label={standalone ? '목록으로' : '닫기'}>
              {standalone ? '←' : '✕'}
            </button>
          </header>
        )}

        {loading ? (
          <div className="gcp-loading">불러오는 중…</div>
        ) : (
          <div className="gcp-body">
            {error && <p className="gcp-error">{error}</p>}
            {saveMsg && <p className="gcp-success">{saveMsg}</p>}

            {/* 지도 — 스팟 수에 따라 높이 동적 조절 */}
            <div className="gcp-map-wrap">
              <div
                ref={mapRef}
                className="gcp-map"
                style={{ height: spots.length <= 2 ? 200 : spots.length <= 5 ? 280 : 360 }}
              />
              {mapErr && <p className="gcp-map-err">{mapErr}</p>}
              {!mapErr && spots.filter((s) => s.name).length === 0 && (
                <div className="gcp-map-empty">
                  <span>장소명을 입력하면 지도에 표시됩니다</span>
                </div>
              )}
              {!mapErr && (
                <p className="gcp-map-loc-hint">
                  지도는 브라우저 위치 권한(선택)과 일정 목적지·장소명 검색을 반영합니다. 제주 고정이 아니라 검색 결과에 맞춰 이동해요.
                </p>
              )}
            </div>

            {/* 집합 장소 */}
            <section className="gcp-section">
              <label className="gcp-label" htmlFor="gcp-meeting">
                집합 장소
                <span className="gcp-label-hint">결제 전 게스트에게도 공개됩니다</span>
              </label>
              <input
                id="gcp-meeting"
                className="gcp-input"
                type="text"
                placeholder="예: 제주 올레시장 정문 앞"
                value={meetingPoint}
                onChange={(e) => setMeetingPoint(e.target.value)}
              />
            </section>

            {/* 안내 메시지 */}
            <section className="gcp-section">
              <label className="gcp-label" htmlFor="gcp-message">
                안내 메시지
                <span className="gcp-label-hint">결제 전 게스트에게도 공개됩니다</span>
              </label>
              <textarea
                id="gcp-message"
                className="gcp-textarea"
                rows={2}
                placeholder="게스트에게 전달할 안내 메시지를 입력하세요"
                value={guideMessage}
                onChange={(e) => setGuideMessage(e.target.value)}
              />
            </section>

            {/* 코스 스팟 */}
            <section className="gcp-section">
              <div className="gcp-label">
                코스 스팟
                <span className="gcp-label-hint">결제 완료 후 게스트에게 공개됩니다</span>
              </div>
              <ul className="gcp-spot-list">
                {spots.map((spot, idx) => {
                  const status = geoStatus[idx] ?? 'idle'
                  return (
                    <li
                      key={idx}
                      className="gcp-spot-item"
                      draggable
                      onDragStart={() => { dragSpotIdx.current = idx }}
                      onDragOver={(e) => e.preventDefault()}
                      onDrop={() => reorderSpots(idx)}
                    >
                      <div className="gcp-spot-head">
                        <span className="gcp-drag-handle" aria-hidden="true">⠿</span>
                        <span className="gcp-spot-num">{idx + 1}</span>
                        <input
                          className="gcp-input gcp-input--spot-name"
                          type="text"
                          placeholder="장소명"
                          value={spot.name}
                          onChange={(e) => updateSpot(idx, 'name', e.target.value)}
                        />
                        {spots.length > 1 && (
                          <button
                            type="button"
                            className="gcp-spot-remove"
                            onClick={() => removeSpot(idx)}
                            aria-label="스팟 삭제"
                          >
                            ✕
                          </button>
                        )}
                      </div>
                      <div className="gcp-spot-row2">
                        <input
                          className="gcp-input"
                          type="text"
                          placeholder="시간 (예: 오전 10:00)"
                          value={spot.time}
                          onChange={(e) => updateSpot(idx, 'time', e.target.value)}
                        />
                        <input
                          className="gcp-input"
                          type="text"
                          placeholder="간단한 설명"
                          value={spot.desc}
                          onChange={(e) => updateSpot(idx, 'desc', e.target.value)}
                        />
                      </div>
                      <p className={`gcp-geo-status gcp-geo-status--${status}`}>
                        {status === 'ok' && spot.name && '✓ 지도에 표시됨'}
                        {status === 'pending' && '⋯ 지도 반영 중'}
                        {status === 'error' && spot.name && '⚠ 장소를 찾을 수 없어요'}
                      </p>
                    </li>
                  )
                })}
              </ul>
              <button type="button" className="gcp-add-spot" onClick={addSpot}>
                + 스팟 추가
              </button>
            </section>

            {/* 액션 */}
            <div className="gcp-actions">
              <button type="button" className="gcp-btn gcp-btn--ghost" onClick={onClose}>
                취소
              </button>
              <button
                type="button"
                className="gcp-btn gcp-btn--primary"
                onClick={() => void handleSave()}
                disabled={saving}
              >
                {saving ? '저장 중…' : '저장하기'}
              </button>
            </div>
          </div>
        )}
      </aside>
  )

  if (standalone) {
    return <div className="gcp-page-wrap">{panel}</div>
  }

  return (
    <div className="gcp-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      {panel}
    </div>
  )
}
