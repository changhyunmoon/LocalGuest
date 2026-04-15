import { Link, Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

import './GuideDashboardLayout.css'

const NAV = [
  { to: '/guide/mypage/profile', label: '📄 프로필 등록' },
  { to: '/guide/mypage/intro', label: '📝 소개글 작성' },
  { to: '/guide/mypage/feed-schedule?tab=feed', label: '💼 피드 등록', tab: 'feed' },
  { to: '/guide/mypage/feed-schedule?tab=schedule', label: '📅 스케줄 관리', tab: 'schedule' },
  { to: '/guide/mypage/settlement', label: '💰 정산 예정 금액' },
  { to: '/guide/mypage/settings', label: '⚙️ 가이드 설정' },
  { to: '/guide/mypage/reviews', label: '⭐ 가이드 리뷰' },
  { to: '/guide/inbox', label: '🤝 매칭 수락/거절', external: true },
]

export function GuideDashboardLayout() {
  const { email, logout, isGuide } = useAuth()
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const displayName = email ? email.split('@')[0] : '홍길동'

  if (!isGuide) {
    return <Navigate to="/mypage" replace />
  }

  return (
    <div className="g-dash">
      <aside className="g-dash-side">
        <div className="g-dash-profile">
          <div className="g-dash-avatar" aria-hidden />
          <strong className="g-dash-name">{displayName}</strong>
          <span className="g-dash-email">{email ?? ''}</span>
          <button type="button" className="g-dash-profile-btn" onClick={() => navigate('/guide/mypage/profile')}>
            프로필 수정
          </button>
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
              >
                {item.label}
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
