import { useEffect, useMemo, useState } from 'react'
import { Link, useParams, useLocation, useNavigate } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { useAuth } from '../context/useAuth.js'

import './GuideDetailPage.css'

/**
 * @param {unknown} raw
 * @returns {string}
 */
function formatScheduleDate(raw) {
  if (raw == null) return ''
  const s = String(raw)
  return s.length >= 10 ? s.slice(0, 10) : s
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

/**
 * @param {unknown} keywords
 * @returns {string[]}
 */
function parseProfileTags(keywords) {
  if (keywords == null || keywords === '') return []
  return String(keywords)
    .split(/[,#\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 10)
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

export function GuideDetailPage() {
  const { guideId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, isGuide } = useAuth()

  const [detail, setDetail] = useState(null)
  const [schedules, setSchedules] = useState([])
  const [reviews, setReviews] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const [selectedScheduleId, setSelectedScheduleId] = useState(null)
  const [destination, setDestination] = useState('')
  const [concept, setConcept] = useState('')
  const [submitErr, setSubmitErr] = useState('')
  const [submitting, setSubmitting] = useState(false)

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

        const [schRes, revRes] = await Promise.all([
          apiRequest(`/guides/${guideId}/schedules`, { method: 'GET', skipAuth: true }),
          apiRequest(`/reviews/guide/${guideId}?size=12&sort=createdAt,desc`, { method: 'GET', skipAuth: true }),
        ])

        const schText = await schRes.text()
        if (schRes.ok) {
          const parsed = schText ? JSON.parse(schText) : []
          const list = toScheduleList(parsed)
          const avail = list.filter((s) => isBookableSchedule(s)).map((s) => normalizeSchedule(s))
          if (!cancelled) {
            setSchedules(avail)
            if (avail.length > 0 && avail[0]?.scheduleId != null) {
              setSelectedScheduleId(Number(avail[0].scheduleId))
            }
          }
        } else if (!cancelled) {
          setSchedules([])
        }

        let revList = []
        if (revRes.ok) {
          try {
            const revText = await revRes.text()
            const revJson = revText ? JSON.parse(revText) : {}
            revList = Array.isArray(revJson.content) ? revJson.content : []
          } catch {
            revList = []
          }
        }
        if (!cancelled) setReviews(revList)
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
    if (!loading && detail?.profile && location.hash === '#match-request') {
      requestAnimationFrame(() => {
        document.getElementById('match-request')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    }
  }, [loading, detail, location.hash])

  const tags = useMemo(() => parseProfileTags(detail?.profile?.keywords), [detail])

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
        ⭐ <strong>{label}</strong>
        {cnt > 0 ? <span> · 리뷰 {cnt}건</span> : null}
      </>
    )
  }, [detail])

  const onSubmitMatch = async (e) => {
    e.preventDefault()
    setSubmitErr('')
    if (!isAuthenticated) {
      setSubmitErr('로그인이 필요합니다.')
      return
    }
    if (isGuide) {
      setSubmitErr('여행자(GUEST) 계정으로 로그인한 뒤 요청해 주세요.')
      return
    }
    if (selectedScheduleId == null || Number.isNaN(Number(selectedScheduleId))) {
      setSubmitErr('예약 가능한 일정을 선택해 주세요. (가이드가 일정을 등록해야 합니다.)')
      return
    }
    const dest = destination.trim()
    if (!dest) {
      setSubmitErr('여행 목적지(지역)를 입력해 주세요.')
      return
    }

    const sch = schedules.find((s) => Number(s.scheduleId) === Number(selectedScheduleId))
    const desiredDate = sch?.availableDate != null ? formatScheduleDate(sch.availableDate) : undefined

    setSubmitting(true)
    try {
      const res = await apiRequest('/matching/requests', {
        method: 'POST',
        json: {
          guideId: Number(guideId),
          scheduleId: Number(selectedScheduleId),
          destination: dest,
          concept: concept.trim() || undefined,
          desiredDate: desiredDate || undefined,
        },
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
      navigate(`/guides/${guideId}/match`, {
        replace: false,
        state: { requestId: rid != null ? Number(rid) : undefined },
      })
    } catch {
      setSubmitErr('네트워크 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
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
  const images = Array.isArray(detail.images) ? detail.images : []
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
        <Link to="/guides">← 목록</Link>
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
          {p.bio && String(p.bio).trim() ? (
            <p className="gdp-prose">{String(p.bio).trim()}</p>
          ) : (
            <p className="gdp-muted">등록된 한 줄 소개가 없어요. 피드와 후기를 참고해 주세요.</p>
          )}
          {storyBlocks.length > 0 && (
            <dl className="gdp-dl" style={{ marginTop: '1rem' }}>
              {storyBlocks.map((row) => (
                <div key={row.label}>
                  <dt>{row.label}</dt>
                  <dd>{row.text}</dd>
                </div>
              ))}
            </dl>
          )}
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
                <div className="gdp-career-title">{c.title ?? '경력'}</div>
                {c.acquiredAt && <div className="gdp-career-date">{formatCareerDate(c.acquiredAt)}</div>}
                {c.description && String(c.description).trim() ? (
                  <p className="gdp-career-desc">{String(c.description).trim()}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {images.length > 0 ? (
        <section className="gdp-section" aria-labelledby="gdp-photos">
          <h2 id="gdp-photos" className="gdp-section-title">
            사진
          </h2>
          <div className="gdp-gallery">
            {images.map((img) => (
              <div
                key={img.imageId ?? img.imageUrl}
                className="gdp-gallery-item"
                style={img.imageUrl ? { backgroundImage: `url(${img.imageUrl})` } : undefined}
                role="img"
                aria-label=""
              />
            ))}
          </div>
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
          후기
        </h2>
        {reviews.length === 0 ? (
          <p className="gdp-muted">아직 표시할 후기가 없어요.</p>
        ) : (
          <ul className="gdp-reviews">
            {reviews.map((r) => (
              <li key={r.id} className="gdp-review">
                <div className="gdp-review-head">
                  <span className="gdp-review-name">{r.writeNickname ?? '여행자'}</span>
                  <span className="gdp-review-stars">
                    {'⭐'.repeat(Math.min(5, Math.max(0, Number(r.rating) || 0)))}
                  </span>
                </div>
                {r.content && String(r.content).trim() ? (
                  <p className="gdp-review-text">{String(r.content).trim()}</p>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="gdp-match" id="match-request">
        <h2>매칭 요청하기</h2>
        <p style={{ color: '#4b5563', fontSize: '0.95rem', lineHeight: 1.5 }}>
          예약 가능한 일정을 고르고 목적지를 적으면 가이드에게 매칭 요청이 전달됩니다. (가이드가 일정을 아직 등록하지 않았다면
          요청할 수 없어요.)
        </p>

        {!isAuthenticated && (
          <p>
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
          <p style={{ color: '#b45309' }}>가이드 계정으로는 게스트 매칭 요청을 보낼 수 없습니다. 여행자로 로그인해 주세요.</p>
        )}

        {isAuthenticated && !isGuide && (
          <form onSubmit={(e) => void onSubmitMatch(e)} style={{ display: 'grid', gap: '0.85rem', maxWidth: 480 }}>
            <label style={{ display: 'grid', gap: 6 }}>
              <span>예약 가능 일정</span>
              {schedules.length === 0 ? (
                <span style={{ color: '#6b7280' }}>등록된 예약 가능 일정이 없습니다. 가이드에게 일정 등록을 요청해 주세요.</span>
              ) : (
                <select
                  value={selectedScheduleId ?? ''}
                  onChange={(ev) => setSelectedScheduleId(ev.target.value ? Number(ev.target.value) : null)}
                  required
                >
                  {schedules.map((s) => (
                    <option key={s.scheduleId} value={s.scheduleId}>
                      {formatScheduleDate(s.availableDate)} {formatLocalTime(s.startTime)} ~ {formatLocalTime(s.endTime)}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label style={{ display: 'grid', gap: 6 }}>
              <span>여행 목적지 · 지역</span>
              <input
                type="text"
                value={destination}
                onChange={(ev) => setDestination(ev.target.value)}
                placeholder="예: 구미, 경북 구미시"
                required
              />
            </label>
            <label style={{ display: 'grid', gap: 6 }}>
              <span>하고 싶은 일 (선택)</span>
              <textarea
                rows={3}
                value={concept}
                onChange={(ev) => setConcept(ev.target.value)}
                placeholder="예: 동네 맛집과 산책 코스를 함께하고 싶어요."
              />
            </label>
            {submitErr && <p style={{ color: '#b91c1c', margin: 0 }}>{submitErr}</p>}
            <div>
              <button
                type="submit"
                disabled={submitting || schedules.length === 0}
                style={{
                  padding: '0.6rem 1.2rem',
                  borderRadius: 8,
                  border: 'none',
                  background: '#111',
                  color: '#fff',
                  cursor: schedules.length === 0 ? 'not-allowed' : 'pointer',
                }}
              >
                {submitting ? '전송 중…' : '매칭 요청 보내기'}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}
