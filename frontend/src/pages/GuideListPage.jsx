import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'

import './GuideListPage.css'

export function GuideListPage() {
  const [searchParams] = useSearchParams()
  const regionFilter = (searchParams.get('region') || '').trim()

  const [items, setItems] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const filtered = useMemo(() => {
    if (!regionFilter) return items
    const q = regionFilter.toLowerCase()
    return items.filter((g) => (g.region || '').toLowerCase().includes(q))
  }, [items, regionFilter])

  const loadGuides = useCallback(async () => {
    const res = await apiRequest('/guides', { method: 'GET', skipAuth: true })
    const text = await res.text()
    if (!res.ok) {
      throw new Error(text || '목록을 불러오지 못했습니다.')
    }
    const data = text ? JSON.parse(text) : []
    setItems(Array.isArray(data) ? data : [])
  }, [])

  const refetch = useCallback(async () => {
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

  return (
    <div className="guide-list">
      <h1 className="guide-list-title">가이드 목록</h1>
      <p className="guide-list-hint">승인·활성 가이드만 표시됩니다 (`GET /api/guides`).</p>

      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void refetch()} />}

      {!loading && !error && items.length === 0 && (
        <PageEmpty title="표시할 가이드가 없습니다">승인된 가이드가 등록되면 여기에 나타납니다.</PageEmpty>
      )}
      {!loading && !error && items.length > 0 && filtered.length === 0 && regionFilter && (
        <PageEmpty title="검색 결과가 없습니다">
          「{regionFilter}」 지역에 해당하는 가이드가 없습니다. 필터를 바꿔 보세요.
        </PageEmpty>
      )}

      <ul className="guide-grid">
        {filtered.map((g) => (
          <li key={g.guideId} className="guide-card">
            <Link to={`/guides/${g.guideId}`}>
              <strong>{g.nickname}</strong>
              <span>{g.region}</span>
              <span>{g.language}</span>
              <span className="meta">{g.pricePerHour != null ? `${g.pricePerHour}원/시간` : ''}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
