import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { fetchChatRooms } from '../api/chat.js'
import { useAuth } from '../context/useAuth.js'

import './MessagesPage.css'

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

function formatDay(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

function nicknameFromEmail(email) {
  if (!email) return '나'
  const head = String(email).split('@')[0]
  return head || '나'
}

/**
 * @typedef {{ roomId: string, title: string, ownerEmail: string, participantCount: number, lastMessage: string | null, lastMessageAt: string | null, unreadCount: number }} ChatRoom
 * @typedef {{ id: string, roomId: string, senderEmail: string, senderNickname: string, message: string, unreadCount: number, createdAt: string }} ChatMessage
 */

export function MessagesPage() {
  const { isAuthenticated, email, token, claims } = useAuth()
  const [searchParams] = useSearchParams()
  const chatBodyRef = useRef(null)
  const stompRef = useRef(/** @type {Client | null} */ (null))
  const sseRef = useRef(/** @type {EventSource | null} */ (null))

  const [rooms, setRooms] = useState(/** @type {ChatRoom[]} */ ([]))
  const [selectedRoomId, setSelectedRoomId] = useState('')
  const [messages, setMessages] = useState(/** @type {ChatMessage[]} */ ([]))
  const [input, setInput] = useState('')
  const [loadingRooms, setLoadingRooms] = useState(false)
  const [loadingMessages, setLoadingMessages] = useState(false)
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const readUpdateTimerRef = useRef(/** @type {ReturnType<typeof setTimeout> | null} */ (null))

  const myNickname = useMemo(() => {
    const fromClaims =
      claims?.nickname || claims?.name || claims?.nickName || claims?.username
    return String(fromClaims || nicknameFromEmail(email))
  }, [claims, email])

  const selectedRoom = useMemo(
    () => rooms.find((r) => r.roomId === selectedRoomId) ?? null,
    [rooms, selectedRoomId],
  )

  useEffect(() => {
    if (!isAuthenticated || !token) return

    // SSE: NEW_MESSAGE/NEW_ROOM 실시간 수신(방 목록 unreadCount 갱신용)
    sseRef.current?.close()
    const qs = encodeURIComponent(token)
    const es = new EventSource(`/api/notifications/subscribe?token=${qs}`)
    sseRef.current = es

    /** @type {ReturnType<typeof setTimeout> | null} */
    let refreshTimer = null

    const scheduleRefreshRooms = () => {
      if (refreshTimer) clearTimeout(refreshTimer)
      refreshTimer = setTimeout(async () => {
        try {
          const list = await fetchChatRooms()
          setRooms(list)
        } catch {
          /* ignore */
        }
      }, 400)
    }

    es.addEventListener('chat-event', (evt) => {
      try {
        const payload = JSON.parse(String(/** @type {MessageEvent} */ (evt).data || '{}'))
        if (payload?.type === 'NEW_MESSAGE' || payload?.type === 'NEW_ROOM') {
          scheduleRefreshRooms()
        }
      } catch {
        /* ignore */
      }
    })

    es.onerror = () => {
      // 브라우저가 자동 재연결한다. 조용히 둔다.
    }

    return () => {
      if (refreshTimer) clearTimeout(refreshTimer)
      es.close()
      if (sseRef.current === es) sseRef.current = null
    }
  }, [isAuthenticated, token])

  const onPickRoom = (roomId) => {
    setSelectedRoomId(roomId)
    // 읽음 처리 요청은 별도로 보내지만, UI는 즉시 0으로 내려 카톡처럼 반응하게 한다.
    setRooms((prev) => prev.map((r) => (r.roomId === roomId ? { ...r, unreadCount: 0 } : r)))
  }

  useEffect(() => {
    const el = chatBodyRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [messages, selectedRoomId])

  useEffect(() => {
    if (!isAuthenticated || !email) return
    let cancelled = false

    const loadRooms = async () => {
      setLoadingRooms(true)
      setError('')
      try {
        const list = await fetchChatRooms()
        if (!cancelled) {
          setRooms(list)
          const fromQuery = searchParams.get('roomId')
          const picked = fromQuery
            ? list.find((r) => r.roomId === fromQuery)?.roomId ?? fromQuery
            : list[0]?.roomId ?? ''
          setSelectedRoomId((prev) => prev || picked)
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoadingRooms(false)
      }
    }

    void loadRooms()
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, email, searchParams])

  useEffect(() => {
    if (!isAuthenticated || !selectedRoomId || !email) return
    let cancelled = false

    const loadMessages = async () => {
      setLoadingMessages(true)
      try {
        const res = await apiRequest(`/chat/rooms/${selectedRoomId}/messages?page=0&size=60`, { method: 'GET' })
        const text = await res.text()
        if (!res.ok) throw new Error(text || '메시지를 불러오지 못했습니다.')
        const data = text ? JSON.parse(text) : {}
        const content = Array.isArray(data?.content) ? data.content : []
        const ordered = [...content].reverse()
        if (!cancelled) setMessages(ordered)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoadingMessages(false)
      }
    }

    const markRead = async () => {
      const q = encodeURIComponent(email)
      try {
        await apiRequest(`/chat/rooms/${selectedRoomId}/read?email=${q}`, { method: 'POST' })
      } catch {
        /* ignore */
      }
    }

    void loadMessages()
    void markRead()
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, selectedRoomId, email])

  useEffect(() => {
    if (!isAuthenticated || !token || !selectedRoomId) return

    stompRef.current?.deactivate()
    const client = new Client({
      webSocketFactory: () => new SockJS('/api/ws-stomp'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: () => {},
      reconnectDelay: 5000,
    })

    client.onConnect = () => {
      client.subscribe(`/sub/chat/room/${selectedRoomId}`, (frame) => {
        const payload = JSON.parse(frame.body)
        if (payload?.type === 'READ_UPDATE') {
          // 상대가 읽음 처리하면, 서버가 기존 메시지들의 unreadCount 를 갱신하므로 최신 상태를 다시 가져온다.
          if (readUpdateTimerRef.current) clearTimeout(readUpdateTimerRef.current)
          readUpdateTimerRef.current = setTimeout(async () => {
            try {
              const res = await apiRequest(`/chat/rooms/${selectedRoomId}/messages?page=0&size=60`, { method: 'GET' })
              const text = await res.text()
              if (!res.ok) return
              const data = text ? JSON.parse(text) : {}
              const content = Array.isArray(data?.content) ? data.content : []
              const ordered = [...content].reverse()
              setMessages(ordered)
            } catch {
              /* ignore */
            }
          }, 250)
          return
        }
        setMessages((prev) => [...prev, payload])
      })
    }

    client.onStompError = () => {
      setError('실시간 연결이 불안정합니다. 새로고침 후 다시 시도해 주세요.')
    }

    client.activate()
    stompRef.current = client

    return () => {
      client.deactivate()
    }
  }, [isAuthenticated, token, selectedRoomId])

  const onSend = async (e) => {
    e.preventDefault()
    const content = input.trim()
    if (!content || !selectedRoomId || !email || !stompRef.current?.connected) return
    setSending(true)
    try {
      stompRef.current.publish({
        destination: '/pub/chat/message',
        body: JSON.stringify({
          roomId: selectedRoomId,
          senderEmail: email,
          senderNickname: myNickname,
          message: content,
        }),
      })
      setInput('')
    } finally {
      setSending(false)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="msg-empty">
        <h1>메시지</h1>
        <p>채팅은 로그인 후 이용할 수 있습니다.</p>
        <Link to="/auth/login" state={{ returnTo: '/messages', hint: '로그인하면 채팅방으로 이동합니다.' }}>
          로그인하기
        </Link>
      </div>
    )
  }

  return (
    <div className="msg">
      <aside className="msg-rooms">
        <h2>대화 목록</h2>
        {loadingRooms && <PageLoading className="page-state--tight" />}
        {!loadingRooms && rooms.length === 0 && (
          <PageEmpty className="page-state--tight" title="채팅방이 없습니다">
            매칭 후 대화가 시작되면 여기에 표시됩니다.
          </PageEmpty>
        )}
        <ul>
          {rooms.map((room) => (
            <li key={room.roomId}>
              <button
                type="button"
                className={`msg-room-btn ${room.roomId === selectedRoomId ? 'is-on' : ''}`}
                onClick={() => onPickRoom(room.roomId)}
              >
                <strong className="msg-room-title">
                  <span className="msg-room-title-text">{room.title || '채팅방'}</span>
                  {room.unreadCount > 0 ? (
                    <span className="msg-room-badge" aria-label={`안 읽은 메시지 ${room.unreadCount}개`}>
                      {room.unreadCount > 99 ? '99+' : String(room.unreadCount)}
                    </span>
                  ) : null}
                </strong>
                <span>{room.lastMessage || '대화를 시작해보세요.'}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <section className="msg-panel">
        <header className="msg-header">
          <div className="msg-avatar" />
          <div className="msg-header-meta">
            <strong>{selectedRoom?.title || '채팅방'}</strong>
            <span>
              {selectedRoom?.participantCount ? `참여자 ${selectedRoom.participantCount}명` : '채팅방을 선택해 주세요'}
            </span>
          </div>
          <span className="msg-dot" />
        </header>

        <div ref={chatBodyRef} className="msg-body">
          {error && <PageError message={error} className="page-state--tight" />}
          {!selectedRoomId && <p className="msg-muted">왼쪽 목록에서 채팅방을 선택해 주세요.</p>}
          {selectedRoomId && loadingMessages && <PageLoading className="page-state--tight" label="메시지를 불러오는 중…" />}
          {selectedRoomId && !loadingMessages && messages.length === 0 && (
            <PageEmpty className="page-state--tight" title="아직 메시지가 없습니다">
              첫 메시지를 보내보세요.
            </PageEmpty>
          )}
          {selectedRoomId && messages.length > 0 && (
            <>
              <div className="msg-day">{formatDay(messages[0]?.createdAt)}</div>
              {messages.map((m) => {
                const mine = m.senderEmail === email
                return (
                  <article key={m.id || `${m.createdAt}-${m.senderEmail}-${m.message.slice(0, 12)}`} className={`msg-bubble-wrap ${mine ? 'mine' : 'other'}`}>
                    {!mine && <div className="msg-mini-avatar" />}
                    <div className="msg-bubble-box">
                      <div className={`msg-bubble ${mine ? 'mine' : 'other'}`}>{m.message}</div>
                      <time className="msg-meta">
                        {mine && m.unreadCount > 0 ? <span className="msg-unread">{m.unreadCount}</span> : null}
                        <span>{formatTime(m.createdAt)}</span>
                      </time>
                    </div>
                  </article>
                )
              })}
            </>
          )}
        </div>

        <form className="msg-input-bar" onSubmit={(e) => void onSend(e)}>
          <button type="button" className="msg-plus" aria-label="추가 메뉴">
            +
          </button>
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="메시지를 입력하세요..."
            disabled={!selectedRoomId || sending}
          />
          <button type="submit" className="msg-send" disabled={!selectedRoomId || sending || !input.trim()}>
            ▶
          </button>
        </form>
      </section>
    </div>
  )
}
