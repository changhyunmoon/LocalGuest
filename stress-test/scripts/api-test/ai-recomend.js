import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = 'https://api.bam-match.com/api'

const BODY = {
    prompt:
        '부산으로 2박 3일 여행 가고 싶고 부모님 포함 3명이에요. 조용하고 걷기 좋은 코스를 선호하고 예산은 40~60만원 정도입니다.',
    topN: 3,
}

export const options = {
    vus: 1,
    iterations: 1,
}

function percentile(sorted, p) {
    if (sorted.length === 0) return 0
    const index = Math.ceil(sorted.length * p) - 1
    return sorted[Math.max(0, index)]
}

function logResult(name, durations, statuses) {
    const sorted = [...durations].sort((a, b) => a - b)

    const p95 = percentile(sorted, 0.95)
    const p99 = percentile(sorted, 0.99)

    const errorCount = statuses.filter((status) => status < 200 || status >= 400).length
    const errorRate = (errorCount / statuses.length) * 100

    console.log(`\n[${name}]`)
    console.log(`count: ${statuses.length}`)
    console.log(`p95: ${p95.toFixed(2)} ms`)
    console.log(`p99: ${p99.toFixed(2)} ms`)
    console.log(`errorRate: ${errorRate.toFixed(2)} %`)
    console.log(`statuses: ${statuses.join(', ')}`)
}

export default function () {
    const measuredDurations = []
    const statuses = []

    // 워밍업 2회
    for (let i = 0; i < 2; i++) {
        const res = http.post(`${BASE_URL}/ai/recommend`, JSON.stringify(BODY), {
            headers: {
                'Content-Type': 'application/json',
            },
        })

        check(res, {
            'warmup ai recommend ok': (r) => r.status === 200,
        })

        sleep(0.2)
    }

    // 본 측정 20회
    for (let i = 0; i < 20; i++) {
        const res = http.post(`${BASE_URL}/ai/recommend`, JSON.stringify(BODY), {
            headers: {
                'Content-Type': 'application/json',
            },
        })

        check(res, {
            'ai recommend request completed': (r) => r.status > 0,
        })

        measuredDurations.push(res.timings.duration)
        statuses.push(res.status)

        sleep(0.2)
    }

    logResult('POST /ai/recommend', measuredDurations, statuses)
}
