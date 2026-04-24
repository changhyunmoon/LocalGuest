import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'

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

export function MypageReviewDetailPage() {
  const { reviewId } = useParams()
  const navigate = useNavigate()
  const [row, setRow] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const id = Number(reviewId)
    if (!Number.isFinite(id)) {
      setError('잘못된 리뷰 ID입니다.')
      setRow(null)
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await apiRequest(`/reviews/me/${id}`, { method: 'GET' })
      const text = await res.text()
      if (!res.ok) throw new Error(parseApiErrorMessage(text))
      const data = text ? JSON.parse(text) : null
      setRow(data && typeof data === 'object' ? data : null)
    } catch (e) {
      setRow(null)
      setError(e instanceof Error ? e.message : '불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [reviewId])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) return <PageLoading />
  if (error || !row) {
    return (
      <div className="mp-member">
        <PageError message={error || '리뷰를 찾을 수 없습니다.'} onRetry={() => void load()} />
        <p style={{ marginTop: '1rem' }}>
          <Link to="/mypage/reviews" className="mp-btn mp-btn--line">
            내 리뷰 목록
          </Link>
        </p>
      </div>
    )
  }

  const mid = row.matchRequestId != null ? Number(row.matchRequestId) : null
  const gid = row.guideId != null ? Number(row.guideId) : null

  return (
    <div className="mp-member">
      <button type="button" className="mp-back-chip" onClick={() => navigate(-1)} aria-label="이전으로">
        <span className="mp-back-chip__icon" aria-hidden>
          ←
        </span>
        돌아가기
      </button>

      <h1>🌟 리뷰 상세</h1>
      <p className="sub">내가 남긴 후기입니다. 수정은 불가이며 삭제는 목록에서만 가능해요.</p>

      <article className="mp-review-detail-card">
        <p className="mp-review-detail-stars">{stars(row.rating)}</p>
        <p className="mp-review-detail-body">&ldquo;{row.content}&rdquo;</p>
        <p className="mp-review-detail-meta">{formatDt(row.createdAt)}</p>
        <div className="mp-review-detail-links">
          {Number.isFinite(mid) ? (
            <Link to={`/mypage/scrapbook/${mid}`} className="mp-btn mp-btn--line">
              스크랩북 여행기 보기
            </Link>
          ) : null}
          {Number.isFinite(gid) ? (
            <Link to={`/guides/${gid}`} className="mp-btn mp-btn--line">
              가이드 프로필
            </Link>
          ) : null}
        </div>
      </article>
    </div>
  )
}
