import { useCallback, useEffect, useState } from 'react'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'

import '../layouts/GuideDashboardLayout.css'
import './GuideMypagePages.css'

function formatDt(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ko-KR')
  } catch {
    return String(iso)
  }
}

function stars(rating) {
  const n = Math.max(1, Math.min(5, Math.round(Number(rating ?? 0))))
  return '🌟'.repeat(n)
}

export function GuideReviewsManagePage() {
  const { guideId, loading: idLoading, error: idError } = useResolvedGuideId()
  const [summary, setSummary] = useState(null)
  const [summaryLoading, setSummaryLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [rows, setRows] = useState([])
  const [last, setLast] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [listLoading, setListLoading] = useState(false)

  const loadSummary = useCallback(async (id) => {
    setSummaryLoading(true)
    try {
      const res = await apiRequest(`/guides/${id}/reviews/summary`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) return
      setSummary(text ? JSON.parse(text) : null)
    } finally {
      setSummaryLoading(false)
    }
  }, [])

  const loadPage = useCallback(async (id, p) => {
    setListLoading(true)
    setLoadError('')
    try {
      const res = await apiRequest(`/reviews/guide/${id}?page=${p}&size=10`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) {
        setLoadError(text || '리뷰 목록 조회 실패')
        return
      }
      const data = text ? JSON.parse(text) : {}
      const content = Array.isArray(data.content) ? data.content : []
      setRows(content)
      setLast(data.last === true || content.length === 0)
      setTotalPages(data.totalPages > 0 ? data.totalPages : 1)
    } catch {
      setLoadError('리뷰 목록 조회 실패')
    } finally {
      setListLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!guideId) return
    void loadSummary(guideId)
  }, [guideId, loadSummary])

  useEffect(() => {
    if (!guideId) return
    void loadPage(guideId, page)
  }, [guideId, page, loadPage])

  if (idLoading) {
    return (
      <div className="g-panel">
        <PageLoading />
      </div>
    )
  }

  if (idError) {
    return (
      <div className="g-panel">
        <PageError message={idError} />
      </div>
    )
  }

  return (
    <div className="g-panel mp-member">
      {loadError && (
        <PageError message={loadError} onRetry={() => guideId != null && void loadPage(guideId, page)} />
      )}
      {listLoading && <PageLoading label="목록을 불러오는 중…" />}

      <div className="mp-scrap-hero" style={{ marginBottom: '0.85rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>🌟 게스트 리뷰</h1>
          <p className="sub" style={{ marginTop: '0.25rem' }}>
            게스트 리뷰 페이지와 같은 카드 UX로 확인할 수 있어요.
          </p>
        </div>
        {summaryLoading ? (
          <p className="mp-trip-detail">로딩 중…</p>
        ) : summary == null || summary?.reviewCount === 0 ? (
          <div className="mp-trip-detail">아직 받은 리뷰가 없어요</div>
        ) : (
          <div className="mp-review-summary-inline">
            <span>평균 {Number(summary.averageRating).toFixed(1)} / 5</span>
            <span>리뷰 {summary.reviewCount}건</span>
          </div>
        )}
      </div>

      {!listLoading && rows.length === 0 && (
        <PageEmpty title="등록된 리뷰가 없습니다">게스트가 남긴 후기가 있으면 벽에 붙듯 모여요.</PageEmpty>
      )}

      {rows.length > 0 && (
        <div className="mp-reviews-moodboard">
          {rows.map((review, idx) => {
            const c = ['mint', 'amber', 'rose', 'indigo'][idx % 4]
            return (
              <article
                key={review.id}
                className={`mp-review-postit mp-review-postit--${c}`}
              >
                <span className="mp-review-postit__pin" aria-hidden />
                <p className="mp-review-postit__stars">{stars(review.rating)}</p>
                <p className="mp-review-postit__body">
                  &ldquo;{review.content ?? '리뷰 내용이 없습니다.'}&rdquo;
                </p>
                <div className="mp-review-postit__foot">
                  <span className="mp-review-postit__date">
                    {review.writeNickname != null && String(review.writeNickname).trim() !== ''
                      ? `${String(review.writeNickname).trim()}님`
                      : '익명'}
                  </span>
                  <span className="mp-review-postit__date">{formatDt(review.createdAt)}</span>
                </div>
              </article>
            )
          })}
        </div>
      )}

      <div className="grv-mood-pager" style={{ marginTop: '1rem' }}>
        <button
          type="button"
          className="mp-btn mp-btn--line"
          disabled={page <= 0 || listLoading}
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          이전
        </button>
        <span className="grv-mood-pager__meta" style={{ color: '#64748b', fontSize: '0.84rem' }}>
          {page + 1} / {totalPages} 페이지
        </span>
        <button
          type="button"
          className="mp-btn mp-btn--line"
          disabled={last || listLoading}
          onClick={() => setPage((p) => p + 1)}
        >
          다음
        </button>
      </div>
    </div>
  )
}
