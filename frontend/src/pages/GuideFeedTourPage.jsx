import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { useAuth } from '../context/useAuth.js'

import './GuideFeedTourPage.css'

function parseFeedHeading(content) {
  if (!content || !String(content).trim()) {
    return { title: '가이드 투어 피드', body: '' }
  }
  const lines = String(content).split(/\r?\n/)
  const title = lines[0]?.trim() || '가이드 투어 피드'
  const body = lines.slice(1).join('\n').trim()
  return { title, body: body || String(content).trim() }
}

function parseProfileTags(keywords) {
  if (keywords == null || keywords === '') return []
  return String(keywords)
    .split(/[,#\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 3)
    .map((t) => (t.startsWith('#') ? t : `#${t}`))
}

async function fetchJson(path, { skipAuth = true } = {}) {
  const res = await apiRequest(path, { method: 'GET', skipAuth })
  const text = await res.text()
  if (!res.ok) {
    throw new Error(text || '요청 실패')
  }
  return text ? JSON.parse(text) : null
}

/** Spring `Page<>` 및 변형 응답에서 리뷰 배열만 추출 */
function extractPageContent(raw) {
  if (raw == null) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw !== 'object') return []
  if (Array.isArray(raw.content)) return raw.content
  const d = raw.data
  if (d && typeof d === 'object' && Array.isArray(d.content)) return d.content
  return []
}

function formatReviewWhen(iso) {
  if (iso == null || iso === '') return ''
  try {
    return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

export function GuideFeedTourPage() {
  const { guideId, feedId } = useParams()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [detail, setDetail] = useState(null)
  const [reviews, setReviews] = useState([])
  const [reviewErr, setReviewErr] = useState('')
  const [reviewReloadKey, setReviewReloadKey] = useState(0)
  const [reviewsLoading, setReviewsLoading] = useState(true)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const reviewTrackRef = useRef(/** @type {HTMLDivElement | null} */ (null))
  const [reviewSlide, setReviewSlide] = useState(0)

  const scrollReviewBy = useCallback((dir) => {
    const el = reviewTrackRef.current
    if (!el) return
    const w = el.clientWidth
    if (w <= 0) return
    el.scrollBy({ left: dir * w, behavior: 'smooth' })
  }, [])

  useEffect(() => {
    const el = reviewTrackRef.current
    if (!el || reviews.length === 0) return
    const onScroll = () => {
      const w = el.clientWidth
      if (w <= 0) return
      setReviewSlide(Math.min(reviews.length - 1, Math.max(0, Math.round(el.scrollLeft / w))))
    }
    el.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
    return () => el.removeEventListener('scroll', onScroll)
  }, [reviews.length])

  useEffect(() => {
    setReviewSlide(0)
    const el = reviewTrackRef.current
    if (el) {
      requestAnimationFrame(() => {
        el.scrollTo({ left: 0, behavior: 'auto' })
      })
    }
  }, [guideId, reviewReloadKey])

  /** 후기 목록이 DOM에 그려진 직후 첫 슬라이드로 정렬 */
  useLayoutEffect(() => {
    if (reviewsLoading || reviews.length === 0) return
    const el = reviewTrackRef.current
    if (!el) return
    el.scrollTo({ left: 0, behavior: 'auto' })
    setReviewSlide(0)
  }, [reviewsLoading, reviews.length, guideId, reviewReloadKey])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const d = await fetchJson(`/guides/${guideId}/detail`, { skipAuth: true })
        if (!cancelled) setDetail(d)
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
    setReviewErr('')
    void (async () => {
      try {
        const res = await apiRequest(`/reviews/guide/${guideId}?size=50`, { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (cancelled) return
        if (!res.ok) {
          setReviews([])
          setReviewErr('후기 목록을 불러오지 못했어요. 네트워크를 확인한 뒤 다시 시도해 주세요.')
          return
        }
        let json = null
        try {
          json = text && text.trim() ? JSON.parse(text) : null
        } catch {
          setReviews([])
          setReviewErr('후기 응답을 해석할 수 없어요.')
          return
        }
        setReviews(extractPageContent(json))
      } catch {
        if (!cancelled) {
          setReviews([])
          setReviewErr('후기를 불러오지 못했어요.')
        }
      } finally {
        if (!cancelled) setReviewsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [guideId, reviewReloadKey])

  const feed = useMemo(() => {
    const feeds = detail?.feeds
    if (!Array.isArray(feeds)) return null
    const fid = Number(feedId)
    return feeds.find((f) => Number(f.feedId) === fid) ?? null
  }, [detail, feedId])

  const profile = detail?.profile

  const otherFeeds = useMemo(() => {
    const feeds = detail?.feeds
    if (!Array.isArray(feeds)) return []
    const fid = Number(feedId)
    return feeds.filter((f) => Number(f.feedId) !== fid).slice(0, 2)
  }, [detail, feedId])

  const { title: tourTitle, body: tourBody } = useMemo(() => parseFeedHeading(feed?.content), [feed])

  const tags = useMemo(() => {
    const fromKw = parseProfileTags(profile?.keywords)
    if (fromKw.length > 0) return fromKw
    return ['#로컬투어', '#맞춤동행', '#동행']
  }, [profile])

  const rating =
    profile?.averageRating != null && profile?.averageRating !== ''
      ? Number(profile.averageRating).toFixed(1)
      : '—'
  const rc = profile?.reviewCount != null ? profile.reviewCount : 0

  const goBack = () => {
    if (window.history.length > 1) navigate(-1)
    else navigate('/ai-search')
  }

  if (loading) {
    return (
      <div className="gft">
        <PageLoading />
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="gft">
        <button type="button" className="gft-back" onClick={() => goBack()}>
          ← 뒤로 가기
        </button>
        <PageError message={error || '가이드를 찾을 수 없습니다.'}>
          <Link to="/guides">가이드 목록</Link>
        </PageError>
      </div>
    )
  }

  if (!feed) {
    return (
      <div className="gft">
        <button type="button" className="gft-back" onClick={() => goBack()}>
          ← 뒤로 가기
        </button>
        <PageEmpty title="이 피드를 찾을 수 없습니다">
          삭제되었거나 주소가 바뀌었을 수 있어요.{' '}
          <Link to={`/guides/${guideId}`}>가이드 프로필</Link>로 이동
        </PageEmpty>
      </div>
    )
  }

  const chatTo = isAuthenticated ? '/messages' : '/auth/login'
  const chatState = isAuthenticated ? undefined : { returnTo: `/guides/${guideId}/feeds/${feedId}`, hint: '채팅은 로그인 후 이용할 수 있어요.' }

  return (
    <div className="gft gft--journal">
      <button type="button" className="gft-back" onClick={() => goBack()}>
        ← 뒤로 가기
      </button>

      <div className="gft-grid">
        <aside className="gft-polaroids" aria-label="가이드 피드 이미지">
          <div className="gft-polaroid gft-polaroid--hero">
            <span className="gft-tape gft-tape--p" aria-hidden />
            <div
              className="gft-polaroid-img"
              style={feed.imageUrl ? { backgroundImage: `url(${feed.imageUrl})` } : undefined}
            />
            <div className="gft-polaroid-cap">
              <strong>{profile.nickname ?? '가이드'}</strong>
              <span>{profile.region ?? ''}</span>
            </div>
          </div>
          {otherFeeds.map((f, i) => (
            <Link
              key={f.feedId}
              to={`/guides/${guideId}/feeds/${f.feedId}`}
              className={`gft-polaroid gft-polaroid--sm ${i === 0 ? 'gft-polaroid--tilt-l' : 'gft-polaroid--tilt-r'}`}
            >
              <span className={`gft-tape ${i === 0 ? 'gft-tape--g' : 'gft-tape--b'}`} aria-hidden />
              <div
                className="gft-polaroid-img"
                style={f.imageUrl ? { backgroundImage: `url(${f.imageUrl})` } : undefined}
              />
            </Link>
          ))}
        </aside>

        <article className="gft-card gft-card--journal">
          <h1 className="gft-title">{tourTitle}</h1>
          <p className="gft-rating">
            🌟 {rating}{' '}
            <span className="gft-rc">({rc}개의 리뷰)</span>
          </p>
          <div className="gft-tags">
            {tags.map((t) => (
              <span key={t} className="gft-tag">
                {t}
              </span>
            ))}
          </div>

          <section className="gft-section" aria-labelledby="gft-intro">
            <h2 id="gft-intro" className="gft-section-title">
              가이드 소개 🥬
            </h2>
            <p className="gft-bio">{tourBody || profile.bio || '가이드가 소개글을 준비 중이에요.'}</p>
            <div className="gft-summary">
              <p>
                <strong>투어 시간</strong> 약 4시간 (반일 투어 예시 — 실제 일정은 가이드와 조율)
              </p>
              <p>
                <strong>이동</strong> 가이드 차량 또는 대중교통 (가이드와 협의)
              </p>
              <p>
                <strong>포함</strong> 피드에 안내된 코스 · 현지 안내
                {profile.pricePerHour != null ? ` · 시간당 ${profile.pricePerHour}원 참고` : ''}
              </p>
            </div>
          </section>

          <section className="gft-section" aria-labelledby="gft-rev">
            <h2 id="gft-rev" className="gft-section-title gft-section-title--tape">
              생생한 후기 💌
            </h2>
            {reviewsLoading ? (
              <p className="gft-muted">후기를 불러오는 중…</p>
            ) : reviewErr ? (
              <p className="gft-err" role="alert">
                {reviewErr}{' '}
                <button type="button" className="gft-retry" onClick={() => setReviewReloadKey((k) => k + 1)}>
                  다시 시도
                </button>
              </p>
            ) : reviews.length === 0 && rc > 0 ? (
              <p className="gft-muted">
                집계상 <strong>{rc}건</strong>의 리뷰가 있으나, 목록이 비어 있어요. 서버·데이터를 확인하거나 잠시 후 다시 열어 주세요.
              </p>
            ) : reviews.length === 0 ? (
              <p className="gft-muted">아직 등록된 리뷰가 없어요.</p>
            ) : (
              <div className="gft-review-carousel" aria-roledescription="carousel">
                <div className="gft-review-carousel__chrome">
                  <button
                    type="button"
                    className="gft-review-nav"
                    aria-label="이전 후기"
                    disabled={reviewSlide <= 0}
                    onClick={() => scrollReviewBy(-1)}
                  >
                    ‹
                  </button>
                  <div className="gft-review-progress" aria-hidden>
                    <div
                      className="gft-review-progress__fill"
                      style={{ width: `${((reviewSlide + 1) / reviews.length) * 100}%` }}
                    />
                  </div>
                  <button
                    type="button"
                    className="gft-review-nav"
                    aria-label="다음 후기"
                    disabled={reviewSlide >= reviews.length - 1}
                    onClick={() => scrollReviewBy(1)}
                  >
                    ›
                  </button>
                </div>
                <p className="gft-review-counter" aria-live="polite">
                  {reviewSlide + 1} / {reviews.length}
                </p>
                <div className="gft-review-rail" ref={reviewTrackRef} tabIndex={0} role="region" aria-label="가이드 후기 슬라이드">
                  {reviews.map((r, i) => {
                    const id = r.id != null ? String(r.id) : `r-${i}`
                    const stars = Math.min(5, Math.max(0, Math.round(Number(r.rating) || 0)))
                    const when = formatReviewWhen(r.createdAt)
                    return (
                      <div key={id} className="gft-review-slide" aria-roledescription="slide">
                        <div className="gft-review">
                          <div className="gft-review-head">
                            <span className="gft-review-name">{r.writeNickname ?? '여행자'}</span>
                            <span className="gft-review-stars" aria-label={`${stars}점`}>
                              {'🌟'.repeat(stars)}
                            </span>
                          </div>
                          {when ? <p className="gft-review-when">{when}</p> : null}
                          <p className="gft-review-text">{r.content != null && String(r.content).trim() !== '' ? r.content : '내용 없음'}</p>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </section>
        </article>
      </div>

      <div className="gft-actions">
        <Link to={chatTo} state={chatState} className="gft-btn gft-btn--line">
          💬 1:1 채팅하기
        </Link>
        <Link
          to={isAuthenticated ? `/guides/${guideId}#match-request` : '/auth/login'}
          state={
            isAuthenticated
              ? { hint: '아래로 스크롤되면「매칭 요청 보내기」로 일정을 등록할 수 있어요.' }
              : {
                  returnTo: `/guides/${guideId}#match-request`,
                  hint: '로그인 후 달력이 있는 매칭 요청 섹션으로 이동해 이어갈 수 있어요.',
                }
          }
          className="gft-btn gft-btn--primary"
        >
          ✨ 동행 요청하기
        </Link>
      </div>
    </div>
  )
}
