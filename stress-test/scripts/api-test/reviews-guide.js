import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = 'https://api.bam-match.com/api'
const GUIDE_ID = 1

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
        const res = http.get(`${BASE_URL}/reviews/guide/${GUIDE_ID}`)
        check(res, { 'warmup reviews ok': (r) => r.status === 200 })
        sleep(0.2)
    }

    for (let i = 0; i < 20; i++) {
        const res = http.get(`${BASE_URL}/reviews/guide/${GUIDE_ID}`)
        check(res, { 'reviews ok': (r) => r.status === 200 })
        durations.push(res.timings.duration)
        statuses.push(res.status)
        sleep(0.2)
    }

    logResult('GET /reviews/guide/{guideId}', durations, statuses)
}
