import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useParams, useLocation, useNavigate } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { ReviewCarousel } from '../components/ReviewCarousel.jsx'
import { apiRequest } from '../api/client'
import { extractReviewListFromPage } from '../lib/reviewPage.js'
import { useAuth } from '../context/useAuth.js'
import { buildTravelDnaPreview, loadTravelDna } from '../lib/travelDna.js'

import './GuideDetailPage.css'

const WEEKDAYS_SHORT = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

/**
 * @param {unknown} raw
 * @returns {string}
 */
function formatScheduleDate(raw) {
  if (raw == null) return ''
  const s = String(raw)
  return s.length >= 10 ? s.slice(0, 10) : s
}

function parseDateOnly(raw) {
  const key = formatScheduleDate(raw)
  if (!key) return null
  const d = new Date(`${key}T00:00:00`)
  return Number.isNaN(d.getTime()) ? null : d
}

function ymd(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function isPastDateKey(dateKey) {
  const d = parseDateOnly(dateKey)
  if (!d) return true
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return d < today
}

/**
 * 백엔드 응답 포맷 차이를 흡수해 스케줄 배열을 추출한다.
 * @param {unknown} raw
 * @returns {Array<any>}
 */
function toScheduleList(raw) {
  if (Array.isArray(raw)) return raw
  if (raw && typeof raw === 'object') {
    const obj = /** @type {{ items?: unknown, data?: unknown, list?: unknown, schedules?: unknown, content?: unknown }} */ (raw)
    if (Array.isArray(obj.items)) return obj.items
    if (Array.isArray(obj.data)) return obj.data
    if (Array.isArray(obj.list)) return obj.list
    if (Array.isArray(obj.schedules)) return obj.schedules
    if (Array.isArray(obj.content)) return obj.content
  }
  return []
}

/**
 * @param {unknown} t
 * @returns {string}
 */
function formatLocalTime(t) {
  if (t == null) return ''
  if (typeof t === 'string') return t.slice(0, 5)
  if (typeof t === 'object' && t.hour != null) {
    return `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}`
  }
  return String(t)
}

/**
 * status 가 비어 있어도(구버전/임시 응답) 일단 예약 후보로 본다.
 * @param {any} schedule
 * @returns {boolean}
 */
function isBookableSchedule(schedule) {
  const normalized = String(schedule?.status ?? '')
    .trim()
    .toUpperCase()
  if (!normalized) return true
  return normalized === 'AVAILABLE'
}

/**
 * 응답 키가 달라도 화면에서 공통 형태로 사용한다.
 * @param {any} schedule
 */
function normalizeSchedule(schedule) {
  const scheduleId =
    schedule?.scheduleId != null
      ? Number(schedule.scheduleId)
      : schedule?.id != null
        ? Number(schedule.id)
        : null
  return {
    ...schedule,
    scheduleId,
  }
}

function compareScheduleTime(a, b) {
  const ad = `${formatScheduleDate(a?.availableDate)} ${formatLocalTime(a?.startTime)}`
  const bd = `${formatScheduleDate(b?.availableDate)} ${formatLocalTime(b?.startTime)}`
  return ad.localeCompare(bd)
}

/**
 * @param {unknown} guideStyle
 * @returns {string[]}
 */
function parseGuideStyleTags(guideStyle) {
  if (guideStyle == null || guideStyle === '') return []
  return String(guideStyle)
    .split(/[,/|]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 3)
    .map((t) => (t.startsWith('#') ? t : `#${t}`))
}

/**
 * @param {unknown} content
 * @returns {string}
 */
function feedCardTitle(content) {
  if (!content || !String(content).trim()) return '피드'
  const line = String(content).split(/\r?\n/)[0]?.trim()
  if (!line) return '피드'
  return line.length > 72 ? `${line.slice(0, 72)}…` : line
}

/**
 * @param {unknown} content
 * @returns {string}
 */
function feedSnippet(content) {
  if (!content) return ''
  const lines = String(content).split(/\r?\n/)
  const rest = lines.slice(1).join(' ').trim()
  const chunk = rest || lines[0]?.trim() || ''
  return chunk.length > 140 ? `${chunk.slice(0, 140)}…` : chunk
}

/**
 * @param {unknown} raw
 * @returns {string}
 */
function formatCareerDate(raw) {
  if (raw == null || raw === '') return ''
  const s = String(raw)
  return s.length >= 10 ? s.slice(0, 10) : s
}

/**
 * @param {any} feed
 * @returns {string}
 */
function feedPrimaryImage(feed) {
  const urls = Array.isArray(feed?.imageUrls) ? feed.imageUrls : []
  const u0 = urls.find((u) => u && String(u).trim())
  if (u0) return String(u0).trim()
  if (feed?.imageUrl) {
    const first = String(feed.imageUrl).split(',')[0]?.trim()
    if (first) return first
  }
  return ''
}

function parseNonNegativeInt(raw) {
  if (raw == null || raw === '') return null
  const n = Number(raw)
  if (!Number.isFinite(n) || n < 0) return null
  return Math.round(n)
}

export function GuideDetailPage() {
  const { guideId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, isGuide } = useAuth()
  const fromMatchedCourse = location.state?.fromMatchedCourse === true
  const hideMatchRequest = location.state?.hideMatchRequest === true
  const returnTo = typeof location.state?.returnTo === 'string' ? location.state.returnTo : ''

  const [aiConceptSummary, setAiConceptSummary] = useState(null)
  const [aiDesiredBudget, setAiDesiredBudget] = useState(null)
  const [aiBudgetMinWon, setAiBudgetMinWon] = useState(null)
  const [aiBudgetMaxWon, setAiBudgetMaxWon] = useState(null)
  const [aiHiddenConcept, setAiHiddenConcept] = useState(null)

  const [detail, setDetail] = useState(null)
  const [schedules, setSchedules] = useState([])
  const matchSectionRef = useRef(/** @type {HTMLElement | null} */ (null))
  /** 매칭 요청 섹션이 뷰포트에 들어오면 true — 상단 떠다니는 안내 문구 숨김 */
  const [matchSectionVisible, setMatchSectionVisible] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const [selectedScheduleId, setSelectedScheduleId] = useState(null)
  const [selectedDateKey, setSelectedDateKey] = useState(null)
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [destination, setDestination] = useState('')
  const [budgetMinWonInput, setBudgetMinWonInput] = useState('')
  const [budgetMaxWonInput, setBudgetMaxWonInput] = useState('')
  const [concept, setConcept] = useState('')
  const [submitErr, setSubmitErr] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [confirmModal, setConfirmModal] = useState(null)
  const [reviews, setReviews] = useState(/** @type {Array<Record<string, unknown>>} */ ([]))
  const [reviewsLoading, setReviewsLoading] = useState(true)

  useEffect(() => {
    // AI 검색 결과에서 넘어온 matchRequestDraft는 게스트에게 노출하지 않고, 전송 시에만 활용한다.
    try {
      const raw = sessionStorage.getItem('localguest_ai_match_draft_v1')
      if (!raw) return
      const snap = JSON.parse(raw)
      const snapGuideId = snap?.guideId != null ? String(snap.guideId) : ''
      if (!snapGuideId || snapGuideId !== String(guideId)) return
      const draft = snap?.matchRequestDraft ?? null
      if (!draft || typeof draft !== 'object') return

      const summaryFromDraft =
        (draft?.conceptSummary && String(draft.conceptSummary).trim()) ||
        (draft?.concept && String(draft.concept).trim()) ||
        ''
      if (summaryFromDraft) setAiConceptSummary(summaryFromDraft)
      if (draft?.desiredBudget != null && draft.desiredBudget !== '') setAiDesiredBudget(Number(draft.desiredBudget))
      if (draft?.budgetMinWon != null && draft.budgetMinWon !== '') setAiBudgetMinWon(Number(draft.budgetMinWon))
      if (draft?.budgetMaxWon != null && draft.budgetMaxWon !== '') setAiBudgetMaxWon(Number(draft.budgetMaxWon))
      if (draft?.budgetMinWon != null && draft.budgetMinWon !== '') setBudgetMinWonInput(String(Number(draft.budgetMinWon)))
      if (draft?.budgetMaxWon != null && draft.budgetMaxWon !== '') setBudgetMaxWonInput(String(Number(draft.budgetMaxWon)))
      if (draft?.concept) setAiHiddenConcept(String(draft.concept))

      // 목적지는 사용자가 비워둔 경우에만 보조로 채운다.
      if (!destination.trim() && draft?.destination) setDestination(String(draft.destination).trim())

      sessionStorage.removeItem('localguest_ai_match_draft_v1')
    } catch {
      /* ignore */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [guideId])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const res = await apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (!res.ok) {
          throw new Error(text || '조회 실패')
        }
        const data = text ? JSON.parse(text) : null
        if (cancelled) return
        setDetail(data)
        const p = data?.profile
        if (p?.region) setDestination(String(p.region).trim())

        const schRes = await apiRequest(`/guides/${guideId}/schedules`, { method: 'GET', skipAuth: true })

        const schText = await schRes.text()
        if (schRes.ok) {
          const parsed = schText ? JSON.parse(schText) : []
          const list = toScheduleList(parsed)
          const avail = list
            .map((s) => normalizeSchedule(s))
            .filter((s) => {
              const key = formatScheduleDate(s.availableDate)
              return key && !isPastDateKey(key)
            })
            .sort(compareScheduleTime)
          if (!cancelled) {
            setSchedules(avail)
            const today = ymd(new Date())
            setSelectedDateKey(today)
            setSelectedScheduleId(null)
            setCalendarMonth(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
          }
        } else if (!cancelled) {
          setSchedules([])
          setSelectedScheduleId(null)
          setSelectedDateKey(ymd(new Date()))
        }

      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [guideId])

  useEffect(() => {
    if (!guideId) return
    let cancelled = false
    setReviewsLoading(true)
    void (async () => {
      try {
        const res = await apiRequest(`/reviews/guide/${guideId}?size=30&sort=id,desc`, {
          method: 'GET',
          skipAuth: true,
        })
        const text = await res.text()
        if (cancelled) return
        if (!res.ok) {
          setReviews([])
          return
        }
        const page = text ? JSON.parse(text) : {}
        setReviews(extractReviewListFromPage(page))
      } catch {
        if (!cancelled) setReviews([])
      } finally {
        if (!cancelled) setReviewsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [guideId])

  useEffect(() => {
    const el = matchSectionRef.current
    if (!el || typeof IntersectionObserver === 'undefined') return
    const onIntersect = (entries) => {
      const e = entries[0]
      if (!e) return
      // 조금만 겹쳐도(모바일 주소창·레이아웃) 곧바로 사라지지 않게 비율 기준
      const ratio = e.intersectionRatio
      setMatchSectionVisible(ratio > 0.18)
    }
    const io = new IntersectionObserver(onIntersect, {
      root: null,
      rootMargin: '0px 0px 0px 0px',
      threshold: [0, 0.05, 0.1, 0.15, 0.2, 0.35, 0.5, 0.75, 1],
    })
    io.observe(el)
    return () => io.disconnect()
  }, [detail?.profile, hideMatchRequest, loading])

  /** 피드·공유 링크 `#match-request` — 달력 매칭 섹션으로 스크롤 */
  useEffect(() => {
    if (loading || hideMatchRequest) return
    const h = (location.hash || '').trim()
    if (h !== '#match-request') return
    const t = window.setTimeout(() => {
      matchSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 80)
    return () => clearTimeout(t)
  }, [loading, hideMatchRequest, location.hash, location.key, guideId])

  const tags = useMemo(() => parseGuideStyleTags(detail?.profile?.guideStyle), [detail])

  const ratingLine = useMemo(() => {
    const p = detail?.profile
    if (!p) return null
    const avg = p.averageRating
    const cnt = p.reviewCount != null ? Number(p.reviewCount) : 0
    if (avg == null || avg === '') {
      return cnt > 0 ? `리뷰 ${cnt}건` : '아직 리뷰가 없어요'
    }
    const stars = Number(avg)
    const label = Number.isFinite(stars) ? stars.toFixed(1) : String(avg)
    return (
      <>
        🌟 <strong>{label}</strong>
        {cnt > 0 ? <span> · 리뷰 {cnt}건</span> : null}
      </>
    )
  }, [detail])

  const blockedDateSet = useMemo(() => {
    const set = new Set()
    for (const schedule of schedules) {
      const key = formatScheduleDate(schedule?.availableDate)
      const status = String(schedule?.status ?? '').toUpperCase()
      if (key && status === 'BLOCKED') set.add(key)
    }
    return set
  }, [schedules])

  const reservedDateSet = useMemo(() => {
    const set = new Set()
    for (const schedule of schedules) {
      const key = formatScheduleDate(schedule?.availableDate)
      const status = String(schedule?.status ?? '').toUpperCase()
      if (key && (status === 'BOOKED' || status === 'PENDING')) set.add(key)
    }
    return set
  }, [schedules])

  const availableByDate = useMemo(() => {
    /** @type {Map<string, any[]>} */
    const map = new Map()
    for (const schedule of schedules) {
      const key = formatScheduleDate(schedule?.availableDate)
      if (!key || isPastDateKey(key)) continue
      if (!isBookableSchedule(schedule)) continue
      if (!map.has(key)) map.set(key, [])
      map.get(key).push(schedule)
    }
    for (const [key, list] of map.entries()) {
      list.sort(compareScheduleTime)
      map.set(key, list)
    }
    return map
  }, [schedules])

  const selectedDateSchedules = useMemo(() => {
    if (!selectedDateKey) return []
    return availableByDate.get(selectedDateKey) ?? []
  }, [availableByDate, selectedDateKey])

  useEffect(() => {
    if (!selectedDateKey) return
    if (selectedDateSchedules.length === 0) {
      if (selectedScheduleId != null) setSelectedScheduleId(null)
      return
    }
    const hasSelected = selectedDateSchedules.some((s) => Number(s.scheduleId) === Number(selectedScheduleId))
    if (!hasSelected) {
      setSelectedScheduleId(Number(selectedDateSchedules[0].scheduleId))
    }
  }, [selectedDateSchedules, selectedDateKey, selectedScheduleId])

  const calendarCells = useMemo(() => {
    const y = calendarMonth.getFullYear()
    const m = calendarMonth.getMonth()
    const first = new Date(y, m, 1).getDay()
    const daysInMonth = new Date(y, m + 1, 0).getDate()
    const prevDays = new Date(y, m, 0).getDate()
    const cells = []
    for (let i = 0; i < first; i += 1) {
      const day = prevDays - first + i + 1
      cells.push({ date: new Date(y, m - 1, day), inMonth: false, key: `p-${y}-${m}-${day}` })
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
      cells.push({ date: new Date(y, m, day), inMonth: true, key: `c-${y}-${m + 1}-${day}` })
    }
    while (cells.length < 42) {
      const day = cells.length - (first + daysInMonth) + 1
      cells.push({ date: new Date(y, m + 1, day), inMonth: false, key: `n-${y}-${m + 2}-${day}` })
    }
    return cells
  }, [calendarMonth])

  const sendMatchRequest = async (payload) => {
    setSubmitting(true)
    try {
      const res = await apiRequest('/matching/requests', {
        method: 'POST',
        json: payload,
      })
      const t = await res.text()
      if (res.status === 401 || res.status === 403) {
        setSubmitErr('권한이 없거나 로그인이 만료되었습니다. 다시 로그인해 주세요.')
        return
      }
      if (!res.ok) {
        let msg = t || '요청에 실패했습니다.'
        try {
          const j = JSON.parse(t)
          if (j?.message) msg = j.message
        } catch {
          /* ignore */
        }
        setSubmitErr(msg)
        return
      }
      const data = t ? JSON.parse(t) : {}
      const rid = data?.requestId
      navigate('/mypage/itinerary', {
        replace: false,
        state: {
          matchRequestSubmitted: true,
          requestId: rid != null ? Number(rid) : undefined,
        },
      })
    } catch {
      setSubmitErr('네트워크 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const onSubmitMatch = async (e) => {
    e.preventDefault()
    if (submitting) return
    setSubmitErr('')
    if (!isAuthenticated) {
      setSubmitErr('로그인이 필요합니다.')
      return
    }
    if (isGuide) {
      setSubmitErr('여행자(GUEST) 계정으로 로그인한 뒤 요청해 주세요.')
      return
    }
    if (!selectedDateKey) {
      setSubmitErr('예약 날짜를 선택해 주세요.')
      return
    }
    if (isPastDateKey(selectedDateKey)) {
      setSubmitErr('지난 날짜에는 요청할 수 없습니다.')
      return
    }
    if (blockedDateSet.has(selectedDateKey)) {
      setSubmitErr('해당 날짜는 가이드가 예약을 받지 않습니다. 다른 날짜를 선택해 주세요.')
      return
    }
    if (reservedDateSet.has(selectedDateKey)) {
      setSubmitErr('해당 날짜는 이미 예약이 잡혀 요청할 수 없습니다. 다른 날짜를 선택해 주세요.')
      return
    }
    const dest = destination.trim()
    if (!dest) {
      setSubmitErr('여행 목적지(지역)를 입력해 주세요.')
      return
    }

    const sch = selectedScheduleId == null
      ? null
      : schedules.find((s) => Number(s.scheduleId) === Number(selectedScheduleId))
    const desiredDate = selectedDateKey || (sch?.availableDate != null ? formatScheduleDate(sch.availableDate) : undefined)
    const dnaPreview = buildTravelDnaPreview(loadTravelDna())
    const conceptText = concept.trim() || (aiHiddenConcept ? String(aiHiddenConcept).trim() : '') || dnaPreview
    const manualMin = parseNonNegativeInt(budgetMinWonInput)
    const manualMax = parseNonNegativeInt(budgetMaxWonInput)
    let budgetMinWon = manualMin
    let budgetMaxWon = manualMax
    if (budgetMinWon == null && budgetMaxWon == null) {
      budgetMinWon = parseNonNegativeInt(aiBudgetMinWon)
      budgetMaxWon = parseNonNegativeInt(aiBudgetMaxWon)
    }
    if (budgetMinWon != null && budgetMaxWon == null) budgetMaxWon = budgetMinWon
    if (budgetMaxWon != null && budgetMinWon == null) budgetMinWon = budgetMaxWon
    if (budgetMinWon != null && budgetMaxWon != null && budgetMinWon > budgetMaxWon) {
      setSubmitErr('예산 범위는 최소 금액이 최대 금액보다 클 수 없습니다.')
      return
    }
    let desiredBudgetValue =
      aiDesiredBudget != null && !Number.isNaN(Number(aiDesiredBudget)) ? Number(aiDesiredBudget) : undefined
    if (budgetMinWon != null && budgetMaxWon != null) {
      desiredBudgetValue = Math.round((budgetMinWon + budgetMaxWon) / 2)
    }

    setConfirmModal({
      destination: dest,
      desiredDate: desiredDate || '',
      guestConcept: concept.trim(),
      payload: {
        guideId: Number(guideId),
        scheduleId: selectedScheduleId != null ? Number(selectedScheduleId) : undefined,
        destination: dest,
        concept: conceptText || undefined,
        conceptSummary: (aiConceptSummary ? String(aiConceptSummary).trim() : '') || dnaPreview || undefined,
        desiredBudget: desiredBudgetValue,
        budgetMinWon: budgetMinWon ?? undefined,
        budgetMaxWon: budgetMaxWon ?? undefined,
        desiredDate: desiredDate || undefined,
      },
    })
  }

  if (loading) {
    return (
      <div className="gdp" style={{ maxWidth: 720, margin: '0 auto', padding: '1rem' }}>
        <PageLoading />
      </div>
    )
  }
  if (error) {
    return (
      <div className="gdp" style={{ maxWidth: 720, margin: '0 auto', padding: '1rem' }}>
        <PageError message={error} />
      </div>
    )
  }
  if (!detail?.profile) {
    return (
      <div className="gdp" style={{ maxWidth: 720, margin: '0 auto', padding: '1rem' }}>
        <PageEmpty title="표시할 프로필이 없습니다">가이드 ID를 확인하거나 목록으로 돌아가 주세요.</PageEmpty>
      </div>
    )
  }

  const p = detail.profile
  const careers = Array.isArray(detail.careers) ? detail.careers : []
  const feeds = Array.isArray(detail.feeds) ? detail.feeds : []
  const avatarStyle = p.profileImage ? { backgroundImage: `url(${p.profileImage})` } : undefined

  const storyBlocks = [
    p.residenceYears != null && Number(p.residenceYears) > 0
      ? { label: '거주', text: `이 지역에 약 ${p.residenceYears}년 살았어요.` }
      : null,
    p.localStory && String(p.localStory).trim()
      ? { label: '지역 이야기', text: String(p.localStory).trim() }
      : null,
    p.guideStyle && String(p.guideStyle).trim()
      ? { label: '가이드 스타일', text: String(p.guideStyle).trim() }
      : null,
    p.defaultCourse && String(p.defaultCourse).trim()
      ? { label: '추천 코스', text: String(p.defaultCourse).trim() }
      : null,
  ].filter(Boolean)

  return (
    <div className="gdp">
      <p className="gdp-back">
        {fromMatchedCourse ? (
          <button
            type="button"
            className="gdp-back-btn"
            onClick={() => {
              if (window.history.length > 1) {
                navigate(-1)
                return
              }
              if (returnTo) {
                navigate(returnTo)
                return
              }
              navigate('/guides')
            }}
          >
            ← 상세 코스로 돌아가기
          </button>
        ) : (
          <Link to="/guides">← 목록</Link>
        )}
      </p>

      <header className="gdp-hero">
        <div className="gdp-hero-avatar" style={avatarStyle} role="img" aria-label={p.nickname ?? '가이드'} />
        <div className="gdp-hero-body">
          <h1>{p.nickname}</h1>
          <p className="gdp-hero-meta">
            {p.region} · {p.language}
            {p.pricePerHour != null ? ` · ${Number(p.pricePerHour).toLocaleString('ko-KR')}원 / 시간` : ''}
          </p>
          {ratingLine && <p className="gdp-hero-rating">{ratingLine}</p>}
          {tags.length > 0 && (
            <div className="gdp-tags">
              {tags.map((t) => (
                <span key={t} className="gdp-tag">
                  {t}
                </span>
              ))}
            </div>
          )}
        </div>
      </header>

      {(p.bio && String(p.bio).trim()) || storyBlocks.length > 0 ? (
        <section className="gdp-section" aria-labelledby="gdp-intro">
          <h2 id="gdp-intro" className="gdp-section-title">
            소개
          </h2>
          <div className="gdp-intro-board">
            <span className="gdp-intro-clip" aria-hidden>
              <svg viewBox="0 0 24 24" width="22" height="22" focusable="false" aria-hidden>
                <path
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M8.2 5.2v12.6a3.8 3.8 0 1 0 7.6 0V6.4a2.2 2.2 0 1 0-4.4 0v11.2"
                />
              </svg>
            </span>
            {p.bio && String(p.bio).trim() ? (
              <p className="gdp-prose gdp-intro-bio">{String(p.bio).trim()}</p>
            ) : (
              <p className="gdp-muted gdp-intro-bio">등록된 한 줄 소개가 없어요. 피드와 후기를 참고해 주세요.</p>
            )}
            {storyBlocks.length > 0 && (
              <dl className="gdp-intro-dl" style={{ marginTop: '1rem' }}>
                {storyBlocks.map((row) => (
                  <div key={row.label}>
                    <dt>{row.label}</dt>
                    <dd>{row.text}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
        </section>
      ) : null}

      {careers.length > 0 ? (
        <section className="gdp-section" aria-labelledby="gdp-career">
          <h2 id="gdp-career" className="gdp-section-title">
            경력 · 자격
          </h2>
          <ul className="gdp-careers">
            {careers.map((c) => (
              <li key={c.careerId ?? c.title} className="gdp-career">
                <div className="gdp-career-body">
                  <div className="gdp-career-title">{c.title ?? '경력'}</div>
                  {c.acquiredAt && <div className="gdp-career-date">{formatCareerDate(c.acquiredAt)}</div>}
                  {c.description && String(c.description).trim() ? (
                    <p className="gdp-career-desc">{String(c.description).trim()}</p>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {feeds.length > 0 ? (
        <section className="gdp-section" aria-labelledby="gdp-feeds">
          <h2 id="gdp-feeds" className="gdp-section-title">
            피드 · 코스
          </h2>
          <div className="gdp-feeds">
            {feeds.map((f) => {
              const href = `/guides/${guideId}/feeds/${f.feedId}`
              const bg = feedPrimaryImage(f)
              return (
                <Link key={f.feedId} to={href} className="gdp-feed-card">
                  <div
                    className="gdp-feed-card-img"
                    style={bg ? { backgroundImage: `url(${bg})` } : undefined}
                  />
                  <div className="gdp-feed-card-body">
                    <h3 className="gdp-feed-card-title">{feedCardTitle(f.content)}</h3>
                    <p className="gdp-feed-card-snippet">{feedSnippet(f.content)}</p>
                  </div>
                </Link>
              )
            })}
          </div>
        </section>
      ) : (
        <section className="gdp-section" aria-labelledby="gdp-feeds-empty">
          <h2 id="gdp-feeds-empty" className="gdp-section-title">
            피드 · 코스
          </h2>
          <p className="gdp-muted">아직 등록된 피드가 없어요.</p>
        </section>
      )}

      <section className="gdp-section" aria-labelledby="gdp-reviews">
        <h2 id="gdp-reviews" className="gdp-section-title">
          가이드 후기
        </h2>
        {reviewsLoading ? (
          <p className="gdp-muted">후기를 불러오는 중…</p>
        ) : reviews.length === 0 ? (
          <p className="gdp-muted">아직 등록된 후기가 없어요. 투어를 완료한 뒤 스크랩북에서 리뷰를 남길 수 있어요.</p>
        ) : (
          <ReviewCarousel reviews={reviews} variant="default" className="gdp-review-carousel" />
        )}
      </section>

      {!hideMatchRequest &&
        typeof document !== 'undefined' &&
        createPortal(
          <div
            className={`gdp-float-hint${matchSectionVisible ? ' gdp-float-hint--hide' : ''}`}
            role="status"
          >
            ⏱️ 전체 패키지는 부담, 하루만 완벽하게!
          </div>,
          document.body,
        )}

      {!hideMatchRequest && (
        <section className="gdp-match" id="match-request" ref={matchSectionRef}>
          <h2>매칭 요청하기</h2>
          <p className="gdp-match-intro">
            날짜를 먼저 선택한 뒤 여행 지역과 원하는 여행 스타일을 적어 주세요. 입력한 정보가 가이드에게 그대로 전달됩니다.
          </p>

          {!isAuthenticated && (
            <p className="gdp-match-login">
              <Link
                to="/auth/login"
                state={{
                  returnTo: `/guides/${guideId}#match-request`,
                  hint: '로그인 후 이 가이드에게 매칭 요청을 보낼 수 있어요.',
                }}
              >
                로그인하고 요청하기
              </Link>
            </p>
          )}

          {isAuthenticated && isGuide && (
            <p className="gdp-match-guide-warn">가이드 계정에서는 요청을 보낼 수 없어요. 여행자 계정으로 로그인해 주세요.</p>
          )}

          {isAuthenticated && !isGuide && (
            <form onSubmit={(e) => void onSubmitMatch(e)} className="gdp-match-form gdp-match-form--wide">
            <label className="gdp-match-field">
              <span className="gdp-match-label">📅 예약 날짜</span>
              <div className="gdp-match-cal gdp-match-cal--gss3">
                <div className="gdp-match-cal-head">
                  <button
                    type="button"
                    className="gdp-match-nav"
                    onClick={() => setCalendarMonth((p) => new Date(p.getFullYear(), p.getMonth() - 1, 1))}
                    aria-label="이전 달"
                  >
                    ‹
                  </button>
                  <strong>
                    {calendarMonth.getFullYear()}년 {calendarMonth.getMonth() + 1}월
                  </strong>
                  <button
                    type="button"
                    className="gdp-match-nav"
                    onClick={() => setCalendarMonth((p) => new Date(p.getFullYear(), p.getMonth() + 1, 1))}
                    aria-label="다음 달"
                  >
                    ›
                  </button>
                </div>
                <div className="gdp-match-week">
                  {WEEKDAYS_SHORT.map((w) => (
                    <span key={w}>{w}</span>
                  ))}
                </div>
                <div className="gdp-match-days">
                  {calendarCells.map((cell) => {
                    const key = ymd(cell.date)
                    const inMonth = cell.inMonth
                    const isPast = isPastDateKey(key)
                    const isBlocked = blockedDateSet.has(key)
                    const isReserved = reservedDateSet.has(key)
                    const hasSchedule = availableByDate.has(key)
                    const selectable = inMonth && !isPast && !isBlocked && !isReserved
                    const selected = selectable && key === selectedDateKey
                    return (
                      <button
                        key={cell.key}
                        type="button"
                        className={`gdp-match-day${!inMonth ? ' is-out' : ''}${isPast ? ' is-past' : ''}${selectable ? ' is-on' : ''}${isBlocked ? ' is-blocked' : ''}${isReserved ? ' is-reserved' : ''}${selected ? ' is-selected' : ''}`}
                        disabled={!selectable}
                        onClick={() => {
                          setSelectedDateKey(key)
                          const picks = availableByDate.get(key) ?? []
                          if (picks.length > 0) setSelectedScheduleId(Number(picks[0].scheduleId))
                          else setSelectedScheduleId(null)
                        }}
                      >
                        {cell.date.getDate()}
                        {isBlocked && <span className="gdp-match-x">×</span>}
                        {isReserved && <span className="gdp-match-x">×</span>}
                      </button>
                    )
                  })}
                </div>
                <p className="gdp-match-cal-hint">
                  지난 날짜와 이미 예약된 날짜는 선택할 수 없습니다. 가이드가 비활성화한 날짜도 자동으로 제외돼요.
                </p>

                {selectedDateSchedules.length > 0 ? (
                  <div className="gdp-match-times" role="radiogroup" aria-label="시간 선택">
                    {selectedDateSchedules.map((s) => {
                      const sid = Number(s.scheduleId)
                      const on = sid === Number(selectedScheduleId)
                      return (
                        <button
                          key={sid}
                          type="button"
                          className={`gdp-match-time${on ? ' is-selected' : ''}`}
                          onClick={() => setSelectedScheduleId(sid)}
                        >
                          {formatLocalTime(s.startTime)} ~ {formatLocalTime(s.endTime)}
                        </button>
                      )
                    })}
                  </div>
                ) : (
                  <p className="gdp-match-default-slot">기본 예약 모드로 요청됩니다. (가이드가 이 날짜를 차단하지 않았다면 요청 가능)</p>
                )}
              </div>
            </label>
            <label className="gdp-match-field">
              <span className="gdp-match-label">
                🗺️ 여행 지역(목적지){' '}
                <span className="gdp-req" title="필수">
                  *
                </span>
              </span>
              <input
                className="gdp-match-input"
                type="text"
                value={destination}
                onChange={(ev) => setDestination(ev.target.value)}
                placeholder="예: 구미 / 경북 구미시 / 제주 제주시"
                required
              />
            </label>
            <label className="gdp-match-field">
              <span className="gdp-match-label">💰 예산 범위</span>
              <div className="gdp-match-budget-row">
                <input
                  className="gdp-match-input"
                  type="number"
                  min="0"
                  inputMode="numeric"
                  value={budgetMinWonInput}
                  onChange={(ev) => setBudgetMinWonInput(ev.target.value)}
                  placeholder="최소 예산(원)"
                />
                <span className="gdp-match-budget-sep">~</span>
                <input
                  className="gdp-match-input"
                  type="number"
                  min="0"
                  inputMode="numeric"
                  value={budgetMaxWonInput}
                  onChange={(ev) => setBudgetMaxWonInput(ev.target.value)}
                  placeholder="최대 예산(원)"
                />
              </div>
              <p className="gdp-match-budget-hint">한쪽만 입력하면 단일 금액으로 처리됩니다.</p>
            </label>
            <label className="gdp-match-field">
              <span className="gdp-match-label">🎨 원하는 여행 스타일</span>
              <textarea
                className="gdp-match-textarea"
                rows={3}
                value={concept}
                onChange={(ev) => setConcept(ev.target.value)}
                placeholder="예: 현지 맛집 + 산책 위주로 여유 있게, 걷는 코스는 짧게 원해요."
              />
            </label>
            {submitErr && <p className="gdp-match-error">{submitErr}</p>}
            <div className="gdp-match-actions">
              <button
                type="submit"
                className="gdp-match-submit"
                disabled={submitting || !selectedDateKey || blockedDateSet.has(selectedDateKey) || reservedDateSet.has(selectedDateKey)}
              >
                {submitting ? '요청 전송 중…' : '매칭 요청 보내기 ✈️'}
              </button>
            </div>
            </form>
          )}
        </section>
      )}

      {confirmModal &&
        typeof document !== 'undefined' &&
        createPortal(
          <div
            className="gdp-confirm-overlay"
            role="dialog"
            aria-modal="true"
            aria-label="매칭 요청 확인"
            onClick={(e) => {
              if (e.target === e.currentTarget && !submitting) setConfirmModal(null)
            }}
          >
            <div className="gdp-confirm">
              <p className="gdp-confirm-kicker">Final Check</p>
              <h3>{p.nickname} 가이드에게 매칭 요청을 보낼까요?</h3>
              <p className="gdp-confirm-sub">아래 정보가 그대로 전달됩니다. 맞으면 전송해 주세요.</p>
              <dl className="gdp-confirm-list">
                <div>
                  <dt>목적지</dt>
                  <dd>{confirmModal.destination}</dd>
                </div>
                <div>
                  <dt>날짜</dt>
                  <dd>{confirmModal.desiredDate || '미정'}</dd>
                </div>
                <div>
                  <dt>하고 싶은 일</dt>
                  <dd>{confirmModal.guestConcept}</dd>
                </div>
                {confirmModal.payload?.budgetMinWon != null && confirmModal.payload?.budgetMaxWon != null ? (
                  <div>
                    <dt>예산</dt>
                    <dd>
                      {Number(confirmModal.payload.budgetMinWon).toLocaleString('ko-KR')}~
                      {Number(confirmModal.payload.budgetMaxWon).toLocaleString('ko-KR')}원
                    </dd>
                  </div>
                ) : confirmModal.payload?.desiredBudget != null && (
                  <div>
                    <dt>예산</dt>
                    <dd>{Number(confirmModal.payload.desiredBudget).toLocaleString('ko-KR')}원</dd>
                  </div>
                )}
              </dl>
              <div className="gdp-confirm-actions">
                <button
                  type="button"
                  className="gdp-confirm-btn gdp-confirm-btn--line"
                  onClick={() => setConfirmModal(null)}
                  disabled={submitting}
                >
                  수정할게요
                </button>
                <button
                  type="button"
                  className="gdp-confirm-btn gdp-confirm-btn--solid"
                  disabled={submitting}
                  onClick={() => {
                    const payload = confirmModal.payload
                    setConfirmModal(null)
                    void sendMatchRequest(payload)
                  }}
                >
                  {submitting ? '전송 중…' : '요청 전송'}
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </div>
  )
}
