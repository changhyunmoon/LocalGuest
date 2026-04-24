import { Navigate } from 'react-router-dom'

import { ProtectedRoute } from '../components/ProtectedRoute'
import { MypageShell } from '../layouts/MypageShell'
import { GuestUpcomingTripsPage } from '../pages/GuestUpcomingTripsPage'
import { MypagePaymentsPage } from '../pages/MypagePaymentsPage'
import { MypagePrivacyPage } from '../pages/MypagePrivacyPage'
import { MypageReviewDetailPage } from '../pages/MypageReviewDetailPage'
import { MypageReviewsPage } from '../pages/MypageReviewsPage'
import { MypageScrapbookPage } from '../pages/MypageScrapbookPage'
import { MypageScrapbookTicketDetailPage } from '../pages/MypageScrapbookTicketDetailPage'
import { MypageTourPage } from '../pages/MypageTourPage'

export const mypageRoutes = [
  {
    path: 'upcoming-trips',
    element: (
      <ProtectedRoute>
        <Navigate to="/mypage/itinerary" replace />
      </ProtectedRoute>
    ),
  },
  {
    path: 'mypage',
    element: (
      <ProtectedRoute>
        <MypageShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="scrapbook" replace /> },
      {
        path: 'scrapbook',
        element: <MypageScrapbookPage />,
      },
      {
        path: 'scrapbook/:requestId',
        element: <MypageScrapbookTicketDetailPage />,
      },
      {
        path: 'itinerary',
        element: <GuestUpcomingTripsPage />,
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
      { path: 'reviews/:reviewId', element: <MypageReviewDetailPage /> },
      { path: 'reviews', element: <MypageReviewsPage /> },
    ],
  },
]

