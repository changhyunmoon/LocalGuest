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
    <div className="g-panel grv-mood">
      {loadError && (
        <PageError message={loadError} onRetry={() => guideId != null && void loadPage(guideId, page)} />
      )}
      {listLoading && <PageLoading label="목록을 불러오는 중…" />}

      <div className="grv-mood-hero">
        <div>
          <h1 className="grv-mood-title">게스트의 소중한 한마디 💌</h1>
          <p className="grv-mood-sub">따뜻한 리뷰는 더 많은 게스트와의 인연을 만들어줍니다.</p>
        </div>
        {summaryLoading ? (
          <p className="grv-mood-loading">로딩 중…</p>
        ) : summary == null || summary?.reviewCount === 0 ? (
          <div className="grv-mood-empty-score">아직 받은 리뷰가 없어요</div>
        ) : (
          <div className="grv-mood-stats">
            <div className="grv-mood-stat">
              <p className="grv-mood-stat__label">평균 평점</p>
              <p className="grv-mood-stat__val">
                {Number(summary.averageRating).toFixed(1)} <span className="grv-mood-stat__unit">/ 5</span>
              </p>
            </div>
            <div className="grv-mood-stat-div" aria-hidden />
            <div className="grv-mood-stat">
              <p className="grv-mood-stat__label">전체 리뷰</p>
              <p className="grv-mood-stat__val">
                {summary.reviewCount} <span className="grv-mood-stat__unit">건</span>
              </p>
            </div>
          </div>
        )}
      </div>

      {!listLoading && rows.length === 0 && (
        <PageEmpty title="등록된 리뷰가 없습니다">게스트가 남긴 후기가 있으면 벽에 붙듯 모여요.</PageEmpty>
      )}

      {rows.length > 0 && (
        <div className="grv-mood-grid">
          {rows.map((review, idx) => {
            const c = ['emerald', 'amber', 'rose', 'indigo', 'sky', 'fuchsia'][idx % 6]
            return (
              <article
                key={review.id}
                className={`grv-mood-tile grv-mood-tile--${c}`}
              >
                <span className="grv-mood-tile__pin" aria-hidden />
                <p className="grv-mood-tile__stars">{stars(review.rating)}</p>
                <p className="grv-mood-tile__quote">
                  &ldquo;{review.content ?? '리뷰 내용이 없습니다.'}&rdquo;
                </p>
                <div className="grv-mood-tile__foot">
                  <span className="grv-mood-tile__name">
                    {review.writeNickname != null && String(review.writeNickname).trim() !== ''
                      ? `${String(review.writeNickname).trim()}님`
                      : '익명'}
                  </span>
                  <span className="grv-mood-tile__date">{formatDt(review.createdAt)}</span>
                </div>
              </article>
            )
          })}
        </div>
      )}

      <div className="grv-mood-pager">
        <button
          type="button"
          className="gm-btn gm-btn--ghost"
          disabled={page <= 0 || listLoading}
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          이전
        </button>
        <span className="grv-mood-pager__meta">
          {page + 1} / {totalPages} 페이지
        </span>
        <button
          type="button"
          className="gm-btn gm-btn--ghost"
          disabled={last || listLoading}
          onClick={() => setPage((p) => p + 1)}
        >
          다음
        </button>
      </div>
    </div>
  )
}
