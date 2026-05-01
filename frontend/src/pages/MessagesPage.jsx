import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { fetchChatRooms, leaveChatRoom } from '../api/chat.js'
import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
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

function readString(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function pickRoomAvatar(room, myEmail) {
  if (!room) return ''

  const direct = [
    room.opponentProfileImageUrl,
    room.otherProfileImageUrl,
    room.partnerProfileImageUrl,
    room.counterpartProfileImageUrl,
    room.profileImageUrl,
    room.profileImage,
    room.avatarUrl,
  ]
    .map(readString)
    .find(Boolean)

  if (direct) return direct

  const members = Array.isArray(room.participants) ? room.participants : []
  const me = String(myEmail ?? '').trim().toLowerCase()
  const other = members.find((m) => String(m?.email ?? '').trim().toLowerCase() !== me) ?? members[0]

  return readString(other?.profileImageUrl) || readString(other?.avatarUrl) || readString(other?.imageUrl) || ''
}

function normalizeRoom(room) {
  return {
    ...room,
    unreadCount: Number(room?.unreadCount ?? 0),
  }
}

/**
 * @typedef {{ roomId: string, title: string, participantCount?: number, lastMessage?: string | null, lastMessageAt?: string | null, unreadCount?: number, participants?: unknown[] }} ChatRoom
 * @typedef {{ id?: string, roomId: string, senderEmail: string, senderNickname?: string, message: string, createdAt?: string }} ChatMessage
 */

export function MessagesPage() {
  const { isAuthenticated, email, token } = useAuth()
  const [searchParams] = useSearchParams()

  const chatBodyRef = useRef(null)
  const stompRef = useRef(/** @type {Client | null} */ (null))
  const roomMenuRef = useRef(/** @type {HTMLDivElement | null} */ (null))

  const [rooms, setRooms] = useState(/** @type {ChatRoom[]} */ ([]))
  const [selectedRoomId, setSelectedRoomId] = useState('')
  const [messages, setMessages] = useState(/** @type {ChatMessage[]} */ ([]))
  const [input, setInput] = useState('')
  const [loadingRooms, setLoadingRooms] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const [roomMenuOpen, setRoomMenuOpen] = useState(false)

  const selectedRoom = useMemo(
    () => rooms.find((room) => room.roomId === selectedRoomId) ?? null,
    [rooms, selectedRoomId],
  )

  const selectedRoomAvatar = useMemo(() => pickRoomAvatar(selectedRoom, email), [selectedRoom, email])

  const bumpRoomPreview = useCallback((roomId, message, createdAt) => {
    setRooms((prev) => {
      const next = prev.map((room) =>
        room.roomId === roomId
          ? {
              ...room,
              lastMessage: message ?? room.lastMessage,
              lastMessageAt: createdAt ?? room.lastMessageAt,
            }
          : room,
      )

      next.sort((a, b) => {
        const ta = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0
        const tb = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0
        return tb - ta
      })

      return next
    })
  }, [])

  const refreshRooms = useCallback(async () => {
    setLoadingRooms(true)
    setError('')
    try {
      const list = await fetchChatRooms()
      const normalized = list.map(normalizeRoom)
      setRooms(normalized)

      const fromQuery = searchParams.get('roomId')
      setSelectedRoomId((prev) => {
        if (prev && normalized.some((room) => room.roomId === prev)) return prev
        if (fromQuery) return normalized.find((room) => room.roomId === fromQuery)?.roomId ?? fromQuery
        return normalized[0]?.roomId ?? ''
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : '채팅방 목록을 불러오지 못했습니다.')
    } finally {
      setLoadingRooms(false)
    }
  }, [searchParams])

  useEffect(() => {
    if (!isAuthenticated) return
    void refreshRooms()
  }, [isAuthenticated, refreshRooms])

  useEffect(() => {
    const el = chatBodyRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [messages, selectedRoomId])

  useEffect(() => {
    setMessages([])
    setRoomMenuOpen(false)
  }, [selectedRoomId])

  useEffect(() => {
    if (!isAuthenticated || !token || !selectedRoomId) return

    stompRef.current?.deactivate()
    setConnecting(true)
    setError('')

    const client = new Client({
      webSocketFactory: () => new SockJS('/api/ws-stomp'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: () => {},
      reconnectDelay: 5000,
    })

    client.onConnect = () => {
      setConnecting(false)
      client.subscribe(`/sub/chat/room/${selectedRoomId}`, (frame) => {
        const payload = JSON.parse(frame.body)

        if (payload?.roomId && payload?.message) {
          setMessages((prev) => [...prev, payload])
          bumpRoomPreview(payload.roomId, payload.message, payload.createdAt)
        }
      })
    }

    client.onStompError = () => {
      setConnecting(false)
      setError('실시간 채팅 연결에 실패했습니다. 다시 로그인한 뒤 시도해 주세요.')
    }

    client.onWebSocketClose = () => {
      setConnecting(false)
    }

    client.activate()
    stompRef.current = client

    return () => {
      client.deactivate()
    }
  }, [bumpRoomPreview, isAuthenticated, token, selectedRoomId])

  useEffect(() => {
    if (!roomMenuOpen) return

    const onDocMouseDown = (e) => {
      const el = roomMenuRef.current
      if (el && !el.contains(/** @type {Node} */ (e.target))) {
        setRoomMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', onDocMouseDown)
    return () => document.removeEventListener('mousedown', onDocMouseDown)
  }, [roomMenuOpen])

  const onPickRoom = (roomId) => {
    setSelectedRoomId(roomId)
  }

  const removeRoomFromList = (roomId) => {
    setRooms((prev) => {
      const next = prev.filter((room) => room.roomId !== roomId)
      setSelectedRoomId((current) => {
        if (current !== roomId) return current
        return next[0]?.roomId ?? ''
      })
      return next
    })
    setMessages([])
  }

  const leaveSelectedRoom = async () => {
    if (!selectedRoomId) return

    const ok = window.confirm('채팅방을 나가시겠습니까? 마지막 참여자라면 채팅방과 메시지가 함께 삭제됩니다.')
    if (!ok) return

    setRoomMenuOpen(false)
    setError('')

    try {
      await leaveChatRoom(selectedRoomId)
      removeRoomFromList(selectedRoomId)
    } catch (e) {
      setError(e instanceof Error ? e.message : '채팅방을 나가지 못했습니다.')
    }
  }

  const onSend = async (e) => {
    e.preventDefault()

    const content = input.trim()
    if (!content || !selectedRoomId || !stompRef.current?.connected) return

    setSending(true)
    try {
      stompRef.current.publish({
        destination: '/pub/chat/message',
        body: JSON.stringify({
          roomId: selectedRoomId,
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
        <div className="msg-rooms-head">
          <h2>채팅방</h2>
          <Link className="msg-room-create" to="/messages/new" aria-label="채팅방 생성">
            생성
          </Link>
        </div>
        <div className="msg-rooms-scroll">
          {loadingRooms && <PageLoading className="page-state--tight" />}
          {!loadingRooms && rooms.length === 0 && (
            <PageEmpty className="page-state--tight" title="채팅방이 없습니다">
              생성된 채팅방이 여기에 표시됩니다.
            </PageEmpty>
          )}
          <ul>
            {rooms.map((room) => {
              const avatarUrl = pickRoomAvatar(room, email)
              return (
                <li key={room.roomId}>
                  <button
                    type="button"
                    className={`msg-room-btn ${room.roomId === selectedRoomId ? 'is-on' : ''}`}
                    onClick={() => onPickRoom(room.roomId)}
                  >
                    <div className="msg-room-main">
                      <div
                        className={`msg-room-avatar${avatarUrl ? '' : ' is-empty'}`}
                        aria-hidden
                        style={avatarUrl ? { backgroundImage: `url(${avatarUrl})` } : undefined}
                      />
                      <div className="msg-room-copy">
                        <strong className="msg-room-title">
                          <span className="msg-room-title-text">{room.title || '채팅방'}</span>
                        </strong>
                        <span>{room.lastMessage || '대화를 시작해보세요.'}</span>
                      </div>
                    </div>
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      </aside>

      <section className="msg-panel">
        <header className="msg-header">
          <div
            className={`msg-avatar${selectedRoomAvatar ? '' : ' is-empty'}`}
            aria-hidden
            style={selectedRoomAvatar ? { backgroundImage: `url(${selectedRoomAvatar})` } : undefined}
          />
          <div className="msg-header-meta">
            <strong>{selectedRoom?.title || '채팅방'}</strong>
            <span>
              {selectedRoom?.participantCount
                ? `참여자 ${selectedRoom.participantCount}명`
                : '채팅방을 선택해 주세요'}
              {connecting ? ' · 연결 중' : ''}
            </span>
          </div>
          <div className="msg-header-actions" ref={roomMenuRef}>
            <button
              type="button"
              className="msg-menu-trigger"
              aria-expanded={roomMenuOpen}
              aria-haspopup="true"
              aria-label="채팅방 메뉴"
              disabled={!selectedRoomId}
              onClick={() => setRoomMenuOpen((open) => !open)}
            >
              <span className="msg-menu-dots" aria-hidden />
            </button>
            {roomMenuOpen && selectedRoomId ? (
              <div className="msg-room-menu" role="menu">
                <button
                  type="button"
                  className="msg-room-menu-item msg-room-menu-item--danger"
                  role="menuitem"
                  onClick={() => void leaveSelectedRoom()}
                >
                  채팅방 나가기
                </button>
              </div>
            ) : null}
          </div>
        </header>

        <div ref={chatBodyRef} className="msg-body">
          {error && <PageError message={error} className="page-state--tight" />}
          {!selectedRoomId && <p className="msg-muted">왼쪽 목록에서 채팅방을 선택해 주세요.</p>}
          {selectedRoomId && messages.length === 0 && (
            <PageEmpty className="page-state--tight" title="아직 메시지가 없습니다">
              첫 메시지를 보내보세요.
            </PageEmpty>
          )}
          {selectedRoomId && messages.length > 0 && (
            <>
              <div className="msg-day">{formatDay(messages[0]?.createdAt)}</div>
              {messages.map((message, index) => {
                const mine = message.senderEmail === email
                const key =
                  message.id ||
                  `${message.createdAt ?? ''}-${message.senderEmail ?? ''}-${message.message?.slice(0, 12) ?? ''}-${index}`

                return (
                  <article key={key} className={`msg-bubble-wrap ${mine ? 'mine' : 'other'}`}>
                    {!mine && (
                      <div
                        className={`msg-mini-avatar${selectedRoomAvatar ? '' : ' is-empty'}`}
                        style={selectedRoomAvatar ? { backgroundImage: `url(${selectedRoomAvatar})` } : undefined}
                        aria-hidden
                      />
                    )}
                    <div className="msg-bubble-box">
                      {!mine && message.senderNickname ? <span className="msg-meta">{message.senderNickname}</span> : null}
                      <div className={`msg-bubble ${mine ? 'mine' : 'other'}`}>{message.message}</div>
                      <time className="msg-meta">{formatTime(message.createdAt)}</time>
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
            disabled={!selectedRoomId || sending || connecting}
          />
          <button type="submit" className="msg-send" disabled={!selectedRoomId || sending || connecting || !input.trim()}>
            전송
          </button>
        </form>
      </section>
    </div>
  )
}
