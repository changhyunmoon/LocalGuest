import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { apiRequest } from '../api/client'

export function GuideDetailPage() {
  const { guideId } = useParams()
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const res = await apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (!res.ok) {
          throw new Error(text || '조회 실패')
        }
        const data = text ? JSON.parse(text) : null
        if (!cancelled) setDetail(data)
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

  if (loading) return <p>불러오는 중…</p>
  if (error) return <p style={{ color: '#b71c1c' }}>{error}</p>
  if (!detail?.profile) return <p>데이터가 없습니다.</p>

  const p = detail.profile

  return (
    <div>
      <p>
        <Link to="/guides">← 목록</Link>
      </p>
      <h1 style={{ marginTop: 0 }}>{p.nickname}</h1>
      <p style={{ color: '#555' }}>
        {p.region} · {p.language} · {p.pricePerHour != null ? `${p.pricePerHour} / 시간` : ''}
      </p>
      {p.bio && <p style={{ lineHeight: 1.6 }}>{p.bio}</p>}
      <p style={{ fontSize: '0.85rem', color: '#777' }}>
        승인: {String(p.isApproved)} · 활성: {String(p.isActive)}
      </p>
    </div>
  )
}
