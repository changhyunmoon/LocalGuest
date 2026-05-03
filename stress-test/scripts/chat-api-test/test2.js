import http from 'k6/http';
import { check, sleep } from 'k6';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = 'https://api.bam-match.com/api';

const ROOM_ID = __ENV.ROOM_ID || 'f0dff13b-4558-11f1-a198-029e36ba38f1';

export const options = {
    stages: [
        { duration: '1m', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000', 'p(99)<3000'],
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
    console.log('테스트가 완료되었습니다. 리포트를 생성합니다.');

    return {
        'summary.html': htmlReport(data),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
