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

function logResult(name, durations, statuses) {
    const sorted = [...durations].sort((a, b) => a - b)
    const avg = durations.reduce((a, b) => a + b, 0) / durations.length
    const p95 = sorted[Math.max(0, Math.ceil(sorted.length * 0.95) - 1)]

    console.log(`\n[${name}]`)
    console.log(`count: ${durations.length}`)
    console.log(`min: ${sorted[0].toFixed(2)} ms`)
    console.log(`avg: ${avg.toFixed(2)} ms`)
    console.log(`p95: ${p95.toFixed(2)} ms`)
    console.log(`max: ${sorted[sorted.length - 1].toFixed(2)} ms`)
    console.log(`statuses: ${statuses.join(', ')}`)
}

export default function () {
    const durations = []
    const statuses = []

    for (let i = 0; i < 2; i++) {
        const res = http.post(`${BASE_URL}/ai/recommend`, JSON.stringify(BODY), {
            headers: { 'Content-Type': 'application/json' },
        })
        check(res, { 'warmup ai recommend ok': (r) => r.status === 200 })
        sleep(0.2)
    }

    for (let i = 0; i < 20; i++) {
        const res = http.post(`${BASE_URL}/ai/recommend`, JSON.stringify(BODY), {
            headers: { 'Content-Type': 'application/json' },
        })
        check(res, { 'ai recommend ok': (r) => r.status === 200 })
        durations.push(res.timings.duration)
        statuses.push(res.status)
        sleep(0.2)
    }

    logResult('POST /ai/recommend', durations, statuses)
}
