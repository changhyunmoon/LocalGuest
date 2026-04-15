import { useEffect, useState } from 'react'

import { apiRequest } from '../api/client.js'
import { resolveGuideId } from '../lib/guideId.js'
import { useAuth } from '../context/useAuth.js'

/**
 * 가이드 마이페이지에서 사용할 guideId (localStorage → 매칭 목록 추론).
 * @returns {{ guideId: number | null, loading: boolean, error: string, reload: () => void }}
 */
export function useResolvedGuideId() {
  const { isGuide } = useAuth()
  const [guideId, setGuideId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!isGuide) {
      setGuideId(null)
      setError('가이드(GUIDE) 계정에서만 사용할 수 있습니다. 가이드 신청 후 JWT에 역할이 반영되도록 다시 로그인해 주세요.')
      setLoading(false)
      return
    }

    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const id = await resolveGuideId(apiRequest)
        if (cancelled) return
        if (!id) {
          setGuideId(null)
          setError(
            '가이드 프로필 ID를 찾을 수 없습니다. 가이드 신청 직후 저장된 ID가 없으면, 예약 요청이 한 건이라도 있어야 자동 추론됩니다.',
          )
          return
        }
        setGuideId(id)
      } catch {
        if (!cancelled) setError('가이드 ID를 불러오지 못했습니다.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [isGuide, tick])

  const reload = () => setTick((t) => t + 1)

  return { guideId, loading, error, reload }
}
