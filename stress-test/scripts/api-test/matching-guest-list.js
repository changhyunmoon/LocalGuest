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

function percentile(sorted, p) {
    if (sorted.length === 0) return 0
    const index = Math.ceil(sorted.length * p) - 1
    return sorted[Math.max(0, index)]
}

function logResult(name, durations, statuses) {
    const sorted = [...durations].sort((a, b) => a - b)
    const p95 = percentile(sorted, 0.95)
    const p99 = percentile(sorted, 0.99)
    const errorCount = statuses.filter((s) => s < 200 || s >= 400).length
    const errorRate = (errorCount / statuses.length) * 100

    console.log(`\n[${name}]`)
    console.log(`count: ${statuses.length}`)
    console.log(`p95: ${p95.toFixed(2)} ms`)
    console.log(`p99: ${p99.toFixed(2)} ms`)
    console.log(`errorRate: ${errorRate.toFixed(2)} %`)
    console.log(`statuses: ${statuses.join(', ')}`)
}

function loginAndGetToken() {
    const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify(LOGIN_INFO), {
        headers: { 'Content-Type': 'application/json' },
    })

    check(res, { 'login success': (r) => r.status === 200 })

    if (res.status !== 200) {
        throw new Error(`로그인 실패: status=${res.status}, body=${res.body}`)
    }

    return res.json().accessToken
}

export default function () {
    const measuredDurations = []
    const statuses = []
    const accessToken = loginAndGetToken()

    const headers = {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
    }

    for (let i = 0; i < 2; i++) {
        const res = http.get(`${BASE_URL}/matching/requests/guest/list`, { headers })
        check(res, { 'warmup ok': (r) => r.status === 200 })
        sleep(0.2)
    }

    for (let i = 0; i < 20; i++) {
        const res = http.get(`${BASE_URL}/matching/requests/guest/list`, { headers })
        check(res, { 'request completed': (r) => r.status > 0 })
        measuredDurations.push(res.timings.duration)
        statuses.push(res.status)
        sleep(0.2)
    }

    logResult('GET /matching/requests/guest/list', measuredDurations, statuses)
}
