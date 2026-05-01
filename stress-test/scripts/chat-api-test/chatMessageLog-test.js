import http from 'k6/http'
import { check, sleep } from 'k6'
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";

const BASE_URL = 'https://api.bam-match.com/api'
const ROOM_IDS = [
    "f0dff13b-4558-11f1-a198-029e36ba38f1", "f126a5e8-4558-11f1-a198-029e36ba38f1",
    "f13bc58e-4558-11f1-a198-029e36ba38f1", "f15cf535-4558-11f1-a198-029e36ba38f1",
    "f1a45234-4558-11f1-a198-029e36ba38f1", "f1d08b7b-4558-11f1-a198-029e36ba38f1",
    "f1dd4560-4558-11f1-a198-029e36ba38f1", "f208dafb-4558-11f1-a198-029e36ba38f1",
    "f2433ada-4558-11f1-a198-029e36ba38f1", "f2674858-4558-11f1-a198-029e36ba38f1"
]

export const options = {
    stages: [
        { duration: '1m', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '1m', target: 0 },
    ],
}

export function setup() {
    const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
        email: 'changhyunmoon1999@gmail.com',
        password: 'zxcv1234!',
        role: 'GUEST',
    }), { headers: { 'Content-Type': 'application/json' } });
    return { token: res.json().accessToken };
}

export default function (data) {
    const params = { headers: { Authorization: `Bearer ${data.token}` } };
    const roomId = ROOM_IDS[Math.floor(Math.random() * ROOM_IDS.length)];

    const res = http.get(`${BASE_URL}/chat/rooms/${roomId}/messages?size=20`, params);

    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(1);
}

// 테스트 완료 후 호출되는 함수
export function handleSummary(data) {
    console.log('테스트가 완료되었습니다. 리포트를 생성합니다.');

    return {
        "summary.html": htmlReport(data), // 브라우저에서 볼 수 있는 HTML 그래프 리포트
        stdout: textSummary(data, { indent: " ", enableColors: true }), // 터미널 요약
    };
}

// 기본 텍스트 요약 도우미
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";