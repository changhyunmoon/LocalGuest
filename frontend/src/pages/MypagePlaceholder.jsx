import { Link } from 'react-router-dom'

export function MypagePlaceholder({ title, description }) {
  return (
    <div>
      <h2 style={{ marginTop: 0 }}>{title}</h2>
      <p style={{ color: '#555', lineHeight: 1.6 }}>{description}</p>
      <p>
        <Link to="/mypage">← 마이페이지 개요</Link>
      </p>
    </div>
  )
}
