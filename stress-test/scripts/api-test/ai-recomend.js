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

function logResult(name, durations, statuses, startTime, endTime) {
    const sorted = [...durations].sort((a, b) => a - b)
    const count = durations.length
    const avg = durations.reduce((a, b) => a + b, 0) / count

    // Percentile 계산
    const p95 = sorted[Math.max(0, Math.ceil(count * 0.95) - 1)]
    const p99 = sorted[Math.max(0, Math.ceil(count * 0.99) - 1)]

    // 오류율 계산 (200이 아닌 모든 상태 코드)
    const errors = statuses.filter(s => s !== 200).length
    const errorRate = (errors / count) * 100

    // TPS 계산 (실제 테스트 수행 시간 기준)
    const durationSeconds = (endTime - startTime) / 1000
    const tps = count / durationSeconds

    console.log(`\n================ [ ${name} ] ================`)
    console.log(`api: ${name}`)
    console.log(`avg: ${avg.toFixed(2)} ms`)
    console.log(`p95: ${p95.toFixed(2)} ms`)
    console.log(`p99: ${p99.toFixed(2)} ms`)
    console.log(`오류율: ${errorRate.toFixed(2)}%`)
    console.log(`초당 처리 건수(TPS): ${tps.toFixed(2)} req/s`)
    console.log(`CPU 사용량: [Grafana에서 확인 필요]`)
    console.log(`메모리 사용량: [Grafana에서 확인 필요]`)
    console.log(`================================================\n`)
}

export default function () {
    const durations = []
    const statuses = []

    // 1. Warmup
    for (let i = 0; i < 2; i++) {
        http.post(`${BASE_URL}/ai/recommend`, BODY, {
            headers: { 'Content-Type': 'application/json' },
        })
        sleep(0.2)
    }

    // 2. Main Test (시간 측정 시작)
    const startTime = Date.now()
    for (let i = 0; i < 20; i++) {
        const res = http.post(`${BASE_URL}/ai/recommend`, BODY, {
            headers: { 'Content-Type': 'application/json' },
        })

        check(res, { 'status is 200': (r) => r.status === 200 })

        durations.push(res.timings.duration)
        statuses.push(res.status)
        sleep(0.2)
    }
    const endTime = Date.now()

    logResult('POST /ai/recommend', durations, statuses, startTime, endTime)
}
