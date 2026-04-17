import { ProtectedRoute } from '../components/ProtectedRoute'
import { MypageShell } from '../layouts/MypageShell'
import { MypageItineraryPage } from '../pages/MypageItineraryPage'
import { MypagePaymentsPage } from '../pages/MypagePaymentsPage'
import { MypagePrivacyPage } from '../pages/MypagePrivacyPage'
import { MypageProfilePage } from '../pages/MypageProfilePage'
import { MypageReviewsPage } from '../pages/MypageReviewsPage'
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
      { path: 'profile', element: <MypageProfilePage /> },
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
      { path: 'reviews', element: <MypageReviewsPage /> },
    ],
  },
]

