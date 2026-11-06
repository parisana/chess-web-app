import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import type { Options } from 'k6/options';
import type { Response } from 'k6/http';

/**
 * About this test:
 * This test reads the statistics for the authenticated Player,
 * specified by vus which defaults to 1 and can be specified via the VUS environment variable.
 * It is intended to establish a baseline for read performance.
 *
 * The test has three phases:
 * Setup	Register and authenticate the test Players
 * Warm-up	Read statistics for 20 seconds
 * Measurement	Continue
 * Teardown	Log out the sessions; retain test Players
 */


interface Client {
  playerId: number;
  sessionCookie: string;
}

interface Fixture {
  clients: Client[];
}

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/+$/, '');
const vus = Number(__ENV.VUS || '1');
const warmupSeconds = 20;
const measurementSeconds = 60;

if (!Number.isInteger(vus) || vus < 1 || vus > 20) {
  throw new Error('VUS must be an integer from 1 to 20 for this initial baseline');
}

// Only requests dispatched in the measurement window enter these metrics.
const measuredRequests = new Counter('measured_read_requests');
const measuredFailures = new Rate('measured_read_failures');
const measuredDuration = new Trend('measured_read_duration', true);

export const options: Options = {
  setupTimeout: '2m',
  teardownTimeout: '2m',
  noCookiesReset: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    statistics_reads: {
      executor: 'constant-vus',
      vus,
      duration: `${warmupSeconds + measurementSeconds}s`,
      gracefulStop: '12s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    measured_read_requests: ['count>0'],
    measured_read_failures: ['rate==0'],
  },
};

function expectStatus(response: Response, expected: number, label: string): void {
  if (!check(response, { [label]: value => value.status === expected })) {
    fail(`${label}: expected ${expected}, received ${response.status}`);
  }
}

function cookie(name: string): string {
  const values = http.cookieJar().cookiesForURL(`${baseUrl}/api/v1/`)[name];
  if (!values || values.length !== 1) {
    fail(`Expected exactly one ${name} cookie`);
  }
  return values[0];
}

function csrf(phase: string): string {
  const response = http.get(`${baseUrl}/api/v1/auth/csrf`, {
    redirects: 0,
    timeout: '10s',
    tags: { name: 'GET /auth/csrf', phase },
  });
  expectStatus(response, 204, `${phase}: initialize CSRF`);
  return cookie('XSRF-TOKEN');
}

function post(path: string, body: Record<string, unknown>, token: string, phase: string): Response {
  return http.post(`${baseUrl}/api/v1${path}`, JSON.stringify(body), {
    redirects: 0,
    timeout: '10s',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': token,
    },
    tags: { name: `POST ${path}`, phase },
  });
}

export function setup(): Fixture {
  const clients: Client[] = [];
  const runId = `${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
  const jar = http.cookieJar();

  for (let index = 0; index < vus; index++) {
    // A distinct cookie jar state prevents login from replacing another client.
    jar.clear(baseUrl);
    const username = `lr_${runId}_${index}`;
    const email = `${username}@example.com`;
    const password = 'LoadTestOnly-123!';
    const token = csrf('setup');

    const registration = post('/auth/register', { username, email, password }, token, 'setup');
    expectStatus(registration, 201, 'setup: register Player');

    const login = post('/auth/login', { email, password }, token, 'setup');
    expectStatus(login, 200, 'setup: login');
    const id = login.json('id');

    if (!check(id, {
      'setup: positive Player ID': value =>
        typeof value === 'number' && Number.isSafeInteger(value) && value > 0,
    })) {
      fail('setup: invalid Player ID');
    }
    if (typeof id !== 'number') {
      fail('setup: Player ID must be numeric');
    }

    clients.push({ playerId: id, sessionCookie: cookie('CHESS_SESSION') });
  }

  return { clients };
}

let currentClient: Client | undefined;

export default function (fixture: Fixture): void {
  if (!currentClient) {
    // This local, single-scenario run assigns one distinct account to each VU.
    currentClient = fixture.clients[execution.vu.idInTest - 1];
    if (!currentClient) {
      fail('No fixture account for this VU');
    }
    // setup() cookies are not automatically copied to VU cookie jars.
    http.cookieJar().set(baseUrl, 'CHESS_SESSION', currentClient.sessionCookie, { path: '/' });
  }

  const elapsed = Date.now() - execution.scenario.startTime;
  const measured = elapsed >= warmupSeconds * 1000 &&
    elapsed < (warmupSeconds + measurementSeconds) * 1000;
  const phase = measured ? 'measure' : 'warmup';

  const response = http.get(`${baseUrl}/api/v1/players/me/statistics`, {
    redirects: 0,
    timeout: '10s',
    tags: { name: 'GET /players/me/statistics', phase },
  });

  let correct = false;
  if (response.status === 200) {
    try {
      const body = response.json() as unknown as {
        playerId: number;
        gamesPlayed: number;
      } | null;
      correct = body !== null && body.playerId === currentClient.playerId && body.gamesPlayed === 0;
    } catch {
      correct = false;
    }
  }

  check(correct, { 'Statistics response has expected status and Player data': value => value }, { phase });

  if (measured) {
    measuredRequests.add(1);
    measuredFailures.add(!correct);
    measuredDuration.add(response.timings.duration);
  }

  // Think time limits traffic. Request time adds to this one-second pause.
  sleep(1);
}

export function teardown(fixture: Fixture): void {
  const jar = http.cookieJar();
  for (const client of fixture.clients) {
    jar.clear(baseUrl);
    jar.set(baseUrl, 'CHESS_SESSION', client.sessionCookie, { path: '/' });
    const token = csrf('teardown');
    const response = post('/auth/logout', {}, token, 'teardown');
    expectStatus(response, 204, 'teardown: logout');
  }
}