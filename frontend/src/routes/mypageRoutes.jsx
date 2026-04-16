import { ProtectedRoute } from '../components/ProtectedRoute'
import { MypageShell } from '../layouts/MypageShell'
import { MypageItineraryPage } from '../pages/MypageItineraryPage'
import { MypagePaymentsPage } from '../pages/MypagePaymentsPage'
import { MypagePlaceholder } from '../pages/MypagePlaceholder'
import { MypagePrivacyPage } from '../pages/MypagePrivacyPage'
import { MypageScrapbookPage } from '../pages/MypageScrapbookPage'
import { MypageTourPage } from '../pages/MypageTourPage'
import { MyPageOverview } from '../pages/MyPageOverview'

export const mypageRoutes = [
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
]

