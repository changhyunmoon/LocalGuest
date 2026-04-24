import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { fetchMyReviewsByMatchRequestId } from '../lib/guestMyReviews.js'
import { fetchGuestMatchRequests } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

function parseApiErrorMessage(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function parseRouteStops(proposedSchedule) {
  const raw = String(proposedSchedule ?? '').trim()
  if (!raw) return []
  return raw
    .split(/->|→|\r?\n|,/g)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function loadGuideNickname(guideId) {
  if (!guideId) return '가이드'
  try {
    const res = await apiRequest(`/guides/${guideId}`, { method: 'GET', skipAuth: true })
    const text = await res.text()
    if (!res.ok || !text) return `가이드 #${guideId}`
    const p = JSON.parse(text)
    return p.nickname ?? `가이드 #${guideId}`
  } catch {
    return `가이드 #${guideId}`
  }
}

export function MypageScrapbookDetailPage() {
  const { requestId } = useParams()
  const navigate = useNavigate()
  const [row, setRow] = useState(null)
  const [guideName, setGuideName] = useState('가이드')
  const [reviewByMatch, setReviewByMatch] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reviewModal, setReviewModal] = useState(false)
  const [autoOpened, setAutoOpened] = useState(false)
  const [rating, setRating] = useState(5)
  const [content, setContent] = useState('')
  const [reviewBusy, setReviewBusy] = useState(false)
  const [reviewError, setReviewError] = useState('')
  const [reviewPhotos, setReviewPhotos] = useState([])
  const reviewFileInputRef = useRef(null)

  const load = useCallback(async () => {
    const rid = Number(requestId)
    if (!Number.isFinite(rid)) throw new Error('잘못된 여행 기록 ID입니다.')
    const [all, reviews] = await Promise.all([
      fetchGuestMatchRequests(apiRequest),
      fetchMyReviewsByMatchRequestId(apiRequest).catch(() => ({})),
    ])
    const found = (Array.isArray(all) ? all : []).find((r) => Number(r.requestId) === rid)
    if (!found) throw new Error('해당 여행 기록을 찾을 수 없습니다.')
    setRow(found)
    setReviewByMatch(reviews)
    const nick = await loadGuideNickname(found.guideId)
    setGuideName(nick)
  }, [requestId])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await load()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [load])

  const routeStops = useMemo(() => parseRouteStops(row?.proposedSchedule), [row?.proposedSchedule])
  const fullCourseText = String(row?.proposedSchedule ?? '').trim()
  const hasReview = reviewByMatch[Number(row?.requestId)] != null
  const ratingLabel = useMemo(() => {
    const labels = {
      5: '5점 - 최고의 여행이었어요!',
      4: '4점 - 아주 좋았어요!',
      3: '3점 - 만족스러웠어요!',
      2: '2점 - 아쉬운 점이 있어요',
      1: '1점 - 기대에 못 미쳤어요',
    }
    return labels[rating] ?? ''
  }, [rating])

  const submitReview = async () => {
    if (!row) return
    const trimmed = content.trim()
    if (trimmed.length < 10) {
      setReviewError('후기는 10자 이상 입력해 주세요.')
      return
    }
    setReviewBusy(true)
    setReviewError('')
    try {
      const res = await apiRequest('/reviews', {
        method: 'POST',
        json: {
          guideId: Number(row.guideId),
          matchRequestId: Number(row.requestId),
          rating,
          content: trimmed,
        },
      })
      const text = await res.text()
      if (!res.ok) {
        setReviewError(parseApiErrorMessage(text))
        return
      }
      setReviewModal(false)
      setContent('')
      setReviewPhotos([])
      const reviews = await fetchMyReviewsByMatchRequestId(apiRequest).catch(() => ({}))
      setReviewByMatch(reviews)
    } catch {
      setReviewError('리뷰 등록 중 네트워크 오류가 발생했습니다.')
    } finally {
      setReviewBusy(false)
    }
  }

  useEffect(() => {
    if (!row || autoOpened) return
    if (!hasReview) {
      setReviewModal(true)
    }
    setAutoOpened(true)
  }, [row, hasReview, autoOpened])

  if (loading) return <PageLoading />
  if (error) return <PageError message={error} onRetry={() => void load()} />
  if (!row) return null

  return (
    <div className="mp-member">
      <button type="button" className="mp-back-chip" onClick={() => navigate('/mypage/scrapbook')} aria-label="스크랩북으로 돌아가기">
        <span className="mp-back-chip__icon" aria-hidden>
          ←
        </span>
        스크랩북으로 돌아가기
      </button>

      <div className="mp-scrap-detail">
        <p className="mp-dday">
          {row.desiredDate} ·{' '}
          {row.guideId != null ? (
            <Link to={`/guides/${row.guideId}`} className="mp-guide-link">
              {guideName}
            </Link>
          ) : (
            guideName
          )}
        </p>
        <h1>{row.destination}</h1>
        <p className="sub">{row.conceptSummary ?? row.concept ?? '여행 컨셉 정보가 없습니다.'}</p>

        <section className="mp-route">
          <p className="mp-route-title">확정 루트</p>
          {routeStops.length > 0 ? (
            <ol className="mp-route-list">
              {routeStops.map((spot, idx) => (
                <li key={`${row.requestId}-${idx}`}>{spot}</li>
              ))}
            </ol>
          ) : (
            <p className="mp-trip-detail">등록된 확정 루트 정보가 없습니다.</p>
          )}
        </section>

        {fullCourseText && (
          <section className="mp-route">
            <p className="mp-route-title">가이드가 공유한 상세 코스</p>
            <pre className="mp-course-full">{fullCourseText}</pre>
          </section>
        )}

        <div className="mp-trip-actions mp-trip-actions--under-route">
          {hasReview ? (
            <>
              <p className="mp-review-done">후기 작성 완료</p>
              <Link to="/mypage/reviews" className="mp-btn mp-btn--line">내 후기 보기</Link>
            </>
          ) : (
            <button
              type="button"
              className="mp-btn"
              onClick={() => {
                setReviewError('')
                setRating(5)
                setContent('')
                setReviewPhotos([])
                setReviewModal(true)
              }}
            >
              후기 작성
            </button>
          )}
        </div>
      </div>

      {reviewModal && (
        <div className="mp-modal-overlay" role="dialog" aria-modal="true" aria-label="후기 작성" onClick={() => !reviewBusy && setReviewModal(false)}>
          <div className="mp-modal mp-review-modal" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="mp-review-close" aria-label="후기 모달 닫기" onClick={() => setReviewModal(false)} disabled={reviewBusy}>
              ×
            </button>
            <div className="mp-review-tape" aria-hidden />
            <h2 className="mp-review-title">투어는 어떠셨나요? ✨</h2>
            <p className="mp-review-sub">
              {row.destination} ·{' '}
              {row.guideId != null ? (
                <Link to={`/guides/${row.guideId}`} className="mp-guide-link">
                  {guideName}
                </Link>
              ) : (
                guideName
              )}
              과의 추억을 남겨주세요.
            </p>

            <div className="mp-review-stars" role="radiogroup" aria-label="별점 선택">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  type="button"
                  className={`mp-review-star${n <= rating ? ' is-on' : ''}`}
                  aria-label={`${n}점`}
                  aria-checked={n === rating}
                  role="radio"
                  onClick={() => setRating(n)}
                  disabled={reviewBusy}
                >
                  🌟
                </button>
              ))}
            </div>
            <p className="mp-review-rating-label">{ratingLabel}</p>

            <textarea
              className="mp-modal-text mp-review-textarea"
              rows={6}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="좋았던 점, 아쉬운 점, 다시 가고 싶은 이유를 자유롭게 남겨주세요."
              disabled={reviewBusy}
            />

            <div className="mp-review-upload-row">
              <input
                ref={reviewFileInputRef}
                type="file"
                accept="image/*"
                multiple
                style={{ display: 'none' }}
                onChange={(e) => setReviewPhotos(Array.from(e.target.files ?? []))}
              />
              <button type="button" className="mp-btn mp-btn--line" disabled={reviewBusy} onClick={() => reviewFileInputRef.current?.click()}>
                📷 사진 첨부
              </button>
              <span className="mp-review-upload-note">
                {reviewPhotos.length > 0 ? `${reviewPhotos.length}장의 사진이 첨부되었습니다.` : '사진을 첨부할 수 있습니다.'}
              </span>
            </div>

            {reviewError && <p className="err" style={{ marginTop: '0.55rem', marginBottom: 0 }}>{reviewError}</p>}
            <div className="mp-modal-actions">
              <button type="button" className="mp-btn mp-review-submit" onClick={() => void submitReview()} disabled={reviewBusy}>
                {reviewBusy ? '리뷰 등록 중…' : '리뷰 등록하기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
