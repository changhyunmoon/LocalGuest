import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { createChatRoom } from '../api/chat.js'
import { PageError } from '../components/PageStates.jsx'
import { useAuth } from '../context/useAuth.js'

import './ChatRoomCreatePage.css'

function parseEmails(value) {
  return value
    .split(/[\s,;]+/)
    .map((email) => email.trim())
    .filter(Boolean)
}

function isEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

export function ChatRoomCreatePage() {
  const navigate = useNavigate()
  const { isAuthenticated, email } = useAuth()

  const [title, setTitle] = useState('')
  const [emailsText, setEmailsText] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const participantEmails = useMemo(() => {
    const emails = parseEmails(emailsText)
    return [...new Set(emails.map((item) => item.toLowerCase()))]
  }, [emailsText])

  const invalidEmails = useMemo(
    () => participantEmails.filter((item) => !isEmail(item)),
    [participantEmails],
  )

  const onSubmit = async (e) => {
    e.preventDefault()

    const cleanTitle = title.trim()
    if (!cleanTitle) {
      setError('채팅방 제목을 입력해 주세요.')
      return
    }

    if (participantEmails.length === 0) {
      setError('초대할 사람의 이메일을 1개 이상 입력해 주세요.')
      return
    }

    if (invalidEmails.length > 0) {
      setError(`이메일 형식이 올바르지 않습니다: ${invalidEmails.join(', ')}`)
      return
    }

    if (email && participantEmails.includes(email.toLowerCase())) {
      setError('내 이메일은 초대 목록에 넣지 않아도 됩니다. 방장은 자동으로 참여자로 추가됩니다.')
      return
    }

    setSubmitting(true)
    setError('')

    try {
      const room = await createChatRoom({
        title: cleanTitle,
        participantEmails,
      })

      if (!room?.roomId) {
        throw new Error('생성된 채팅방 정보를 확인하지 못했습니다.')
      }

      navigate(`/messages?roomId=${encodeURIComponent(room.roomId)}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '채팅방을 만들지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="chat-create-empty">
        <h1>채팅방 생성</h1>
        <p>채팅방은 로그인 후 만들 수 있습니다.</p>
        <Link to="/auth/login" state={{ returnTo: '/messages/new', hint: '로그인하면 채팅방을 만들 수 있습니다.' }}>
          로그인하기
        </Link>
      </div>
    )
  }

  return (
    <main className="chat-create">
      <div className="chat-create-head">
        <div>
          <p className="chat-create-kicker">New chat room</p>
          <h1>채팅방 생성</h1>
        </div>
        <Link className="chat-create-back" to="/messages">
          목록으로
        </Link>
      </div>

      <form className="chat-create-card" onSubmit={(e) => void onSubmit(e)}>
        {error ? <PageError message={error} className="page-state--tight" /> : null}

        <label className="chat-create-label" htmlFor="chat-title">
          채팅방 제목
        </label>
        <input
          id="chat-title"
          className="chat-create-input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="예: 제주 여행 준비방"
          maxLength={80}
          autoFocus
        />

        <label className="chat-create-label" htmlFor="chat-emails">
          초대할 사람 이메일
        </label>
        <textarea
          id="chat-emails"
          className="chat-create-textarea"
          value={emailsText}
          onChange={(e) => setEmailsText(e.target.value)}
          placeholder="example1@email.com&#10;example2@email.com"
        />

        <div className="chat-create-preview">
          <strong>초대 대상 {participantEmails.length}명</strong>
          <span>이메일은 줄바꿈, 쉼표, 공백으로 구분할 수 있습니다.</span>
        </div>

        {participantEmails.length > 0 ? (
          <ul className="chat-create-chips" aria-label="초대 이메일 목록">
            {participantEmails.map((item) => (
              <li key={item} className={isEmail(item) ? '' : 'is-invalid'}>
                {item}
              </li>
            ))}
          </ul>
        ) : null}

        <div className="chat-create-actions">
          <Link className="chat-create-cancel" to="/messages">
            취소
          </Link>
          <button type="submit" disabled={submitting}>
            {submitting ? '생성 중...' : '채팅방 생성'}
          </button>
        </div>
      </form>
    </main>
  )
}
