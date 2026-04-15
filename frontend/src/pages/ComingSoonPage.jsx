import { Link, useLocation } from 'react-router-dom'

import './ComingSoonPage.css'

const titles = {
  '/messages': '메시지',
}

export function ComingSoonPage() {
  const { pathname } = useLocation()
  const title = titles[pathname] ?? '서비스'

  return (
    <div className="soon">
      <h1>{title}</h1>
      <p>백엔드·채팅·AI 연동 전까지 이 화면은 안내용입니다.</p>
      <Link to="/">홈으로</Link>
    </div>
  )
}
