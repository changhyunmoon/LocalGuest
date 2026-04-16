import { Link } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

export function MyPageOverview() {
  const { email, isGuide } = useAuth()

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>대시보드</h2>
      <p style={{ color: '#555', lineHeight: 1.5 }}>
        로그인 계정: <strong>{email}</strong>
        <br />
        JWT 역할: <strong>{isGuide ? 'GUIDE' : 'GUEST'}</strong> — 가이드 신청 후에는 <strong>재로그인</strong>해서
        로그인 유형을 GUIDE로 선택하면 가이드 토큰이 발급됩니다.
      </p>
      <ul style={{ paddingLeft: '1.2rem', color: '#444', lineHeight: 1.6 }}>
        <li>
          <Link to="/mypage/scrapbook">나의 여행 기록</Link> · <code>GET /api/matching/requests/guest/list</code>
        </li>
        <li>
          <Link to="/mypage/itinerary">앞으로의 여행 일정</Link> · 동일 API (진행·예정)
        </li>
        <li>
          <Link to="/mypage/profile">프로필</Link> · 조회/수정 API는 추후 연동
        </li>
        <li>
          <Link to="/mypage/payments">결제 내역</Link> · <code>GET /api/matching/payments/guest/list</code>
        </li>
        <li>
          <Link to="/mypage/tour">연장·환불</Link> · <code>/api/matching/extensions</code>, <code>…/payments/refunds</code>
        </li>
        <li>
          <Link to="/mypage/reviews">내 리뷰</Link> · <code>/api/reviews</code>
        </li>
      </ul>
      {!isGuide && (
        <p style={{ marginTop: '1.25rem' }}>
          <Link to="/guide/register" className="button" style={{ display: 'inline-flex' }}>
            가이드 신청하기 (약관 → <code>POST /api/guides</code>)
          </Link>
        </p>
      )}
      {isGuide && (
        <p style={{ marginTop: '1.25rem', color: '#0d47a1' }}>
          가이드 메뉴: 상단 <strong>가이드 예약함</strong>에서 <code>GET /api/matching/requests/guide/list</code> 결과를
          확인할 수 있습니다.
        </p>
      )}
    </div>
  )
}
