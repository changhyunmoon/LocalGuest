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
  return '★'.repeat(n)
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
    <div className="g-panel">
      <h1>⭐ 가이드 리뷰</h1>

      {loadError && (
        <PageError message={loadError} onRetry={() => guideId != null && void loadPage(guideId, page)} />
      )}
      {listLoading && <PageLoading label="목록을 불러오는 중…" />}

      <section className="grv-wrap">
        <header className="grv-head">
          <div className="grv-title-chip">
            <span className="grv-title-mark" />
            <strong>✨ 가이드 리뷰</strong>
          </div>
          {summaryLoading ? (
            <p style={{ fontSize: '13px', color: '#9ca3af' }}>로딩 중…</p>
          ) : summary?.reviewCount === 0 || summary == null ? (
            <p style={{ fontSize: '13px', color: '#9ca3af' }}>아직 받은 리뷰가 없어요</p>
          ) : (
            <article className="grv-score-card">
              <p>나의 평균 평점</p>
              <strong>
                ★ {Number(summary.averageRating).toFixed(1)}
              </strong>
              <span>리뷰 {summary.reviewCount}개</span>
            </article>
          )}
        </header>

        {!listLoading && rows.length === 0 && (
          <PageEmpty title="등록된 리뷰가 없습니다">게스트가 남긴 후기가 있으면 여기에 표시됩니다.</PageEmpty>
        )}

        {rows.length > 0 && (
          <div className="grv-grid">
            {rows.map((review) => (
              <article key={review.id} className="grv-wide">
                <p className="grv-stars">{stars(review.rating)}</p>
                <p className="grv-content">{review.content ?? '리뷰 내용이 없습니다.'}</p>
                <p className="grv-meta">
                  - 여행자 '{review.writeNickname ?? '익명'}' ({formatDt(review.createdAt)})
                </p>
              </article>
            ))}
          </div>
        )}

        <div className="gm-actions" style={{ marginTop: '0.9rem' }}>
          <button
            type="button"
            className="gm-btn gm-btn--ghost"
            disabled={page <= 0 || listLoading}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            이전 리뷰
          </button>
          <span style={{ fontSize: '13px', color: '#6b7280', alignSelf: 'center' }}>
            {page + 1} / {totalPages} 페이지
          </span>
          <button
            type="button"
            className="gm-btn gm-btn--ghost"
            disabled={last || listLoading}
            onClick={() => setPage((p) => p + 1)}
          >
            다음 리뷰
          </button>
        </div>
      </section>
    </div>
  )
}
