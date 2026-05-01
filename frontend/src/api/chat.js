import { apiRequest } from './client.js'

/**
 * Chat rooms:
 * - List: GET /chat/rooms
 * - Create: POST /chat/rooms
 * - Leave: DELETE /chat/rooms/{roomId}/leave
 * - Messages: GET /chat/rooms/{roomId}/messages
 * @typedef {{ roomId: string, title: string, ownerEmail: string, participantCount: number, lastMessage: string | null, lastMessageAt: string | null, unreadCount: number, createdAt?: string }} ChatRoomListItem
 */

/**
 * @returns {Promise<ChatRoomListItem[]>}
 */
export async function fetchChatRooms() {
  const res = await apiRequest('/chat/rooms', { method: 'GET' })
  const text = await res.text()
  if (!res.ok) throw new Error(text || '채팅방 목록을 불러오지 못했습니다.')
  const data = text ? JSON.parse(text) : {}
  return Array.isArray(data?.rooms) ? data.rooms : []
}

/**
 * @param {{ title: string, participantEmails: string[] }} body
 * @returns {Promise<ChatRoomListItem>}
 */
export async function createChatRoom(body) {
  const res = await apiRequest('/chat/rooms', { method: 'POST', json: body })
  const text = await res.text()
  if (!res.ok) throw new Error(text || '채팅방을 만들지 못했습니다.')
  return text ? JSON.parse(text) : {}
}

/**
 * @param {string} roomId
 * @returns {Promise<{ roomId?: string, userEmail?: string, left?: boolean, roomDeleted?: boolean }>}
 */
export async function leaveChatRoom(roomId) {
  const res = await apiRequest(`/chat/rooms/${encodeURIComponent(roomId)}/leave`, { method: 'DELETE' })
  const text = await res.text()
  if (!res.ok) throw new Error(text || '채팅방을 나가지 못했습니다.')
  return text ? JSON.parse(text) : {}
}

/**
 * @param {string} roomId
 * @param {{ cursor?: string | null, size?: number }} [options]
 * @returns {Promise<{ messages: unknown[], nextCursor: string | null, hasNext: boolean }>}
 */
export async function fetchChatMessages(roomId, { cursor = null, size = 20 } = {}) {
  const params = new URLSearchParams()
  params.set('size', String(size))

  if (cursor) {
    params.set('cursor', cursor)
  }

  const res = await apiRequest(
    `/chat/rooms/${encodeURIComponent(roomId)}/messages?${params.toString()}`,
    { method: 'GET' },
  )

  const text = await res.text()
  if (!res.ok) throw new Error(text || '메시지를 불러오지 못했습니다.')

  return text
    ? JSON.parse(text)
    : {
        messages: [],
        nextCursor: null,
        hasNext: false,
      }
}

/**
 * Finds an existing room created with the same title convention as a matching request.
 * @param {ChatRoomListItem[]} rooms
 * @param {string | number} matchRequestId
 */
export function findRoomByMatchTitle(rooms, matchRequestId) {
  const title = `LG-MATCH-${matchRequestId}`
  return rooms.find((r) => r.title === title) ?? null
}
