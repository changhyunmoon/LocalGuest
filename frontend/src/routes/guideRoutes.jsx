import { Navigate } from 'react-router-dom'

import { ProtectedRoute } from '../components/ProtectedRoute'
import { GuideDashboardLayout } from '../layouts/GuideDashboardLayout'
import { GuideApplyPage } from '../pages/GuideApplyPage'
import { GuideCourseEditorPage } from '../pages/GuideCourseEditorPage'
import { GuideFeedSchedulePage } from '../pages/GuideFeedSchedulePage'
import { GuideInboxPage } from '../pages/GuideInboxPage'
import { GuideIntroEditPage } from '../pages/GuideIntroEditPage'
import { GuideProfileEditPage } from '../pages/GuideProfileEditPage'
import { GuideReviewsManagePage } from '../pages/GuideReviewsManagePage'
import { GuideSettlementPage } from '../pages/GuideSettlementPage'
import { GuideSettingsPage } from '../pages/GuideSettingsPage'
import { GuideTermsPage } from '../pages/GuideTermsPage'

export const guideRoutes = [
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
    path: 'guide/requests/:requestId/course-editor',
    element: (
      <ProtectedRoute>
        <GuideCourseEditorPage />
      </ProtectedRoute>
    ),
  },
  {
    path: 'guide/schedules/:scheduleId/course-editor',
    element: (
      <ProtectedRoute>
        <GuideCourseEditorPage />
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
      { index: true, element: <Navigate to="profile" replace /> },
      { path: 'fees', element: <Navigate to="../settings" replace /> },
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
]

