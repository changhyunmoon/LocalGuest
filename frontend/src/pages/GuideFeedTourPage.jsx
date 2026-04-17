import { useEffect, useMemo, useState } from 'react'
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

export function GuideFeedTourPage() {
  const { guideId, feedId } = useParams()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [detail, setDetail] = useState(null)
  const [reviews, setReviews] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const [d, revPage] = await Promise.all([
          fetchJson(`/guides/${guideId}/detail`, { skipAuth: true }),
          fetchJson(`/reviews/guide/${guideId}?size=6&sort=createdAt,desc`, { skipAuth: true }).catch(() => ({
            content: [],
          })),
        ])
        if (!cancelled) {
          setDetail(d)
          const raw = revPage?.content
          setReviews(Array.isArray(raw) ? raw : [])
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
    <div className="gft">
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

        <article className="gft-card">
          <h1 className="gft-title">{tourTitle}</h1>
          <p className="gft-rating">
            ⭐ {rating}{' '}
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
            <h2 id="gft-rev" className="gft-section-title">
              생생한 후기 💌
            </h2>
            {reviews.length === 0 ? (
              <p className="gft-muted">아직 등록된 리뷰가 없어요.</p>
            ) : (
              <ul className="gft-reviews">
                {reviews.slice(0, 2).map((r) => (
                  <li key={r.id} className="gft-review">
                    <div className="gft-review-head">
                      <span className="gft-review-name">{r.writeNickname ?? '여행자'}</span>
                      <span className="gft-review-stars">{'⭐'.repeat(Math.min(5, Math.max(0, Number(r.rating) || 0)))}</span>
                    </div>
                    <p className="gft-review-text">{r.content}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </article>
      </div>

      <div className="gft-actions">
        <Link to={chatTo} state={chatState} className="gft-btn gft-btn--line">
          💬 1:1 채팅하기
        </Link>
        <Link
          to={isAuthenticated ? '/mypage/itinerary' : '/auth/login'}
          state={
            isAuthenticated
              ? { hint: '일정에서 매칭·요청 상태를 확인할 수 있어요.' }
              : { returnTo: `/guides/${guideId}/feeds/${feedId}`, hint: '동행 요청은 로그인 후 마이페이지 일정에서 이어갈 수 있어요.' }
          }
          className="gft-btn gft-btn--primary"
        >
          ✨ 동행 요청하기
        </Link>
      </div>
    </div>
  )
}
