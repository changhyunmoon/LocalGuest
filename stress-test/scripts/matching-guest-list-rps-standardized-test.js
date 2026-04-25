import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'https://api.bam-match.com/api';
const TOKEN = __ENV.TOKEN;
const ENDPOINT = __ENV.ENDPOINT || '/matching/requests/guest/list/slice-projected?page=0&size=20';
const RATE = Number(__ENV.RATE || 160);
const DURATION = __ENV.DURATION || '10m';

const status2xx = new Counter('status_2xx');
const status4xx = new Counter('status_4xx');
const status5xx = new Counter('status_5xx');
const status401 = new Counter('status_401');
const status403 = new Counter('status_403');
const status404 = new Counter('status_404');
const status429 = new Counter('status_429');
const okRate = new Rate('status_200_rate');

export const options = {
  scenarios: {
    guest_list_fixed_rps_standardized: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 120,
      maxVUs: 500,
    },
  },
  // 실험 재현성을 위해 핵심 통계값을 고정 출력한다.
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
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
    headers: { Authorization: `Bearer ${TOKEN}` },
    tags: { endpoint: ENDPOINT },
  });

  if (res.status >= 200 && res.status < 300) status2xx.add(1);
  if (res.status >= 400 && res.status < 500) status4xx.add(1);
  if (res.status >= 500) status5xx.add(1);
  if (res.status === 401) status401.add(1);
  if (res.status === 403) status403.add(1);
  if (res.status === 404) status404.add(1);
  if (res.status === 429) status429.add(1);
  okRate.add(res.status === 200);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
