import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = 'http://host.docker.internal:8080';

const errorRate = new Rate('errors');
const reqDuration = new Trend('req_duration', true);

// 4개 전략 시나리오 순차 실행
export const options = {
  scenarios: {
    baseline: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 10,
      maxDuration: '60s',
      startTime: '0s',
      env: { STRATEGY: 'baseline' },
      tags: { scenario: 'baseline' },
    },
    mutexLock: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 10,
      maxDuration: '60s',
      startTime: '65s',
      env: { STRATEGY: 'mutexLock' },
      tags: { scenario: 'mutexLock' },
    },
    logicalExpiration: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 10,
      maxDuration: '60s',
      startTime: '130s',
      env: { STRATEGY: 'logicalExpiration' },
      tags: { scenario: 'logicalExpiration' },
    },
    ttlJitter: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 10,
      maxDuration: '60s',
      startTime: '195s',
      env: { STRATEGY: 'ttlJitter' },
      tags: { scenario: 'ttlJitter' },
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration': ['p(95)<500'],
  },
};

// logicalExpiration 시나리오 전 warmUp 호출
export function setup() {
  const warmUpRes = http.post(`${BASE_URL}/api/accounts/warmup/1`);
  console.log(`WarmUp status: ${warmUpRes.status}`);
}

export default function () {
  const strategy = __ENV.STRATEGY || 'baseline';
  const accountId = 1;

  const res = http.get(`${BASE_URL}/api/accounts/${accountId}?strategy=${strategy}`);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'has ownerName': (r) => r.json('ownerName') !== undefined,
  });

  errorRate.add(!success);
  reqDuration.add(res.timings.duration);

  sleep(0.01);
}
