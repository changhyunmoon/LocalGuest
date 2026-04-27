import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { PageEmpty, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { useAuth } from '../context/useAuth.js'
import { extractReviewListFromPage, reviewStableKey } from '../lib/reviewPage.js'

import './HomePage.css'

const POLAROID_A = '/hero/hero-polaroid-coastal-mint.png'
const POLAROID_B = '/hero/hero-polaroid-cafe-mint.png'

const DESTINATIONS = [
  { name: '제주', image: '/destinations/jeju.webp' },
  { name: '부산', image: '/destinations/busan.webp' },
  { name: '강릉', image: '/destinations/gangneung.webp' },
  { name: '여수', image: '/destinations/yeosu.webp' },
]

/** 민트 키 베이스·거의 흰색에 가까운 밝은 파스텔(한 행 카드끼리 색 구분) */
const MINT_POSTIT_PALETTE = [
  '#fdfffe',
  '#fcfefd',
  '#fefffd',
  '#fdfffc',
  '#fcfdfb',
  '#fbfffd',
  '#fafffc',
  '#f9fefd',
  '#fcfef9',
  '#fdfffa',
  '#fafff9',
  '#f8fefb',
]

function distinctMintColorsForReviews(reviews) {
  const pool = [...MINT_POSTIT_PALETTE]
  const base = reviews.reduce((acc, r, i) => acc + (Number(r.id) || 0) * (i + 3), 1)
  let s = Math.abs(base) % 2147483646 || 1
  const out = []
  for (let i = 0; i < reviews.length; i++) {
    if (pool.length === 0) break
    s = (s * 48271 + 49297) % 2147483647
    const j = s % pool.length
    out.push(pool.splice(j, 1)[0])
  }
  return out
}

function tiltDegForReview(r, idx) {
  const raw = Number(r.id)
  const seed = (Number.isFinite(raw) ? raw : 0) * 19 + idx * 11 + 5
  const t = (Math.abs(seed) % 21) - 10
  return Math.round(t * 0.35 * 10) / 10
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
  const [reviews, setReviews] = useState([])
  const [reviewsLoading, setReviewsLoading] = useState(true)
  const [reviewsError, setReviewsError] = useState('')

  const reviewMintColors = useMemo(() => distinctMintColorsForReviews(reviews), [reviews])

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
            <span className="f02-notepad-tape" aria-hidden />
            <div className="f02-notepad-surface">
              <h1 id="f02-hero-title" className="f02-headline">
                계획 없는 여행, 현지인이 채워드립니다.
              </h1>
              <p className="f02-desc">
                스트레스 받는 여행 계획은 그만! 현지인 토박이와 매칭되어 진짜 로컬 여행을 경험해보세요 :)
              </p>
              {aiCta}
            </div>
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
        <h2 id="f02-dest-title" className="f02-section-title f02-section-title--solo">
          Destinations
        </h2>
        <div className="f02-dest-grid">
          {DESTINATIONS.map((d) => (
            <div key={d.name} className="f02-dest-card" role="img" aria-label={`${d.name} 여행지 이미지`}>
              <span className="f02-dest-tape" aria-hidden />
              <div className="f02-dest-photo">
                <img src={d.image} alt="" width={320} height={400} loading="lazy" decoding="async" />
              </div>
              <span className="f02-dest-name">{d.name}</span>
            </div>
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
              const rating = r.rating != null ? Number(r.rating) : 0
              const content = String(r.content ?? '').trim() || '내용이 없습니다.'
              const nick = String(r.writeNickname ?? '여행자')
              const dateLine = formatReviewDate(r.createdAt)
              const postitBg = reviewMintColors[idx] ?? '#fdfffe'
              const tilt = tiltDegForReview(r, idx)
              return (
                <article
                  key={reviewStableKey(r, idx)}
                  className="f02-review-card"
                  style={{
                    '--f02-review-postit': postitBg,
                    '--f02-review-tilt': `${tilt}deg`,
                  }}
                >
                  <div className="f02-review-inner">
                    <div className="f02-review-top">
                      <span className="f02-review-stars" aria-label={`별점 ${rating}점`}>
                        {'★'.repeat(Math.min(5, Math.max(0, rating)))}
                        <span className="f02-review-stars-muted">
                          {'★'.repeat(Math.max(0, 5 - Math.min(5, Math.max(0, rating))))}
                        </span>
                      </span>
                      {dateLine ? <time className="f02-review-date">{dateLine}</time> : null}
                    </div>
                    <p className="f02-review-body">{content}</p>
                    <footer className="f02-review-foot">
                      <span className="f02-review-nick">{nick}</span>
                    </footer>
                  </div>
                  <span className="f02-review-tape" aria-hidden />
                </article>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
