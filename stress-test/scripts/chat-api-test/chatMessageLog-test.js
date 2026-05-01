import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = 'https://api.bam-match.com/api'

// 1. 테스트 설정: 점진적으로 사용자(Vuser) 증가
export const options = {
    stages: [
        { duration: '1m', target: 20 },  // 1분 동안 20명까지 서서히 증가 (Warm-up)
        { duration: '2m', target: 50 },  // 2분 동안 50명 유지 (Steady State)
        { duration: '2m', target: 100 }, // 2분 동안 100명까지 증가 (Stress Test)
        { duration: '1m', target: 0 },   // 종료
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95%의 응답 시간이 500ms 이내일 것
        http_req_failed: ['rate<0.01'],   // 에러율 1% 미만 유지
    },
}

const ROOM_IDS = [
    "f0dff13b-4558-11f1-a198-029e36ba38f1",
    "f126a5e8-4558-11f1-a198-029e36ba38f1",
    "f13bc58e-4558-11f1-a198-029e36ba38f1",
    "f15cf535-4558-11f1-a198-029e36ba38f1",
    "f1a45234-4558-11f1-a198-029e36ba38f1",
    "f1d08b7b-4558-11f1-a198-029e36ba38f1",
    "f1dd4560-4558-11f1-a198-029e36ba38f1",
    "f208dafb-4558-11f1-a198-029e36ba38f1",
    "f2433ada-4558-11f1-a198-029e36ba38f1",
    "f2674858-4558-11f1-a198-029e36ba38f1"
]

// 2. Setup 단계: 테스트 시작 전 로그인을 수행하여 토큰을 모든 Vuser에게 공유
export function setup() {
    const loginPayload = JSON.stringify({
        email: 'changhyunmoon1999@gmail.com',
        password: 'zxcv1234!',
        role: 'GUEST',
    })

    const res = http.post(`${BASE_URL}/auth/login`, loginPayload, {
        headers: { 'Content-Type': 'application/json' },
    })

    check(res, { 'setup login success': (r) => r.status === 200 })

    return { accessToken: res.json().accessToken }
}

// 3. 메인 시나리오: 각 Vuser가 반복 수행
export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.accessToken}`,
    }

    // [Step 1] 리스트 중 랜덤하게 방 하나 선택
    const roomId = ROOM_IDS[Math.floor(Math.random() * ROOM_IDS.length)]

    // [Step 2] 첫 페이지 조회 (최신 메시지 20개)
    let res = http.get(`${BASE_URL}/chat/rooms/${roomId}/messages?size=20`, { headers })

    check(res, {
        'get messages status is 200': (r) => r.status === 200,
        'response has content': (r) => r.json().content !== undefined,
    })

    // [Step 3] 커서가 있다면 스크롤 시뮬레이션 (다음 페이지 조회)
    const body = res.json()
    if (body.nextCursor) {
        sleep(0.5) // 사용자가 읽는 시간 지연

        let nextRes = http.get(
            `${BASE_URL}/chat/rooms/${roomId}/messages?cursor=${body.nextCursor}&size=20`,
            { headers }
        )

        check(nextRes, {
            'paging messages status is 200': (r) => r.status === 200,
        })
    }

    // 다음 행동 전 대기 (Think Time)
    sleep(1)
}