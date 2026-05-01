<h1>Module Chat</h1>

<p>
  <code>module-chat</code>은 채팅방 생성/조회/나가기, STOMP 기반 실시간 메시지 송수신,
  Redis Pub/Sub 브로드캐스트, MongoDB 메시지 저장을 담당한다.
</p>

<h2>전체 메시지 흐름</h2>

<pre>
Client
→ STOMP CONNECT JWT 인증
→ /pub/chat/message 메시지 전송
→ ChatController
→ ChatMessageService에서 채팅방/참여자 검증
→ MongoDB에 메시지 저장
→ Redis publish
→ Redis subscribe
→ /sub/chat/room/{roomId} 로 broadcast
→ Client 수신
</pre>

<h2>구성 요소</h2>

<ul>
  <li><strong>Controller</strong>: 채팅방 API 요청과 STOMP 메시지 요청을 받는다.</li>
  <li><strong>ChatRoomService</strong>: 채팅방 생성, 리스트 조회, 나가기 로직을 처리한다.</li>
  <li><strong>ChatMessageService</strong>: 메시지 저장 전 채팅방 존재 여부와 참여자 여부를 검증하고 MongoDB에 저장한다.</li>
  <li><strong>RedisPubService</strong>: roomId를 Redis topic으로 사용하고 메시지를 발행한다.</li>
  <li><strong>RedisPublisher</strong>: RedisTemplate으로 실제 publish를 수행한다.</li>
  <li><strong>RedisSubscribeListener</strong>: Redis 메시지를 수신해 WebSocket 구독자에게 전달한다.</li>
</ul>

<h2>STOMP 인증</h2>

<pre>
Client
→ /ws-stomp 연결
→ CONNECT frame에 Authorization: Bearer {accessToken} 전달
→ JwtChannelInterceptor에서 JWT 검증
→ Authentication을 STOMP session user로 저장
</pre>

<p>
  이후 메시지 전송 시 서버는 클라이언트가 보낸 sender 정보를 믿지 않고,
  <code>authentication.getName()</code>으로 발신자를 확정한다.
</p>

<h2>채팅방 생성</h2>

<pre>
POST /api/chat/rooms
</pre>

<pre>
Client
→ 채팅방 제목, 초대할 사용자 이메일 목록 전송
→ ChatRoomController에서 방장 email 추출
→ ChatRoomService에서 방장/참여자 Member 조회
→ ChatRoom 생성
→ ChatParticipant 추가
→ ChatRoom 저장
→ roomId, title, participants 반환
</pre>

<h2>채팅방 리스트 조회</h2>

<pre>
GET /api/chat/rooms
</pre>

<pre>
ChatRoomController
→ Authentication에서 email 추출
→ ChatRoomService.findMyRooms(email)
→ ChatRoomRepository.findAllByUserEmail(email)
→ 내가 참여 중인 채팅방 목록 반환
</pre>

<h2>채팅방 나가기</h2>

<pre>
DELETE /api/chat/rooms/{roomId}/leave
</pre>

<pre>
ChatRoom + participants 조회
→ 로그인 사용자의 참여 정보 제거
→ 남은 참여자가 있으면 chat_participants만 삭제
→ 마지막 참여자라면 MongoDB chat_messages 삭제
→ 마지막 참여자라면 MySQL chat_rooms 삭제
</pre>

<h2>저장소 역할</h2>

<ul>
  <li><strong>MySQL</strong>: 채팅방과 참여자 정보를 저장한다.</li>
  <li><strong>MongoDB</strong>: 실제 채팅 메시지를 저장한다.</li>
  <li><strong>Redis</strong>: 서버 간 메시지 브로드캐스트를 담당한다.</li>
</ul>

<h2>주의 사항</h2>

<ul>
  <li>메시지는 MongoDB 저장이 성공한 뒤 Redis로 publish한다.</li>
  <li>RedisSubscribeListener에서는 메시지를 저장하지 않는다.</li>
  <li>클라이언트가 보낸 sender 정보는 신뢰하지 않는다.</li>
  <li>마지막 참여자가 나가면 MySQL 채팅방과 MongoDB 메시지를 함께 삭제한다.</li>
</ul>
