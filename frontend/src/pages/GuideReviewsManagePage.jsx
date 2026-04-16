import { useCallback, useEffect, useState } from 'react'

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
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState([])
  const [last, setLast] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [listLoading, setListLoading] = useState(false)

  const loadSummary = useCallback(async (id) => {
    const res = await apiRequest(`/guides/${id}/reviews/summary`, { method: 'GET', skipAuth: true })
    const text = await res.text()
    if (!res.ok) return
    setSummary(text ? JSON.parse(text) : null)
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
        <p>불러오는 중…</p>
      </div>
    )
  }

  if (idError) {
    return (
      <div className="g-panel">
        <p className="g-error">{idError}</p>
      </div>
    )
  }

  return (
    <div className="g-panel">
      <h1>⭐ 가이드 리뷰</h1>

      {loadError && <p className="g-error">{loadError}</p>}
      {listLoading && <p style={{ marginTop: '0.75rem' }}>목록 불러오는 중…</p>}

      <section className="grv-wrap">
        <header className="grv-head">
          <div className="grv-title-chip">
            <span className="grv-title-mark" />
            <strong>✨ 가이드 리뷰</strong>
          </div>
          <article className="grv-score-card">
            <p>나의 평균 평점</p>
            <strong>
              ★ {summary?.averageRating != null ? Number(summary.averageRating).toFixed(1) : '0.0'}
            </strong>
            <span>리뷰 {summary?.reviewCount ?? 0}개</span>
          </article>
        </header>

        {!listLoading && rows.length === 0 && <p className="gm-hint">아직 등록된 리뷰가 없습니다.</p>}

        {rows.length > 0 && (
          <div className="grv-grid">
            <article className="grv-note grv-note--left">
              <p className="grv-stars">{stars(rows[0]?.rating)}</p>
              <p className="grv-content">{rows[0]?.content ?? '리뷰 내용이 없습니다.'}</p>
              <p className="grv-meta">
                - 여행자 '{rows[0]?.writeNickname ?? '익명'}' ({formatDt(rows[0]?.createdAt)})
              </p>
              <span className="grv-dot" aria-hidden />
            </article>

            {rows[1] && (
              <article className="grv-note grv-note--right">
                <span className="grv-tape" aria-hidden />
                <p className="grv-stars">{stars(rows[1]?.rating)}</p>
                <p className="grv-content">{rows[1]?.content}</p>
                <p className="grv-meta">
                  - 여행자 '{rows[1]?.writeNickname ?? '익명'}' ({formatDt(rows[1]?.createdAt)})
                </p>
              </article>
            )}

            {rows[2] && (
              <article className="grv-wide">
                <p className="grv-stars">{stars(rows[2]?.rating)}</p>
                <p className="grv-content">{rows[2]?.content}</p>
                <p className="grv-meta">
                  - 여행자 '{rows[2]?.writeNickname ?? '익명'}' ({formatDt(rows[2]?.createdAt)})
                </p>
              </article>
            )}
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
