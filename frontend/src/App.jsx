import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'

import { ProtectedRoute } from './components/ProtectedRoute'
import { AppLayout } from './layouts/AppLayout'
import { MypageShell } from './layouts/MypageShell'
import { GuideDashboardLayout } from './layouts/GuideDashboardLayout'
import { GuideApplyPage } from './pages/GuideApplyPage'
import { GuideTermsPage } from './pages/GuideTermsPage'
import { GuideDetailPage } from './pages/GuideDetailPage'
import { GuideFeedTourPage } from './pages/GuideFeedTourPage'
import { GuideProposalPage } from './pages/GuideProposalPage'
import { GuideMatchOptionsPage } from './pages/GuideMatchOptionsPage'
import { GuideMatchedCoursePage } from './pages/GuideMatchedCoursePage'
import { GuideFeedSchedulePage } from './pages/GuideFeedSchedulePage'
import { GuideFeesPage } from './pages/GuideFeesPage'
import { GuideInboxPage } from './pages/GuideInboxPage'
import { GuideIntroEditPage } from './pages/GuideIntroEditPage'
import { GuideProfileEditPage } from './pages/GuideProfileEditPage'
import { GuideReviewsManagePage } from './pages/GuideReviewsManagePage'
import { GuideSettlementPage } from './pages/GuideSettlementPage'
import { GuideSettingsPage } from './pages/GuideSettingsPage'
import { AiQuickSearchPage } from './pages/AiQuickSearchPage'
import { MessagesPage } from './pages/MessagesPage'
import { GuideListPage } from './pages/GuideListPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { MypageItineraryPage } from './pages/MypageItineraryPage'
import { MypagePaymentsPage } from './pages/MypagePaymentsPage'
import { MypagePlaceholder } from './pages/MypagePlaceholder'
import { MypagePrivacyPage } from './pages/MypagePrivacyPage'
import { MypageScrapbookPage } from './pages/MypageScrapbookPage'
import { MypageTourPage } from './pages/MypageTourPage'
import { MyPageOverview } from './pages/MyPageOverview'
import { PaymentKakaoStubPage } from './pages/PaymentKakaoStubPage'
import { SignupPage } from './pages/SignupPage'

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'auth/login', element: <LoginPage /> },
      { path: 'auth/signup', element: <SignupPage /> },
      { path: 'messages', element: <MessagesPage /> },
      { path: 'ai-search', element: <AiQuickSearchPage /> },
      { path: 'ai-quick', element: <Navigate to="/ai-search" replace /> },
      { path: 'guides', element: <GuideListPage /> },
      { path: 'guides/:guideId/proposal', element: <GuideProposalPage /> },
      { path: 'guides/:guideId/match', element: <GuideMatchOptionsPage /> },
      { path: 'guides/:guideId/match/complete', element: <GuideMatchedCoursePage /> },
      { path: 'guides/:guideId/feeds/:feedId', element: <GuideFeedTourPage /> },
      { path: 'guides/:guideId', element: <GuideDetailPage /> },
      { path: 'pay/kakao-stub', element: <PaymentKakaoStubPage /> },
      {
        path: 'mypage',
        element: (
          <ProtectedRoute>
            <MypageShell />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <MyPageOverview /> },
          {
            path: 'profile',
            element: (
              <MypagePlaceholder
                title="프로필"
                description="회원 프로필 조회·수정 API가 추가되면 이 화면에서 연동합니다."
              />
            ),
          },
          {
            path: 'scrapbook',
            element: <MypageScrapbookPage />,
          },
          {
            path: 'itinerary',
            element: <MypageItineraryPage />,
          },
          {
            path: 'payments',
            element: <MypagePaymentsPage />,
          },
          {
            path: 'privacy',
            element: <MypagePrivacyPage />,
          },
          {
            path: 'tour',
            element: <MypageTourPage />,
          },
          {
            path: 'reviews',
            element: <MypagePlaceholder title="내 리뷰" description="/api/reviews 기반 목록·작성 화면 연동 예정." />,
          },
        ],
      },
      {
        path: 'guide/register',
        element: (
          <ProtectedRoute>
            <GuideTermsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'guide/apply',
        element: (
          <ProtectedRoute>
            <GuideApplyPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'guide/inbox',
        element: (
          <ProtectedRoute>
            <GuideInboxPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'guide/mypage',
        element: (
          <ProtectedRoute>
            <GuideDashboardLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <Navigate to="fees" replace /> },
          { path: 'fees', element: <GuideFeesPage /> },
          { path: 'settlement', element: <GuideSettlementPage /> },
          {
            path: 'profile',
            element: <GuideProfileEditPage />,
          },
          {
            path: 'intro',
            element: <GuideIntroEditPage />,
          },
          {
            path: 'feed-schedule',
            element: <GuideFeedSchedulePage />,
          },
          {
            path: 'settings',
            element: <GuideSettingsPage />,
          },
          {
            path: 'reviews',
            element: <GuideReviewsManagePage />,
          },
        ],
      },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
