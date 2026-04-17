import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { useAuth } from '../context/useAuth.js'

import './HomePage.css'

const DESTINATIONS = [
  { name: '제주', query: '제주' },
  { name: '부산', query: '부산' },
  { name: '강릉', query: '강릉' },
  { name: '여수', query: '여수' },
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

export function HomePage() {
  const { isAuthenticated } = useAuth()
  const [guides, setGuides] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

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
    setLoading(true)
    setError('')
    try {
      await loadGuides()
    } catch (e) {
      setError(e instanceof Error ? e.message : '오류')
    } finally {
      setLoading(false)
    }
  }, [loadGuides])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await loadGuides()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [loadGuides])

  const experts = guides.slice(0, 3)

  const aiCta = isAuthenticated ? (
    <Link to="/ai-search" className="f02-cta">
      ✨ AI 맞춤 가이드 찾기
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
      ✨ AI 맞춤 가이드 찾기
    </Link>
  )

  return (
    <div className="f02">
      <section className="f02-hero" aria-labelledby="f02-hero-title">
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
              <div className="f02-polaroid-img" />
              <span className="f02-polaroid-cap">hidden spots ✨</span>
            </div>
            <div className="f02-polaroid f02-polaroid--b">
              <div className="f02-polaroid-tape f02-polaroid-tape--b" />
              <div className="f02-polaroid-img f02-polaroid-img--b" />
              <span className="f02-polaroid-cap">coffee!</span>
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
              <div className="f02-dest-photo" />
              <span className="f02-dest-name">{d.name}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="f02-section f02-section--experts" aria-labelledby="f02-exp-title">
        <h2 id="f02-exp-title" className="f02-section-title f02-section-title--solo">
          Local Experts
        </h2>
        {loading && <PageLoading label="가이드를 불러오는 중…" />}
        {!loading && error && <PageError message={error} onRetry={() => void refetchGuides()} />}
        {!loading && !error && experts.length === 0 && (
          <PageEmpty title="등록된 가이드가 없습니다">가이드 신청을 기다리고 있어요.</PageEmpty>
        )}
        {!loading && !error && experts.length > 0 && (
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
                  <Link to={`/guides/${g.guideId}`} className="f02-expert-link">
                    <div className="f02-expert-top">
                      <div className="f02-expert-avatar" style={g.profileImage ? { backgroundImage: `url(${g.profileImage})` } : undefined} />
                      <div className="f02-expert-meta">
                        <p className="f02-expert-rating">⭐ {rating}</p>
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
