import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

import { DashboardLayout } from './DashboardLayout.jsx'

/**
 * 여행자 전용 /mypage/* 트리. 가이드(GUIDE)는 가이드 대시보드로 보냅니다.
 */
export function MypageShell() {
  const { isGuide } = useAuth()
  if (isGuide) {
    return <Navigate to="/guide/mypage/profile" replace />
  }
  return (
    <DashboardLayout>
      <Outlet />
    </DashboardLayout>
  )
}
