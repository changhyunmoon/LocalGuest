import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = 'https://api.bam-match.com/api'

const LOGIN_INFO = {
    email: 'changhyunmoon1999@gmail.com',
    password: 'zxcv1234!',
    role: 'GUEST',
}

export const options = {
    vus: 1,
    iterations: 1,
}

function logResult(name, durations, statuses) {
    const sorted = [...durations].sort((a, b) => a - b)
    const sum = durations.reduce((acc, v) => acc + v, 0)
    const avg = sum / durations.length
    const min = sorted[0]
    const max = sorted[sorted.length - 1]
    const p95 = sorted[Math.max(0, Math.ceil(sorted.length * 0.95) - 1)]

    console.log(`\n[${name}]`)
    console.log(`count: ${durations.length}`)
    console.log(`min: ${min.toFixed(2)} ms`)
    console.log(`avg: ${avg.toFixed(2)} ms`)
    console.log(`p95: ${p95.toFixed(2)} ms`)
    console.log(`max: ${max.toFixed(2)} ms`)
    console.log(`statuses: ${statuses.join(', ')}`)
}

export default function () {
    const durations = []
    const statuses = []

    for (let i = 0; i < 2; i++) {
        const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify(LOGIN_INFO), {
            headers: { 'Content-Type': 'application/json' },
        })
        check(res, { 'warmup login ok': (r) => r.status === 200 })
        sleep(0.2)
    }

    for (let i = 0; i < 20; i++) {
        const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify(LOGIN_INFO), {
            headers: { 'Content-Type': 'application/json' },
        })
        check(res, { 'login ok': (r) => r.status === 200 })
        durations.push(res.timings.duration)
        statuses.push(res.status)
        sleep(0.2)
    }

    logResult('POST /auth/login', durations, statuses)
}
