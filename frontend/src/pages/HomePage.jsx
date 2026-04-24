import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { useAuth } from '../context/useAuth.js'
import { extractReviewListFromPage, reviewStableKey } from '../lib/reviewPage.js'

import './HomePage.css'

const POLAROID_A = '/hero/hero-polaroid-coastal-mint.png'
const POLAROID_B = '/hero/hero-polaroid-cafe-mint.png'

const DESTINATIONS = [
  { name: '제주', query: '제주', image: '/destinations/jeju.webp' },
  { name: '부산', query: '부산', image: '/destinations/busan.webp' },
  { name: '강릉', query: '강릉', image: '/destinations/gangneung.webp' },
  { name: '여수', query: '여수', image: '/destinations/yeosu.webp' },
]

function parseTags(keywords) {
  if (keywords == null || keywords === '') return []
  return String(keywords)
    .split(/[,#\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 3)
}

function truncate(s, n) {
  if (!s) return ''
  const t = String(s).trim()
  return t.length <= n ? t : `${t.slice(0, n)}…`
}

function formatReviewDate(iso) {
  if (!iso) return ''
  try {
    const d = new Date(String(iso))
    if (Number.isNaN(d.getTime())) return ''
    return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

export function HomePage() {
  const { isAuthenticated } = useAuth()
  const [guides, setGuides] = useState([])
  const [guidesLoading, setGuidesLoading] = useState(true)
  const [guidesError, setGuidesError] = useState('')

  const [reviews, setReviews] = useState([])
  const [reviewsLoading, setReviewsLoading] = useState(true)
  const [reviewsError, setReviewsError] = useState('')

  const loadGuides = useCallback(async () => {
    const res = await apiRequest('/guides', { method: 'GET', skipAuth: true })
    const text = await res.text()
    if (!res.ok) {
      throw new Error(text || '가이드 목록을 불러오지 못했습니다.')
    }
    const data = text ? JSON.parse(text) : []
    setGuides(Array.isArray(data) ? data : [])
  }, [])

  const refetchGuides = useCallback(async () => {
    setGuidesLoading(true)
    setGuidesError('')
    try {
      await loadGuides()
    } catch (e) {
      setGuidesError(e instanceof Error ? e.message : '오류')
    } finally {
      setGuidesLoading(false)
    }
  }, [loadGuides])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setGuidesLoading(true)
      setGuidesError('')
      try {
        await loadGuides()
      } catch (e) {
        if (!cancelled) setGuidesError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setGuidesLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [loadGuides])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setReviewsLoading(true)
      setReviewsError('')
      try {
        const res = await apiRequest('/reviews?size=4&sort=createdAt,desc', { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (cancelled) return
        if (!res.ok) {
          setReviews([])
          setReviewsError(text || '후기를 불러오지 못했습니다.')
          return
        }
        const page = text ? JSON.parse(text) : {}
        const list = extractReviewListFromPage(page).slice(0, 4)
        setReviews(list)
        setReviewsError('')
      } catch (e) {
        if (!cancelled) {
          setReviews([])
          setReviewsError(e instanceof Error ? e.message : '오류')
        }
      } finally {
        if (!cancelled) setReviewsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const experts = guides.slice(0, 3)

  const aiCta = isAuthenticated ? (
    <Link to="/ai-search" className="f02-cta">
      <span className="f02-cta-icon" aria-hidden>
        ✨
      </span>
      AI 맞춤 가이드 찾기
    </Link>
  ) : (
    <Link
      to="/auth/login"
      className="f02-cta"
      state={{
        returnTo: '/ai-search',
        hint: 'AI 맞춤 추천은 로그인 후 이용할 수 있습니다.',
      }}
    >
      <span className="f02-cta-icon" aria-hidden>
        ✨
      </span>
      AI 맞춤 가이드 찾기
    </Link>
  )

  return (
    <div className="f02">
      <section className="f02-hero" aria-labelledby="f02-hero-title">
        <div className="f02-hero-media" aria-hidden />
        <div className="f02-hero-inner">
          <div className="f02-notepad">
            <h1 id="f02-hero-title" className="f02-headline">
              계획 없는 여행, 현지인이 채워드립니다.
            </h1>
            <p className="f02-desc">
              스트레스 받는 여행 계획은 그만! 현지인 토박이와 매칭되어 진짜 로컬 여행을 경험해보세요 :)
            </p>
            {aiCta}
          </div>
          <div className="f02-hero-visual" aria-hidden>
            <div className="f02-polaroid f02-polaroid--a">
              <div className="f02-polaroid-tape" />
              <div className="f02-polaroid-img">
                <img src={POLAROID_A} alt="" width={360} height={480} decoding="async" />
              </div>
              <span className="f02-polaroid-cap">hidden spots ✨</span>
            </div>
            <div className="f02-polaroid f02-polaroid--b">
              <div className="f02-polaroid-tape f02-polaroid-tape--b" />
              <div className="f02-polaroid-img">
                <img src={POLAROID_B} alt="" width={360} height={480} decoding="async" />
              </div>
              <span className="f02-polaroid-cap">had the best coffee!</span>
            </div>
          </div>
        </div>
      </section>

      <section className="f02-section" aria-labelledby="f02-dest-title">
        <div className="f02-section-head">
          <h2 id="f02-dest-title" className="f02-section-title">
            Destinations
          </h2>
          <Link to="/guides" className="f02-view-all">
            View All
          </Link>
        </div>
        <div className="f02-dest-grid">
          {DESTINATIONS.map((d) => (
            <Link key={d.name} to={`/guides?region=${encodeURIComponent(d.query)}`} className="f02-dest-card">
              <span className="f02-dest-tape" aria-hidden />
              <div className="f02-dest-photo">
                <img src={d.image} alt="" width={320} height={400} loading="lazy" decoding="async" />
              </div>
              <span className="f02-dest-name">{d.name}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="f02-section f02-section--reviews" aria-labelledby="f02-rev-title">
        <div className="f02-section-head">
          <h2 id="f02-rev-title" className="f02-section-title">
            실제 여행자 후기
          </h2>
        </div>
        {reviewsLoading && <PageLoading label="최근 후기를 불러오는 중…" />}
        {!reviewsLoading && reviewsError && !reviews.length && (
          <p className="f02-muted" role="status">
            {reviewsError}
          </p>
        )}
        {!reviewsLoading && !reviewsError && reviews.length === 0 && (
          <PageEmpty title="아직 등록된 후기가 없어요">첫 매칭 후기가 곧 채워질 거예요.</PageEmpty>
        )}
        {!reviewsLoading && reviews.length > 0 && (
          <div className="f02-review-grid">
            {reviews.map((r, idx) => {
              const guideId = r.guideId != null ? Number(r.guideId) : null
              const rating = r.rating != null ? Number(r.rating) : 0
              const content = truncate(String(r.content ?? ''), 120)
              const nick = String(r.writeNickname ?? '여행자')
              const dateLine = formatReviewDate(r.createdAt)
              const inner = (
                <>
                  <div className="f02-review-top">
                    <span className="f02-review-stars" aria-label={`별점 ${rating}점`}>
                      {'★'.repeat(Math.min(5, Math.max(0, rating)))}
                      <span className="f02-review-stars-muted">
                        {'★'.repeat(Math.max(0, 5 - Math.min(5, Math.max(0, rating))))}
                      </span>
                    </span>
                    {dateLine ? <time className="f02-review-date">{dateLine}</time> : null}
                  </div>
                  <p className="f02-review-body">{content || '내용이 없습니다.'}</p>
                  <footer className="f02-review-foot">
                    <span className="f02-review-nick">{nick}</span>
                    {guideId != null && Number.isFinite(guideId) ? (
                      <span className="f02-review-more">가이드 보기 →</span>
                    ) : null}
                  </footer>
                </>
              )
              return guideId != null && Number.isFinite(guideId) ? (
                <Link key={reviewStableKey(r, idx)} to={`/guides/${guideId}`} className="f02-review-card">
                  {inner}
                </Link>
              ) : (
                <article key={reviewStableKey(r, idx)} className="f02-review-card f02-review-card--static">
                  {inner}
                </article>
              )
            })}
          </div>
        )}
      </section>

      <section className="f02-section f02-section--experts" aria-labelledby="f02-exp-title">
        <h2 id="f02-exp-title" className="f02-section-title f02-section-title--solo">
          Local Experts
        </h2>
        {guidesLoading && <PageLoading label="가이드를 불러오는 중…" />}
        {!guidesLoading && guidesError && <PageError message={guidesError} onRetry={() => void refetchGuides()} />}
        {!guidesLoading && !guidesError && experts.length === 0 && (
          <PageEmpty title="등록된 가이드가 없습니다">가이드 신청을 기다리고 있어요.</PageEmpty>
        )}
        {!guidesLoading && !guidesError && experts.length > 0 && (
          <div className="f02-expert-grid">
            {experts.map((g) => {
              const rating =
                g.averageRating != null && g.averageRating !== '' ? Number(g.averageRating).toFixed(1) : '—'
              const tags = parseTags(g.keywords)
              const fallbackTags = tags.length > 0 ? tags : ['#로컬투어', '#맞춤동행', g.region ? `#${g.region}` : '#가이드'].filter(
                Boolean,
              )
              return (
                <article key={g.guideId} className="f02-expert-card">
                  <span className="f02-expert-tape" aria-hidden />
                  <Link to={`/guides/${g.guideId}`} className="f02-expert-link">
                    <div className="f02-expert-top">
                      <div
                        className="f02-expert-avatar"
                        style={g.profileImage ? { backgroundImage: `url(${g.profileImage})` } : undefined}
                      />
                      <div className="f02-expert-meta">
                        <p className="f02-expert-rating">🌟 {rating}</p>
                        <strong className="f02-expert-name">{g.nickname ?? '가이드'}</strong>
                        <span className="f02-expert-region">{g.region ?? '지역 미등록'}</span>
                      </div>
                    </div>
                    <p className="f02-expert-bio">{truncate(g.bio, 96) || '소개글이 곧 채워질 예정이에요.'}</p>
                    <div className="f02-expert-tags">
                      {fallbackTags.slice(0, 3).map((t) => (
                        <span key={t} className="f02-tag">
                          {t.startsWith('#') ? t : `#${t}`}
                        </span>
                      ))}
                    </div>
                  </Link>
                </article>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
