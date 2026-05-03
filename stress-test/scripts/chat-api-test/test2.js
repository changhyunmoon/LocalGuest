import http from 'k6/http';
import { check, sleep } from 'k6';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = 'https://api.bam-match.com/api';

const ROOM_ID = __ENV.ROOM_ID;
const TEST_NAME = __ENV.TEST_NAME || ROOM_ID;

if (!ROOM_ID) {
    throw new Error('ROOM_ID environment variable is required');
}

export const options = {
    scenarios: {
        message_list_load_test: {
            executor: 'ramping-vus',
            stages: [
                { duration: '1m', target: 10 },
                { duration: '2m', target: 10 },

                { duration: '1m', target: 30 },
                { duration: '2m', target: 30 },

                { duration: '1m', target: 50 },
                { duration: '2m', target: 50 },

                { duration: '1m', target: 80 },
                { duration: '2m', target: 80 },

                { duration: '1m', target: 100 },
                { duration: '2m', target: 100 },

                { duration: '1m', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    },
    tags: {
        test_name: TEST_NAME,
        room_id: ROOM_ID,
    },
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email: 'changhyunmoon1999@gmail.com',
            password: 'zxcv1234!',
            role: 'GUEST',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(loginRes, {
        'login status is 200': (r) => r.status === 200,
        'access token exists': (r) => Boolean(r.json('accessToken')),
    });

    const token = loginRes.json('accessToken');

    if (!token) {
        throw new Error(`Login failed. status=${loginRes.status}, body=${loginRes.body}`);
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
            test_name: TEST_NAME,
            room_id: ROOM_ID,
        },
    };

    const res = http.get(
        `${BASE_URL}/chat/rooms/${ROOM_ID}/messages?size=20`,
        params
    );

    check(res, {
        'messages status is 200': (r) => r.status === 200,
    });

    sleep(1);
}

export function handleSummary(data) {
    console.log(`테스트가 완료되었습니다. TEST_NAME=${TEST_NAME}, ROOM_ID=${ROOM_ID}`);

    return {
        [`summary-${TEST_NAME}.html`]: htmlReport(data),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
