import http from 'k6/http';
import { check, sleep } from 'k6';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = 'https://api.bam-match.com/api';
const ROOM_ID = 'b0075633-46ff-11f1-a198-029e36ba38f1';
const TEST_NAME = 'room_100_messages';

export const options = {
    scenarios: {
        message_list_load_test: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: 20 },  // 점진적 상승 (Warm-up)
                { duration: '1m', target: 20 },
                { duration: '30s', target: 50 },  // 중간 부하
                { duration: '1m', target: 50 },
                { duration: '1m', target: 100 },  // 목표 부하 도달 (Peak)
                { duration: '3m', target: 100 },  // 3분간 목표 수치 유지 및 검증
                { duration: '1m', target: 0 },    // 쿨다운
            ],
        },
    },
    // 설정하신 목표 수치를 테스트 통과 기준으로 설정
    thresholds: {
        // 에러율 0% 목표 (0.01%보다 작아야 통과하도록 설정)
        'http_req_failed': ['rate<0.001'],
        // p95 500ms 이내, p99 1000ms 이내
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],
        // 특정 API 태그별로도 엄격하게 관리
        'http_req_duration{name:GET /chat/rooms/{roomId}/messages}': ['p(95)<500', 'p(99)<1000'],
    },
    tags: {
        test_name: TEST_NAME,
        room_id: ROOM_ID,
    },
};

// 1회만 실행되어 인증 토큰 획득
export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email: 'changhyunmoon1999@gmail.com',
            password: 'zxcv1234!',
            role: 'GUEST',
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const token = loginRes.json('accessToken');
    if (!token) {
        throw new Error(`Login failed! Status: ${loginRes.status}`);
    }
    return { token };
}

export default function (data) {
    const params = {
        headers: {
            Authorization: `Bearer ${data.token}`,
        },
        tags: {
            name: 'GET /chat/rooms/{roomId}/messages',
        },
    };

    const res = http.get(
        `${BASE_URL}/chat/rooms/${ROOM_ID}/messages?size=20`,
        params
    );

    // 에러율 0% 목표를 위해 모든 200 응답 확인
    check(res, {
        'is status 200': (r) => r.status === 200,
        'transaction time OK': (r) => r.timings.duration < 1000,
    });

    // CPU 부하 조절을 위해 1초간 휴식
    sleep(1);
}

export function handleSummary(data) {
    return {
        [`summary-${TEST_NAME}.html`]: htmlReport(data),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
