import http from 'k6/http';
import { check, fail } from 'k6';
import type { Options } from 'k6/options';
import type { Response } from 'k6/http';

interface PlayerProfile {
  id: number;
  username: string;
  email: string;
}

interface PlayerStatistics {
  playerId: number;
  gamesPlayed: number;
}

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/+$/, '');

export const options: Options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
  },
};

function expectStatus(response: Response, expectedStatus: number, label: string): void {
  const passed = check(response, {
    [label]: result => result.status === expectedStatus,
  });

  if (!passed) {
    fail(`${label}: expected ${expectedStatus}, received ${response.status}`);
  }
}

function requireCookie(name: string, path: string): string {
  const cookies = http.cookieJar().cookiesForURL(`${baseUrl}${path}`);
  const values = cookies[name];

  const present = check(values, {
    [`${name} cookie is available`]: value =>
      value !== undefined && value.length === 1,
  });

  if (!present) {
    fail(`Expected exactly one ${name} cookie`);
  }

  return values[0];
}

function initializeCsrf(): string {
  const response = http.get(`${baseUrl}/api/v1/auth/csrf`, {
    redirects: 0,
    tags: { name: 'GET /auth/csrf' },
  });

  expectStatus(response, 204, 'CSRF initialization returns 204');
  return requireCookie('XSRF-TOKEN', '/api/v1/auth/login');
}

function postJson(path: string, body: Record<string, unknown>, csrfToken: string, name: string): Response {
  return http.post(`${baseUrl}${path}`, JSON.stringify(body), {
    redirects: 0,
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    tags: { name },
  });
}

export default function (): void {
  // Each run leaves one identifiable test Player in the isolated database.
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
  const username = `lt_${suffix}`;
  const email = `${username}@example.com`;
  const password = 'LoadTestOnly-123!';

  let csrfToken = initializeCsrf();

  const registration = postJson(
    '/api/v1/auth/register',
    { username, email, password },
    csrfToken,
    'POST /auth/register',
  );

  expectStatus(registration, 201, 'Registration returns 201');

  const login = postJson(
    '/api/v1/auth/login',
    { email, password },
    csrfToken,
    'POST /auth/login',
  );

  expectStatus(login, 200, 'Login returns 200');

  const playerId = login.json('id');
  const validPlayer = check(playerId, {
    'Login returns a positive Player ID': id =>
      typeof id === 'number' && Number.isSafeInteger(id) && id > 0,
  });

  if (!validPlayer) {
    fail('Login response has an invalid Player ID');
  }

  requireCookie('CHESS_SESSION', '/api/v1/players/me');

  // Login resets CSRF. Obtain the current token before another write.
  csrfToken = initializeCsrf();

  const profile = http.get(`${baseUrl}/api/v1/players/me`, {
    redirects: 0,
    tags: { name: 'GET /players/me' },
  });

  expectStatus(profile, 200, 'Authenticated profile read returns 200');

  // These assertions describe expected JSON; checks verify the relevant fields.
  const profileBody = profile.json() as unknown as PlayerProfile;

  check(profileBody, {
    'Profile belongs to the registered Player': value =>
      value !== null &&
      value.id === playerId &&
      value.username === username &&
      value.email === email,
  });

  const statistics = http.get(`${baseUrl}/api/v1/players/me/statistics`, {
    redirects: 0,
    tags: { name: 'GET /players/me/statistics' },
  });

  expectStatus(statistics, 200, 'Statistics read returns 200');

  const statisticsBody = statistics.json() as unknown as PlayerStatistics;

  check(statisticsBody, {
    'New Player statistics belong to this Player and contain no games': value =>
      value !== null && value.playerId === playerId && value.gamesPlayed === 0,
  });

  const logout = postJson(
    '/api/v1/auth/logout',
    {},
    csrfToken,
    'POST /auth/logout',
  );

  expectStatus(logout, 204, 'Logout returns 204');

  const afterLogout = http.get(`${baseUrl}/api/v1/players/me`, {
    redirects: 0,
    // A 401 is the intended outcome here, not an unexpected HTTP failure.
    responseCallback: http.expectedStatuses(401),
    tags: { name: 'GET /players/me after logout' },
  });

  expectStatus(afterLogout, 401, 'Protected read after logout returns 401');
}