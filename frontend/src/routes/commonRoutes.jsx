import { Navigate } from 'react-router-dom'

import { AiQuickSearchPage } from '../pages/AiQuickSearchPage'
import { GuideDetailPage } from '../pages/GuideDetailPage'
import { GuideFeedTourPage } from '../pages/GuideFeedTourPage'
import { GuideListPage } from '../pages/GuideListPage'
import { GuideMatchedCoursePage } from '../pages/GuideMatchedCoursePage'
import { GuideMatchOptionsPage } from '../pages/GuideMatchOptionsPage'
import { GuideProposalPage } from '../pages/GuideProposalPage'
import { HomePage } from '../pages/HomePage'
import { ChatRoomCreatePage } from '../pages/ChatRoomCreatePage'
import { MessagesPage } from '../pages/MessagesPage'
import { PaymentKakaoStubPage } from '../pages/PaymentKakaoStubPage'

export const commonRoutes = [
  { index: true, element: <HomePage /> },
  { path: 'messages', element: <MessagesPage /> },
  { path: 'messages/new', element: <ChatRoomCreatePage /> },
  { path: 'ai-search', element: <AiQuickSearchPage /> },
  { path: 'ai-quick', element: <Navigate to="/ai-search" replace /> },
  { path: 'guides', element: <GuideListPage /> },
  { path: 'guides/:guideId/proposal', element: <GuideProposalPage /> },
  { path: 'guides/:guideId/match', element: <GuideMatchOptionsPage /> },
  { path: 'guides/:guideId/match/complete', element: <GuideMatchedCoursePage /> },
  { path: 'guides/:guideId/feeds/:feedId', element: <GuideFeedTourPage /> },
  { path: 'guides/:guideId', element: <GuideDetailPage /> },
  { path: 'pay/kakao-stub', element: <PaymentKakaoStubPage /> },
]

