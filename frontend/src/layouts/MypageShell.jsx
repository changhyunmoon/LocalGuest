import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

import { DashboardLayout } from './DashboardLayout.jsx'

/**
 * 여행자 전용 /mypage/* 트리. 가이드(GUIDE)는 홈으로 보냅니다(JWT 역할과 경로 불일치 정리).
 */
export function MypageShell() {
  const { isGuide } = useAuth()
  if (isGuide) {
    return <Navigate to="/" replace />
  }
  return (
    <DashboardLayout>
      <Outlet />
    </DashboardLayout>
  )
}
