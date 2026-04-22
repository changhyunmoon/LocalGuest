import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useGuidePendingRequests } from '../context/GuidePendingRequestsProvider.jsx'
import { useAuth } from '../context/useAuth.js'
import { useResolvedGuideId } from '../hooks/useResolvedGuideId.js'

import './GuideDashboardLayout.css'

const NAV = [
  { to: '/guide/mypage/profile', label: '📄 프로필 · 소개' },
  { to: '/guide/mypage/feed-schedule?tab=feed', label: '💼 피드 등록', tab: 'feed' },
  { to: '/guide/mypage/feed-schedule?tab=schedule', label: '📅 스케줄 관리', tab: 'schedule' },
  { to: '/guide/mypage/settlement', label: '💰 정산 예정 금액' },
  { to: '/guide/mypage/settings', label: '⚙️ 가이드 설정' },
  { to: '/guide/mypage/reviews', label: '🌟 가이드 리뷰' },
  { to: '/guide/inbox', label: '🤝 매칭 요청', external: true },
]

export function GuideDashboardLayout() {
  const { email, logout, isGuide } = useAuth()
  const { pendingCount } = useGuidePendingRequests()
  const { pathname, search } = useLocation()
  const { guideId } = useResolvedGuideId()
  const displayName = email ? email.split('@')[0] : '홍길동'
  const [guideStyleRaw, setGuideStyleRaw] = useState('')
  const [guideProfile, setGuideProfile] = useState(null)

  useEffect(() => {
    if (!guideId) return
    let cancelled = false
    ;(async () => {
      try {
        const res = await apiRequest(`/guides/${guideId}`, { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (!res.ok || cancelled) return
        const data = text ? JSON.parse(text) : null
        if (!cancelled) {
          setGuideProfile(data)
          setGuideStyleRaw(String(data?.guideStyle ?? '').trim())
        }
      } catch {
        /* ignore guide style fetch errors in sidebar */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [guideId])

  const guideStyleTags = useMemo(() => {
    if (!guideStyleRaw) return []
    return [...new Set(
      guideStyleRaw
        .split(/[,/|]+/g)
        .map((s) => s.trim())
        .filter(Boolean),
    )].slice(0, 3)
  }, [guideStyleRaw])

  if (!isGuide) {
    return <Navigate to="/mypage" replace />
  }

  return (
    <div className="g-dash">
      <aside className="g-dash-side">
        <div className="g-dash-profile">
          <div
            className="g-dash-avatar"
            aria-hidden
            style={guideProfile?.profileImage ? { backgroundImage: `url(${guideProfile.profileImage})` } : undefined}
          />
          <strong className="g-dash-name">{displayName}</strong>
          <span className="g-dash-email">{email ?? ''}</span>
          {guideStyleTags.length > 0 && (
            <div className="g-dash-style-tags" aria-label="가이드 스타일 태그">
              {guideStyleTags.map((tag) => (
                <span key={tag} className="g-dash-style-tag">
                  #{tag}
                </span>
              ))}
            </div>
          )}
        </div>
        <nav className="g-dash-nav" aria-label="가이드 메뉴">
          {NAV.map((item) => {
            if (item.tab) {
              const currentTab = new URLSearchParams(search).get('tab') || 'feed'
              const on = pathname === '/guide/mypage/feed-schedule' && currentTab === item.tab
              return (
                <Link key={item.to} to={item.to} className={`g-dash-link${on ? ' is-active' : ''}`}>
                  {item.label}
                </Link>
              )
            }
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => `g-dash-link${isActive ? ' is-active' : ''}`}
                aria-label={
                  item.to === '/guide/inbox' && pendingCount > 0
                    ? `매칭 요청, 처리할 요청 ${pendingCount}건`
                    : undefined
                }
              >
                <span className="g-dash-link-inner">
                  <span className="g-dash-link-text">{item.label}</span>
                  {item.to === '/guide/inbox' && pendingCount > 0 && (
                    <span className="g-dash-nav-badge" aria-hidden="true">
                      {pendingCount > 99 ? '99+' : pendingCount}
                    </span>
                  )}
                </span>
              </NavLink>
            )
          })}
        </nav>
        <div className="g-dash-sep" />
        <button type="button" className="g-dash-logout" onClick={() => void logout()}>
          로그아웃
        </button>
      </aside>
      <div className="g-dash-main">
        <Outlet />
      </div>
    </div>
  )
}
