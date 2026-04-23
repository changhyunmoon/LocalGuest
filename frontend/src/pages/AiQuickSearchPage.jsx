import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './AiQuickSearchPage.css'

const PLACEHOLDER =
  '예시) 부산 2박3일 / 3명(부모님+성인) / 총예산 40~60만원\n' +
  '- 희망 시간: 오전 10시 시작, 저녁 8시 전 종료\n' +
  '- 하고 싶은 것: 바다뷰 카페, 로컬 맛집, 야경 산책\n' +
  '- 제약사항: 계단 많은 코스/장거리 도보는 피하고 싶어요\n' +
  '- 언어: 한국어 가능한 가이드 희망 (영어 가능하면 더 좋아요)'
const LS_AI_SEARCH_SNAPSHOT = 'localguest_ai_search_snapshot_v1'
const LS_AI_MATCH_DRAFT = 'localguest_ai_match_draft_v1'
const LS_AI_CLIENT_SESSION = 'localguest_ai_client_session_v1'
const AI_GUIDE_DEFAULT_SHOW = 3
const AI_GUIDE_EXPANDED_SHOW = 5

const JEJU_OREUM_SPOTS = [
  { title: '아부오름', caption: '완만한 능선, 소풍 명소', tape: 'g' },
  { title: '새별오름', caption: '노을·야경이 예쁜 곳', tape: 'b' },
  { title: '안돌오름', caption: '한적한 산책 코스', tape: 'p' },
]

const DEFAULT_SPOTS = [
  { title: '로컬 산책로', caption: '가이드와 함께 여유롭게', tape: 'g' },
  { title: '숨은 뷰 포인트', caption: '사진·힐링에 좋아요', tape: 'b' },
  { title: '동네 맛집 골목', caption: '현지인 픽 코스', tape: 'p' },
]

function buildNarrative(data) {
  if (!data) return ''
  const parts = []
  if (data.notice) parts.push(String(data.notice).trim())
  if (data.conceptSummary) parts.push(String(data.conceptSummary).trim())
  const draft = data.matchRequestDraft
  if (draft?.concept) parts.push(String(draft.concept).trim())
  const recs = Array.isArray(data.recommendations) ? data.recommendations : []
  if (recs.length > 0) {
    parts.push('추천 가이드를 고른 이유를 짧게 정리했어요.')
    recs.slice(0, 4).forEach((r) => {
      if (r?.reason) parts.push(`• ${r.guideName ?? '가이드'}: ${String(r.reason).trim()}`)
    })
  }
  const text = parts.filter(Boolean).join('\n\n')
  return text.length > 2000 ? `${text.slice(0, 2000)}…` : text
}

function pickSpots(prompt, keywords) {
  const blob = `${prompt ?? ''} ${keywords?.region ?? ''}`.toLowerCase()
  if (blob.includes('제주')) return JEJU_OREUM_SPOTS
  return DEFAULT_SPOTS
}

function styleTags(styleRaw) {
  if (!styleRaw) return []
  return [...new Set(
    String(styleRaw)
      .split(/[,/|]+/g)
      .map((s) => s.trim())
      .filter(Boolean),
  )]
    .slice(0, 3)
    .map((t) => (t.startsWith('#') ? t : `#${t}`))
}

function tagsForGuide(rec, keywords, styleRaw) {
  const fromStyle = styleTags(styleRaw)
  if (fromStyle.length > 0) {
    return fromStyle
  }
  const fromMatch = rec?.matched?.tags
  if (Array.isArray(fromMatch) && fromMatch.length > 0) {
    return fromMatch.slice(0, 3).map((t) => (String(t).startsWith('#') ? String(t) : `#${t}`))
  }
  const acts = keywords?.activityTags
  if (Array.isArray(acts) && acts.length > 0) {
    return acts.slice(0, 3).map((t) => `#${t}`)
  }
  return []
}

function normalizeFallbackGuide(g, reasonText) {
  return {
    guideId: g?.guideId,
    guideName: g?.nickname ?? '가이드',
    representativeImageUrl: g?.profileImage ?? null,
    publicFeedThumbnailUrls: [],
    region: g?.region ?? '',
    guideStyle: g?.guideStyle ?? '',
    averageRating: g?.averageRating ?? 0,
    reviewCount: g?.reviewCount ?? 0,
    reason:
      reasonText ??
      'AI 결과가 비어 있어 활동 중인 가이드 목록에서 추천했어요. (지역 키워드가 어긋나면 목록에서 가까운 가이드를 골라요.)',
    matched: { tags: [] },
  }
}

/**
 * AI가 recommendations 를 비울 때 서버 keywords.region 과 다른 표기(경북 vs 구미 등)로 필터가 비는 것을 완화한다.
 */
function pickFallbackGuides(promptText, keywords, allGuides) {
  if (!Array.isArray(allGuides) || allGuides.length === 0) return []
  const p = String(promptText ?? '').trim()
  const regionKw = String(keywords?.region ?? '').trim()

  if (regionKw) {
    const byKw = allGuides.filter((g) => String(g?.region ?? '').includes(regionKw))
    if (byKw.length > 0) {
      return byKw.map((g) =>
        normalizeFallbackGuide(g, 'AI 추천 목록이 비어, 요청 지역에 가까운 활동 가이드를 골랐어요.'),
      )
    }
  }

  const tokens = p.match(/[\uAC00-\uD7A3]{2,}/g) ?? []
  for (const tok of tokens) {
    const hit = allGuides.filter((g) => String(g?.region ?? '').includes(tok))
    if (hit.length > 0) {
      return hit.map((g) =>
        normalizeFallbackGuide(
          g,
          `프롬프트에 나온 「${tok}」와 가이드 활동 지역을 맞춰 추렸어요. (AI 추천은 비어 있었어요)`,
        ),
      )
    }
  }

  for (const g of allGuides) {
    const r = String(g?.region ?? '').trim()
    if (r.length >= 2 && p.includes(r)) {
      return [
        normalizeFallbackGuide(
          g,
          '입력하신 문장과 가이드 지역이 겹쳐 후보로 보여 드려요. (AI 추천은 비어 있었어요)',
        ),
      ]
    }
  }

  return allGuides
    .slice(0, 3)
    .map((g) => normalizeFallbackGuide(g, 'AI 추천이 비어, 활성 가이드 중에서 보여 드려요.'))
}

export function AiQuickSearchPage() {
  const location = useLocation()
  const [prompt, setPrompt] = useState(() => String(location.state?.initialPrompt ?? ''))
  const [hasSearched, setHasSearched] = useState(false)
  const [panelOpen, setPanelOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [streamText, setStreamText] = useState('')
  const [showGuides, setShowGuides] = useState(false)
  const [showSpots, setShowSpots] = useState(false)
  const [fallbackGuides, setFallbackGuides] = useState([])
  const [expandedGuides, setExpandedGuides] = useState(false)
  /** @type {Record<string, Array<{ feedId: number, imageUrl?: string, content?: string }>>} */
  const [guideFeedsById, setGuideFeedsById] = useState({})
  /** @type {Record<string, string>} */
  const [guideStyleById, setGuideStyleById] = useState({})
  const typeTimerRef = useRef(null)
  const clientSessionIdRef = useRef('')

  useEffect(() => {
    try {
      let sid = sessionStorage.getItem(LS_AI_CLIENT_SESSION)
      if (!sid || typeof sid !== 'string') {
        sid =
          typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
            ? crypto.randomUUID()
            : `sess-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
        sessionStorage.setItem(LS_AI_CLIENT_SESSION, sid)
      }
      clientSessionIdRef.current = sid
    } catch {
      clientSessionIdRef.current = `sess-${Date.now()}`
    }
  }, [])

  const recommendations = useMemo(() => {
    if (!Array.isArray(result?.recommendations)) return []
    return result.recommendations
  }, [result?.recommendations])

  const openPanelSoon = useCallback(() => {
    setPanelOpen(false)
    requestAnimationFrame(() => {
      requestAnimationFrame(() => setPanelOpen(true))
    })
  }, [])

  const stopTypewriter = useCallback(() => {
    if (typeTimerRef.current != null) {
      clearInterval(typeTimerRef.current)
      typeTimerRef.current = null
    }
  }, [])

  const spotsTimerRef = useRef(null)

  const startTypewriter = useCallback(
    (full) => {
      stopTypewriter()
      if (spotsTimerRef.current != null) {
        clearTimeout(spotsTimerRef.current)
        spotsTimerRef.current = null
      }
      setStreamText('')
      setShowGuides(false)
      setShowSpots(false)
      if (!full) {
        setShowGuides(true)
        spotsTimerRef.current = setTimeout(() => setShowSpots(true), 400)
        return
      }
      let i = 0
      typeTimerRef.current = setInterval(() => {
        i += 2
        const next = full.slice(0, Math.min(i, full.length))
        setStreamText(next)
        if (next.length >= full.length) {
          stopTypewriter()
          setShowGuides(true)
          spotsTimerRef.current = setTimeout(() => setShowSpots(true), 420)
        }
      }, 14)
    },
    [stopTypewriter],
  )

  useEffect(
    () => () => {
      stopTypewriter()
      if (spotsTimerRef.current != null) {
        clearTimeout(spotsTimerRef.current)
      }
    },
    [stopTypewriter],
  )

  useEffect(() => {
    // 상세 화면으로 이동했다가 돌아왔을 때, 직전 AI 검색 패널을 복원한다.
    try {
      const raw = sessionStorage.getItem(LS_AI_SEARCH_SNAPSHOT)
      if (!raw) return
      const snap = JSON.parse(raw)
      if (!snap || typeof snap !== 'object') return
      const restoredPrompt = typeof snap.prompt === 'string' ? snap.prompt : ''
      const restoredResult = snap.result ?? null
      const restoredFallback = Array.isArray(snap.fallbackGuides) ? snap.fallbackGuides : []
      setPrompt(restoredPrompt)
      setResult(restoredResult)
      setFallbackGuides(restoredFallback)
      setExpandedGuides(Boolean(snap.expandedGuides))
      setHasSearched(Boolean(snap.hasSearched))
      // 뒤로가기 복원에서는 로딩/타이핑 없이 즉시 결과를 보여준다.
      setPanelOpen(false)
      const narrative = buildNarrative(restoredResult)
      setStreamText(narrative)
      setShowGuides(true)
      setShowSpots(true)
      requestAnimationFrame(() => {
        requestAnimationFrame(() => setPanelOpen(true))
      })
      sessionStorage.removeItem(LS_AI_SEARCH_SNAPSHOT)
    } catch {
      /* ignore */
    }
  }, [])

  const persistSnapshotForBack = useCallback(() => {
    try {
      sessionStorage.setItem(
        LS_AI_SEARCH_SNAPSHOT,
        JSON.stringify({
          prompt,
          hasSearched,
          panelOpen: true,
          result,
          fallbackGuides,
          expandedGuides,
        }),
      )
    } catch {
      /* ignore */
    }
  }, [expandedGuides, fallbackGuides, hasSearched, prompt, result])

  const persistMatchDraftForGuide = useCallback(
    (guideId) => {
      try {
        const draft = result?.matchRequestDraft ?? null
        if (!draft || !guideId) return
        sessionStorage.setItem(
          LS_AI_MATCH_DRAFT,
          JSON.stringify({
            guideId: String(guideId),
            savedAt: Date.now(),
            matchRequestDraft: draft,
          }),
        )
      } catch {
        /* ignore */
      }
    },
    [result],
  )

  const onClickGuideDetail = useCallback(
    (guideId, rank) => {
      const gid = guideId != null ? Number(guideId) : NaN
      if (Number.isFinite(gid) && rank != null && rank > 0) {
        void (async () => {
          try {
            await apiRequest('/ai/recommend/click', {
              method: 'POST',
              json: {
                policyVersion: result?.policyVersion || undefined,
                guideId: gid,
                rank,
              },
            })
          } catch {
            /* ignore — 클릭 로깅 실패는 탐색 흐름을 막지 않음 */
          }
        })()
      }
      persistMatchDraftForGuide(guideId)
      persistSnapshotForBack()
    },
    [persistMatchDraftForGuide, persistSnapshotForBack, result?.policyVersion],
  )

  useEffect(() => {
    const idsSource = recommendations.length > 0 ? recommendations : fallbackGuides
    const ids = [...new Set(idsSource.map((r) => r?.guideId).filter((id) => id != null))].map(String)
    if (ids.length === 0) {
      setGuideStyleById({})
      return
    }
    let cancelled = false
    ;(async () => {
      const next = {}
      await Promise.all(
        ids.map(async (id) => {
          try {
            const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
            const text = await res.text()
            if (!res.ok) return
            const row = text ? JSON.parse(text) : null
            const style = String(row?.guideStyle ?? '').trim()
            if (style) next[id] = style
          } catch {
            /* ignore */
          }
        }),
      )
      if (!cancelled) setGuideStyleById(next)
    })()
    return () => {
      cancelled = true
    }
  }, [recommendations, fallbackGuides])

  useEffect(() => {
    if (recommendations.length === 0) {
      setGuideFeedsById({})
      return
    }
    const showMax = expandedGuides ? AI_GUIDE_EXPANDED_SHOW : AI_GUIDE_DEFAULT_SHOW
    const ids = [...new Set(recommendations.slice(0, showMax).map((r) => r?.guideId).filter((id) => id != null))].map(String)
    if (ids.length === 0) return

    let cancelled = false
    ;(async () => {
      const next = {}
      await Promise.all(
        ids.map(async (id) => {
          try {
            const res = await apiRequest(`/guides/${id}/feeds`, { method: 'GET', skipAuth: true })
            const text = await res.text()
            if (!res.ok) return
            const data = text ? JSON.parse(text) : []
            next[id] = Array.isArray(data) ? data : []
          } catch {
            /* ignore */
          }
        }),
      )
      if (!cancelled) {
        setGuideFeedsById((prev) => ({ ...prev, ...next }))
      }
    })()
    return () => {
      cancelled = true
    }
  }, [recommendations, expandedGuides])

  const runSearch = async (e, options = {}) => {
    e?.preventDefault()
    const q = prompt.trim()
    if (!q || loading) return
    const topN = Number(options?.topN ?? AI_GUIDE_DEFAULT_SHOW)

    setError('')
    setResult(null)
    setFallbackGuides([])
    setHasSearched(true)
    setExpandedGuides(topN > AI_GUIDE_DEFAULT_SHOW)
    setLoading(true)
    openPanelSoon()

    try {
      const sessionId = clientSessionIdRef.current
      const res = await apiRequest('/ai/recommend', {
        method: 'POST',
        json: {
          prompt: q,
          topN,
          ...(sessionId ? { clientSessionId: sessionId } : {}),
        },
      })
      const text = await res.text()
      if (res.status === 401 || res.status === 403 || res.status === 302) {
        setError('login')
        setHasSearched(false)
        setPanelOpen(false)
        return
      }
      if (!res.ok) {
        throw new Error(text || '추천 요청에 실패했습니다.')
      }
      const contentType = String(res.headers.get('content-type') ?? '').toLowerCase()
      // 인증 리다이렉트가 fetch 단계에서 따라가진 경우 HTML이 내려올 수 있다.
      if (!contentType.includes('application/json') || text.startsWith('<!doctype html') || text.startsWith('<html')) {
        setError('login')
        setHasSearched(false)
        setPanelOpen(false)
        return
      }
      const data = text ? JSON.parse(text) : null
      setResult(data)
      const recommendations = Array.isArray(data?.recommendations) ? data.recommendations : []
      if (recommendations.length === 0) {
        try {
          const guideRes = await apiRequest('/guides', { method: 'GET', skipAuth: true })
          const guideText = await guideRes.text()
          if (guideRes.ok) {
            const all = guideText ? JSON.parse(guideText) : []
            const picked = pickFallbackGuides(q, data?.keywords, Array.isArray(all) ? all : [])
            setFallbackGuides(picked.slice(0, topN))
          }
        } catch {
          setFallbackGuides([])
        }
      }
      const narrative = buildNarrative(data)
      startTypewriter(narrative)
    } catch (err) {
      setResult(null)
      setError(err instanceof Error ? err.message : '오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const onPromptKeyDown = (e) => {
    // 한글 IME 조합 중 Enter는 확정 입력으로 처리하고 전송하지 않는다.
    if (e.nativeEvent?.isComposing) return
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void runSearch()
    }
  }

  const showMax = expandedGuides ? AI_GUIDE_EXPANDED_SHOW : AI_GUIDE_DEFAULT_SHOW
  const topGuides = recommendations.length > 0 ? recommendations.slice(0, showMax) : fallbackGuides.slice(0, showMax)
  const specialGuide = result?.specialSuggestion?.guide ?? null
  const specialClickRank =
    Array.isArray(result?.recommendations) && result.recommendations.length > 0
      ? result.recommendations.length + 1
      : 1
  const keywords = result?.keywords
  const spots = pickSpots(prompt, keywords)
  const activityHint =
    keywords?.activityTags && keywords.activityTags.length > 0
      ? keywords.activityTags.slice(0, 2).join(', ')
      : '당신의 여행 스타일'

  return (
    <div className={`ais ${hasSearched ? 'ais--after' : 'ais--hero'}`}>
      <form className="ais-prompt" onSubmit={(e) => void runSearch(e)} aria-label="AI 여행 요청">
        <div className="ais-prompt-wrap">
          <label className="ais-prompt-label ais-hero-title" htmlFor="ais-input">
            어떤 여행을 꿈꾸고 계신가요?
          </label>
          <div className="ais-prompt-bar ais-modern-paper-bar">
            <textarea
              id="ais-input"
              className="ais-input"
              rows={5}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={onPromptKeyDown}
              placeholder={PLACEHOLDER}
              disabled={loading}
              aria-busy={loading}
            />
            <button type="submit" className="ais-send" disabled={loading || !prompt.trim()} title="추천 받기">
              {loading ? (
                <span className="ais-send-spinner" aria-hidden />
              ) : (
                <span className="ais-send-icon" aria-hidden>
                  ➤
                </span>
              )}
              <span className="visually-hidden">추천 받기</span>
            </button>
          </div>
        </div>
      </form>

      {error === 'login' && (
        <p className="ais-inline-err">
          AI 추천은 로그인 후 이용할 수 있어요.{' '}
          <Link to="/auth/login" state={{ returnTo: '/ai-search', hint: '로그인하면 AI 맞춤 추천을 받을 수 있어요.' }}>
            로그인하기
          </Link>
        </p>
      )}
      {error && error !== 'login' && <p className="ais-inline-err">{error}</p>}

      {hasSearched && (
        <div className={`ais-grow ${panelOpen ? 'is-open' : ''}`}>
          <div className="ais-grow-inner">
            {loading && (
              <div className="ais-skeleton-block" aria-live="polite">
                <div className="ais-skeleton-line ais-skeleton-line--lg" />
                <div className="ais-skeleton-line" />
                <div className="ais-skeleton-line ais-skeleton-line--sm" />
              </div>
            )}

            {!loading && !result && error && error !== 'login' && (
              <div className="ais-panel ais-fail" role="alert">
                <p>{error}</p>
                <button type="button" className="ais-retry" onClick={() => void runSearch()}>
                  다시 시도
                </button>
              </div>
            )}

            {!loading && result && (
              <>
                <section className="ais-panel ais-panel--ai ais-grid-bg" aria-labelledby="ais-ai-title">
                  <span className="ais-ai-sparkle" aria-hidden>
                    ✦
                  </span>
                  <h2 id="ais-ai-title" className="ais-panel-title ais-panel-title--narrative">
                    LocalMate AI의 한마디
                  </h2>
                  <div className="ais-ai-body">
                    {streamText ? (
                      <p className="ais-ai-pre">{streamText}</p>
                    ) : (
                      <p className="ais-ai-muted">요약을 불러오는 중이에요…</p>
                    )}
                  </div>
                </section>

                <section
                  className={`ais-panel ais-panel--guides ${showGuides ? 'is-visible' : ''}`}
                  aria-labelledby="ais-guides-title"
                >
                  <h2 id="ais-guides-title" className="ais-panel-title ais-panel-title--row">
                    <span className="ais-title-icon" aria-hidden>
                      ▦
                    </span>
                    당신을 위해 선별한 베스트 가이드
                  </h2>
                  <p className="ais-sub">「{activityHint}」에 어울리는 가이드예요.</p>
                  {topGuides.length === 0 ? (
                    <p className="ais-muted">이번 요청에 맞는 가이드가 아직 없어요. 지역이나 일정을 넓혀 보면 어떨까요?</p>
                  ) : (
                    <ul className="ais-guide-list">
                      {topGuides.map((g, idx) => {
                        const gid = g.guideId != null ? String(g.guideId) : ''
                        const feeds = gid ? guideFeedsById[gid] ?? [] : []
                        const rating =
                          g.averageRating != null && g.averageRating !== ''
                            ? Number(g.averageRating).toFixed(1)
                            : '—'
                        const rc = g.reviewCount != null ? g.reviewCount : 0
                        const img =
                          g.representativeImageUrl ||
                          (Array.isArray(g.publicFeedThumbnailUrls) && g.publicFeedThumbnailUrls[0]) ||
                          null
                        const tags = tagsForGuide(g, keywords, (gid ? guideStyleById[gid] : '') || g.guideStyle)
                        return (
                          <li key={g.guideId ?? idx} className="ais-guide-card ais-polaroid-card">
                            <div className="ais-rank" aria-label={`추천 순위 ${idx + 1}위`}>
                              <span className="ais-rank-badge">{idx + 1}</span>
                              <span className="ais-rank-text">추천 {idx + 1}위</span>
                            </div>
                            <div className="ais-guide-feeds" aria-label="가이드 피드">
                              {feeds.length > 0 ? (
                                feeds.slice(0, 6).map((f) => (
                                  <Link
                                    key={f.feedId}
                                    to={`/guides/${g.guideId}#match-request`}
                                    className="ais-feed-thumb"
                                    title={f.content ? String(f.content).slice(0, 80) : '가이드 상세보기'}
                                    onClick={() => onClickGuideDetail(g.guideId, idx + 1)}
                                  >
                                    <span
                                      className="ais-feed-thumb-img"
                                      style={f.imageUrl ? { backgroundImage: `url(${f.imageUrl})` } : undefined}
                                    />
                                    <span className="ais-feed-thumb-cap">코스</span>
                                  </Link>
                                ))
                              ) : (
                                <Link
                                  to={`/guides/${g.guideId}#match-request`}
                                  className="ais-feed-empty"
                                  title="가이드 상세보기"
                                  onClick={() => onClickGuideDetail(g.guideId, idx + 1)}
                                >
                                  <span
                                    className="ais-feed-thumb-img"
                                    style={img ? { backgroundImage: `url(${img})` } : undefined}
                                  />
                                  <span className="ais-feed-thumb-cap">코스 상세</span>
                                </Link>
                              )}
                            </div>
                            <div className="ais-guide-row">
                              <div
                                className="ais-guide-photo"
                                style={img ? { backgroundImage: `url(${img})` } : undefined}
                              />
                              <div className="ais-guide-main">
                                <p className="ais-guide-name">{g.guideName ?? '가이드'}</p>
                                <p className="ais-guide-rating">
                                  🌟 {rating} <span className="ais-guide-rc">({rc})</span>
                                </p>
                                <p className="ais-guide-region">{g.region ?? ''}</p>
                                <p className="ais-guide-reason">{g.reason ? String(g.reason).slice(0, 120) : ''}</p>
                                {tags.length > 0 && (
                                  <div className="ais-guide-tags">
                                    {tags.map((t) => (
                                      <span key={t} className="ais-chip">
                                        {t}
                                      </span>
                                    ))}
                                  </div>
                                )}
                              </div>
                              <Link
                                to={`/guides/${g.guideId}#match-request`}
                                className="ais-profile-btn ais-profile-btn--primary"
                                onClick={() => onClickGuideDetail(g.guideId, idx + 1)}
                              >
                                가이드 상세보기
                              </Link>
                            </div>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                  {!loading && !expandedGuides && recommendations.length > AI_GUIDE_DEFAULT_SHOW && (
                    <button
                      type="button"
                      className="ais-more"
                      onClick={() => void runSearch(null, { topN: AI_GUIDE_EXPANDED_SHOW })}
                    >
                      추천 가이드 더 보기
                    </button>
                  )}
                </section>

                {specialGuide && specialGuide.guideId != null && (
                  <section
                    className={`ais-panel ais-panel--special ${showGuides ? 'is-visible' : ''}`}
                    aria-labelledby="ais-special-title"
                  >
                    <h2 id="ais-special-title" className="ais-panel-title">
                      참고로 볼 만한 가이드
                    </h2>
                    <p className="ais-sub">
                      {String(result?.specialSuggestion?.notice ?? '').trim() ||
                        '선택한 날짜 기준으로 메인 추천에서는 빠졌지만, 조건은 잘 맞아 참고용으로 보여 드려요.'}
                    </p>
                    {(() => {
                      const sg = specialGuide
                      const sgid = String(sg.guideId)
                      const sgImg =
                        sg.representativeImageUrl ||
                        (Array.isArray(sg.publicFeedThumbnailUrls) && sg.publicFeedThumbnailUrls[0]) ||
                        null
                      const sgRating =
                        sg.averageRating != null && sg.averageRating !== ''
                          ? Number(sg.averageRating).toFixed(1)
                          : '—'
                      const sgRc = sg.reviewCount != null ? sg.reviewCount : 0
                      const sgTags = tagsForGuide(sg, keywords, guideStyleById[sgid] || sg.guideStyle)
                      return (
                        <div className="ais-special-card">
                          <div className="ais-guide-row ais-guide-row--special">
                            <div
                              className="ais-guide-photo"
                              style={sgImg ? { backgroundImage: `url(${sgImg})` } : undefined}
                            />
                            <div className="ais-guide-main">
                              <p className="ais-guide-name">{sg.guideName ?? '가이드'}</p>
                              <p className="ais-guide-rating">
                                🌟 {sgRating} <span className="ais-guide-rc">({sgRc})</span>
                              </p>
                              <p className="ais-guide-region">{sg.region ?? ''}</p>
                              <p className="ais-guide-reason">{sg.reason ? String(sg.reason).slice(0, 160) : ''}</p>
                              {sgTags.length > 0 && (
                                <div className="ais-guide-tags">
                                  {sgTags.map((t) => (
                                    <span key={t} className="ais-chip">
                                      {t}
                                    </span>
                                  ))}
                                </div>
                              )}
                            </div>
                            <Link
                              to={`/guides/${sg.guideId}#match-request`}
                              className="ais-profile-btn ais-profile-btn--primary"
                              onClick={() => onClickGuideDetail(sg.guideId, specialClickRank)}
                            >
                              가이드 상세보기
                            </Link>
                          </div>
                        </div>
                      )
                    })()}
                  </section>
                )}

                <section
                  className={`ais-panel ais-panel--spots ${showSpots ? 'is-visible' : ''}`}
                  aria-labelledby="ais-spots-title"
                >
                  <h2 id="ais-spots-title" className="ais-panel-title ais-panel-title--row">
                    <span className="ais-title-icon ais-title-icon--rose" aria-hidden>
                      ◎
                    </span>
                    코스에 꼭 넣어야 할 스팟
                  </h2>
                  <p className="ais-sub">가이드와 함께하면 더 즐거운 곳들이에요.</p>
                  <div className="ais-spot-grid">
                    {spots.map((s) => (
                      <article key={s.title} className="ais-spot">
                        <span className={`ais-spot-tape ais-spot-tape--${s.tape}`} aria-hidden />
                        <div className="ais-spot-img" />
                        <h3 className="ais-spot-name">{s.title}</h3>
                        <p className="ais-spot-cap">{s.caption}</p>
                      </article>
                    ))}
                  </div>
                </section>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
