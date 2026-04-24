import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './AiQuickSearchPage.css'

const PLACEHOLDER =
  '예시) 부산으로 2박 3일 여행 가고 싶어요. 부모님 포함 3명이고, 바다 뷰 카페랑 로컬 맛집 위주로 조용히 걷는 코스를 원해요.\n' +
  '오전 10시쯤 시작해서 저녁 8시 전에는 마무리하고 싶고, 계단 많은 곳이나 장거리 도보는 피하고 싶어요.\n' +
  '총 예산은 40~60만 원 정도 생각 중이고, 한국어 가능한 가이드를 선호해요. (영어도 가능하면 더 좋아요)'
const LS_AI_SEARCH_SNAPSHOT = 'localguest_ai_search_snapshot_v1'
const LS_AI_MATCH_DRAFT = 'localguest_ai_match_draft_v1'
const LS_AI_CLIENT_SESSION = 'localguest_ai_client_session_v1'
/** 상단 폴라로이드로 고정 노출 */
const AI_TOP_POLAROID = 3
/** API한번에 받아 둘 후보 수 (4위~ 더보기용) */
const AI_FETCH_TOP_N = 20

function buildNarrative(data) {
  if (!data) return ''
  const parts = []
  if (data.notice) parts.push(String(data.notice).trim())
  if (data.conceptSummary) parts.push(String(data.conceptSummary).trim())
  const draft = data.matchRequestDraft
  if (draft?.concept) parts.push(String(draft.concept).trim())
  const recs = Array.isArray(data.recommendations) ? data.recommendations : []
  if (recs.length > 0) {
    parts.push('여행자님 취향을 바탕으로, 이렇게 골랐어요.')
    recs.slice(0, 4).forEach((r) => {
      if (r?.reason) parts.push(`• ${r.guideName ?? '가이드'}: ${String(r.reason).trim()}`)
    })
  }
  const text = parts.filter(Boolean).join('\n\n')
  return text.length > 2000 ? `${text.slice(0, 2000)}…` : text
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
      '요청 내용과 지역 힌트를 기준으로, 활동 중인 가이드 후보를 먼저 골라 보여드렸어요.',
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
        normalizeFallbackGuide(g, '요청하신 지역과 가까운 곳에서 활동하는 가이드를 먼저 골랐어요.'),
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
          `문장에 나온 「${tok}」를 힌트로, 해당 지역에서 활동하는 가이드를 먼저 골랐어요.`,
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
          '입력하신 문장에 포함된 지역 힌트를 기준으로 후보를 골랐어요.',
        ),
      ]
    }
  }

  return allGuides
    .slice(0, 3)
    .map((g) => normalizeFallbackGuide(g, '조건을 넓혀, 현재 활동 중인 가이드부터 보여드릴게요.'))
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
  const [fallbackGuides, setFallbackGuides] = useState([])
  /** true면 상단 탑3 폴라로이드는 접고 4위~ 목록만 표시 */
  const [showRestGuideList, setShowRestGuideList] = useState(false)
  const [polaroidExit, setPolaroidExit] = useState(false)
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

  const startTypewriter = useCallback(
    (full) => {
      stopTypewriter()
      setStreamText('')
      setShowGuides(false)
      if (!full) {
        setShowGuides(true)
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
        }
      }, 14)
    },
    [stopTypewriter],
  )

  useEffect(
    () => () => {
      stopTypewriter()
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
      setShowRestGuideList(Boolean(snap.showRestGuideList))
      setPolaroidExit(Boolean(snap.showRestGuideList))
      setHasSearched(Boolean(snap.hasSearched))
      // 뒤로가기 복원에서는 로딩/타이핑 없이 즉시 결과를 보여준다.
      setPanelOpen(false)
      const narrative = buildNarrative(restoredResult)
      setStreamText(narrative)
      setShowGuides(true)
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
          showRestGuideList,
        }),
      )
    } catch {
      /* ignore */
    }
  }, [showRestGuideList, fallbackGuides, hasSearched, prompt, result])

  const persistMatchDraftForGuide = useCallback(
    (guideId) => {
      try {
        if (guideId == null || guideId === '') return

        const draft =
          result?.matchRequestDraft != null && typeof result.matchRequestDraft === 'object'
            ? { ...result.matchRequestDraft }
            : {}

        const topSummary = typeof result?.conceptSummary === 'string' ? String(result.conceptSummary).trim() : ''
        if (topSummary && !String(draft.conceptSummary || '').trim()) {
          draft.conceptSummary = topSummary
        }
        const region = result?.keywords?.region
        if (region && !String(draft.destination || '').trim()) {
          draft.destination = String(region).trim()
        }

        const hasPayload = Object.keys(draft).length > 0
        if (!hasPayload) return
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
    const src = recommendations.length > 0 ? recommendations : fallbackGuides
    if (src.length === 0) {
      setGuideFeedsById({})
      return
    }
    const slice = showRestGuideList ? src : src.slice(0, AI_TOP_POLAROID)
    const ids = [...new Set(slice.map((r) => r?.guideId).filter((id) => id != null))].map(String)
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
  }, [recommendations, fallbackGuides, showRestGuideList])

  const runSearch = async (e, options = {}) => {
    e?.preventDefault()
    const q = prompt.trim()
    if (!q || loading) return
    const topN = Number(options?.topN ?? AI_FETCH_TOP_N)

    setError('')
    setResult(null)
    setFallbackGuides([])
    setHasSearched(true)
    setShowRestGuideList(false)
    setPolaroidExit(false)
    setLoading(true)
    openPanelSoon()

    try {
      const sessionId = clientSessionIdRef.current
      const res = await apiRequest('/ai/recommend', {
        method: 'POST',
        skipAuth: true,
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

  const allRecs = useMemo(() => {
    if (recommendations.length > 0) return recommendations
    return fallbackGuides
  }, [recommendations, fallbackGuides])
  const topPolaroidGuides = useMemo(() => allRecs.slice(0, AI_TOP_POLAROID), [allRecs])
  const restGuideItems = useMemo(() => allRecs.slice(AI_TOP_POLAROID), [allRecs])
  const specialGuide = result?.specialSuggestion?.guide ?? null
  const specialClickRank =
    Array.isArray(result?.recommendations) && result.recommendations.length > 0
      ? result.recommendations.length + 1
      : 1
  const keywords = result?.keywords
  const activityHint =
    keywords?.activityTags && keywords.activityTags.length > 0
      ? keywords.activityTags.slice(0, 2).join(', ')
      : '당신의 여행 스타일'

  const onRevealRestGuides = useCallback(() => {
    if (restGuideItems.length === 0) return
    setPolaroidExit(true)
    window.setTimeout(() => {
      setShowRestGuideList(true)
    }, 420)
  }, [restGuideItems.length])

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
                  {allRecs.length === 0 ? (
                    <p className="ais-muted">이번 요청에 맞는 가이드가 아직 없어요. 지역이나 일정을 넓혀 보면 어떨까요?</p>
                  ) : (
                    <>
                      {!showRestGuideList && topPolaroidGuides.length > 0 && (
                        <div className={`ais-polaroid-strip${polaroidExit ? ' ais-polaroid-strip--exit' : ''}`}>
                          <ul className="ais-polaroid-row">
                            {topPolaroidGuides.map((g, idx) => {
                              const gid = g.guideId != null ? String(g.guideId) : `i-${idx}`
                              const img =
                                g.representativeImageUrl ||
                                (Array.isArray(g.publicFeedThumbnailUrls) && g.publicFeedThumbnailUrls[0]) ||
                                null
                              const rating =
                                g.averageRating != null && g.averageRating !== ''
                                  ? Number(g.averageRating).toFixed(1)
                                  : '—'
                              const rc = g.reviewCount != null ? g.reviewCount : 0
                              return (
                                <li
                                  key={gid}
                                  className={`ais-polaroid-tile ais-polaroid-tile--${idx % 3}`}
                                >
                                  <div className="ais-polaroid-frame">
                                    <span className="ais-polaroid-pin" aria-hidden />
                                    <Link
                                      to={`/guides/${g.guideId}`}
                                      className="ais-polaroid-link"
                                      onClick={() => onClickGuideDetail(g.guideId, idx + 1)}
                                    >
                                      <div
                                        className="ais-polaroid-photo"
                                        style={img ? { backgroundImage: `url(${img})` } : undefined}
                                      />
                                      <div className="ais-polaroid-cap">
                                        <span className="ais-polaroid-rank" aria-label={`추천 ${idx + 1}위`}>
                                          {idx + 1}
                                        </span>
                                        <p className="ais-polaroid-name">{g.guideName ?? '가이드'}</p>
                                        <p className="ais-polaroid-region">{g.region ?? ''}</p>
                                        <p className="ais-polaroid-rating">
                                          🌟 {rating} <span className="ais-polaroid-rc">({rc})</span>
                                        </p>
                                        {g.reason && String(g.reason).trim() ? (
                                          <p className="ais-polaroid-reason" title={String(g.reason).trim()}>
                                            {String(g.reason).trim().length > 120
                                              ? `${String(g.reason).trim().slice(0, 120)}…`
                                              : String(g.reason).trim()}
                                          </p>
                                        ) : (
                                          <p className="ais-polaroid-reason ais-polaroid-reason--muted">
                                            AI가 이 가이드를 추천한 이유가 아직 짧게만 전달돼요. 상세에서 프로필을
                                            비교해 보세요.
                                          </p>
                                        )}
                                      </div>
                                    </Link>
                                  </div>
                                </li>
                              )
                            })}
                          </ul>
                          {restGuideItems.length > 0 && (
                            <div className="ais-more-wrap">
                              <button
                                type="button"
                                className="ais-more-guides"
                                onClick={onRevealRestGuides}
                              >
                                가이드 더보기
                                <span className="ais-more-guides-sub">
                                  {' '}
                                  · {restGuideItems.length}명 더보기
                                </span>
                              </button>
                            </div>
                          )}
                        </div>
                      )}

                      {showRestGuideList && restGuideItems.length > 0 && (
                        <ul className="ais-guide-list ais-guide-list--rest is-enter" aria-label="추가 추천 가이드">
                          {restGuideItems.map((g, idx) => {
                            const gid = g.guideId != null ? String(g.guideId) : ''
                            const rank = AI_TOP_POLAROID + idx + 1
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
                            const tags = tagsForGuide(
                              g,
                              keywords,
                              (gid ? guideStyleById[gid] : '') || g.guideStyle,
                            )
                            return (
                              <li key={g.guideId ?? `r-${idx}`} className="ais-guide-card ais-polaroid-card">
                                <div className="ais-rank" aria-label={`추천 순위 ${rank}위`}>
                                  <span className="ais-rank-badge">{rank}</span>
                                  <span className="ais-rank-text">추천 {rank}위</span>
                                </div>
                                <div className="ais-guide-feeds" aria-label="가이드 피드">
                                  {feeds.length > 0 ? (
                                    feeds.slice(0, 6).map((f) => (
                                      <Link
                                        key={f.feedId}
                                        to={`/guides/${g.guideId}`}
                                        className="ais-feed-thumb"
                                        title={f.content ? String(f.content).slice(0, 80) : '가이드 상세보기'}
                                        onClick={() => onClickGuideDetail(g.guideId, rank)}
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
                                      to={`/guides/${g.guideId}`}
                                      className="ais-feed-empty"
                                      title="가이드 상세보기"
                                      onClick={() => onClickGuideDetail(g.guideId, rank)}
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
                                    <p className="ais-guide-reason">
                                      {g.reason ? String(g.reason).slice(0, 120) : ''}
                                    </p>
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
                                    to={`/guides/${g.guideId}`}
                                    className="ais-profile-btn ais-profile-btn--primary"
                                    onClick={() => onClickGuideDetail(g.guideId, rank)}
                                  >
                                    가이드 상세보기
                                  </Link>
                                </div>
                              </li>
                            )
                          })}
                        </ul>
                      )}
                    </>
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
                              to={`/guides/${sg.guideId}`}
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
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
