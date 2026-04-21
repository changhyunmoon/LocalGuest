import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import { apiRequest } from '../api/client.js'
import { useAuth } from './useAuth.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

const GuidePendingContext = createContext({
  pendingCount: 0,
  refresh: async () => {},
})

const POLL_MS = 45_000

function countPendingGuideRequests(list) {
  let n = 0
  for (const r of Array.isArray(list) ? list : []) {
    if (String(r?.status ?? '').toUpperCase() === 'PENDING') n += 1
  }
  return n
}

export function GuidePendingRequestsProvider({ children }) {
  const { isGuide, isAuthenticated } = useAuth()
  const [pendingCount, setPendingCount] = useState(0)
  const [toast, setToast] = useState(null)
  const prevAfterSyncRef = useRef(null)
  const hasSyncedOnceRef = useRef(false)
  const toastTimerRef = useRef(null)

  const refresh = useCallback(async () => {
    if (!isGuide || !isAuthenticated) {
      setPendingCount(0)
      prevAfterSyncRef.current = null
      hasSyncedOnceRef.current = false
      return
    }
    try {
      const data = await fetchGuideMatchRequests(apiRequest)
      const n = countPendingGuideRequests(data)
      const prev = prevAfterSyncRef.current
      if (hasSyncedOnceRef.current && prev != null && n > prev) {
        if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current)
        setToast('새 매칭 요청이 도착했어요. 매칭 요청 메뉴에서 확인해 주세요.')
        toastTimerRef.current = window.setTimeout(() => {
          setToast(null)
          toastTimerRef.current = null
        }, 5000)
      }
      setPendingCount(n)
      hasSyncedOnceRef.current = true
      prevAfterSyncRef.current = n
    } catch {
      // 목록 조회 실패 시 이전 카운트 유지
    }
  }, [isAuthenticated, isGuide])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!isGuide || !isAuthenticated) return undefined
    const id = window.setInterval(() => {
      void refresh()
    }, POLL_MS)
    return () => window.clearInterval(id)
  }, [isAuthenticated, isGuide, refresh])

  useEffect(() => {
    if (!isGuide || !isAuthenticated) return undefined
    const onFocus = () => {
      void refresh()
    }
    const onVis = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onVis)
    return () => {
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('visibilitychange', onVis)
    }
  }, [isAuthenticated, isGuide, refresh])

  useEffect(
    () => () => {
      if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current)
    },
    [],
  )

  const value = useMemo(() => ({ pendingCount, refresh }), [pendingCount, refresh])

  return (
    <GuidePendingContext.Provider value={value}>
      {children}
      {toast && (
        <div className="shell-match-toast" role="status" aria-live="polite">
          <span className="shell-match-toast__dot" aria-hidden="true" />
          <span className="shell-match-toast__msg">{toast}</span>
          <button type="button" className="shell-match-toast__close" onClick={() => setToast(null)} aria-label="알림 닫기">
            ×
          </button>
        </div>
      )}
    </GuidePendingContext.Provider>
  )
}

export function useGuidePendingRequests() {
  return useContext(GuidePendingContext)
}
