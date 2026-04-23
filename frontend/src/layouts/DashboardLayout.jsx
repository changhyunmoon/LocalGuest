import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useAuth } from '../context/useAuth.js'
import { getGuestDisplayName, loadTravelTags } from '../lib/guestMypagePrefs.js'

import './DashboardLayout.css'

const guestItems = [
  { to: '/mypage/scrapbook', label: '📒 나의 여행 기록(스크랩북)' },
  { to: '/upcoming-trips', label: '📆 앞으로의 여행 일정' },
  { to: '/mypage/payments', label: '💳 결제 내역' },
  { to: '/mypage/privacy', label: '⚙️ 개인정보 설정' },
  { to: '/mypage/tour', label: '🔁 투어 연장/환불 관리' },
  { to: '/mypage/reviews', label: '🌟 내 리뷰' },
]

export function DashboardLayout() {
  const { email, token, isGuide, logout } = useAuth()
  const [prefsTick, setPrefsTick] = useState(0)
  const [profileImageUrl, setProfileImageUrl] = useState(null)

  useEffect(() => {
    const onPrefs = () => setPrefsTick((t) => t + 1)
    window.addEventListener('localguest_mypage_prefs_updated', onPrefs)
    return () => window.removeEventListener('localguest_mypage_prefs_updated', onPrefs)
  }, [])

  const displayName = useMemo(() => getGuestDisplayName(email), [email, prefsTick])
  const travelTags = useMemo(() => loadTravelTags(), [prefsTick])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!token) {
        if (!cancelled) setProfileImageUrl(null)
        return
      }
      try {
        const res = await apiRequest('/members/me/profile', { method: 'GET' })
        const text = await res.text()
        if (!res.ok) return
        const data = text ? JSON.parse(text) : {}
        if (!cancelled) setProfileImageUrl(data?.profileImageUrl ? String(data.profileImageUrl) : null)
      } catch {
        if (!cancelled) {
          setProfileImageUrl(null)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <div className="dash">
      <aside className="dash-side">
        <div className="dash-profile">
          <div
            className={`dash-avatar${profileImageUrl ? '' : ' is-empty'}`}
            aria-hidden
            style={profileImageUrl ? { backgroundImage: `url(${profileImageUrl})` } : undefined}
          />
          <strong className="dash-name">{displayName}</strong>
          <span className="dash-email">{email ?? ''}</span>
          <div className="dash-local-note" role="note" aria-label="여행 성향 태그">
            {travelTags.length > 0 ? (
              <div className="dash-tag-list">
                {travelTags.map((tag) => (
                  <span key={tag} className="dash-tag-chip">
                    #{tag}
                  </span>
                ))}
              </div>
            ) : (
              <span className="dash-tag-empty">성향 태그 없음</span>
            )}
          </div>
        </div>

        <nav className="dash-nav" aria-label="마이페이지 메뉴">
          {guestItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end ?? false}
              className={({ isActive }) => (isActive ? 'dash-nav-link is-active' : 'dash-nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="dash-footer">
          {!isGuide && (
            <Link to="/guide/register" className="dash-extra dash-extra--cta">
              📝 가이드 신청하기
            </Link>
          )}
          <button type="button" className="dash-logout" onClick={() => void logout()}>
            🚪 로그아웃
          </button>
        </div>
      </aside>

      <section className="dash-content">
        <Outlet />
      </section>
    </div>
  )
}
