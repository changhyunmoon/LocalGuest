import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'https://api.bam-match.com/api';
const TOKEN = __ENV.TOKEN;
const ENDPOINT = __ENV.ENDPOINT || '/matching/requests/guest/list';
const RATE = Number(__ENV.RATE || 120);
const DURATION = __ENV.DURATION || '10m';

export const options = {
  scenarios: {
    guest_list_fixed_rps: {
      executor: 'constant-arrival-rate',
      // 초당 RATE건을 고정으로 생성 (before/after 모두 동일 값으로 비교)
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      // 시스템 여유에 맞춰 VU 풀 조정
      preAllocatedVUs: 80,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export default function () {
  if (!TOKEN) {
    throw new Error('TOKEN is required. Set env var TOKEN');
  }

  const res = http.get(`${BASE}${ENDPOINT}`, {
    headers: {
      Authorization: `Bearer ${TOKEN}`,
    },
    tags: {
      endpoint: ENDPOINT,
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
