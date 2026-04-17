import { apiRequest } from './client.js'

/**
 * module-chat REST — 읽지 않은 개수·목록은 GET /chat/rooms, 방 생성은 POST /chat/rooms.
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
  const data = text ? JSON.parse(text) : {}
  return data
}

/**
 * 매칭 요청과 같은 제목 규칙으로 만든 방이 이미 있으면 찾는다 (서버/운영에서 동일 제목으로 생성된 경우).
 * @param {ChatRoomListItem[]} rooms
 * @param {string | number} matchRequestId
 */
export function findRoomByMatchTitle(rooms, matchRequestId) {
  const title = `LG-MATCH-${matchRequestId}`
  return rooms.find((r) => r.title === title) ?? null
}
