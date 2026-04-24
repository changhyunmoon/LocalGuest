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

function login() {
    const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify(LOGIN_INFO), {
        headers: { 'Content-Type': 'application/json' },
    })

    check(res, { 'login ok': (r) => r.status === 200 })

    if (res.status !== 200) {
        throw new Error(`login failed: ${res.status} ${res.body}`)
    }

    return res.json().accessToken
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
    const token = login()
    const headers = {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
    }

    const durations = []
    const statuses = []

    for (let i = 0; i < 2; i++) {
        const res = http.get(`${BASE_URL}/matching/requests/guest/list`, { headers })
        check(res, { 'warmup matching guest list ok': (r) => r.status === 200 })
        sleep(0.2)
    }

    for (let i = 0; i < 20; i++) {
        const res = http.get(`${BASE_URL}/matching/requests/guest/list`, { headers })
        check(res, { 'matching guest list ok': (r) => r.status === 200 })
        durations.push(res.timings.duration)
        statuses.push(res.status)
        sleep(0.2)
    }

    logResult('GET /matching/requests/guest/list', durations, statuses)
}
