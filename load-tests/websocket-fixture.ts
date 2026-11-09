import http from 'k6/http';
import { check, fail } from 'k6';
import type { CookieJar, Params, Response } from 'k6/http';

export interface Client {
  id: number;
  jar: CookieJar;
  csrfToken: string;
}

interface QueueState {
  status: 'NOT_QUEUED' | 'WAITING' | 'MATCHED';
  gameId: number | null;
}

export interface GameState {
  id: number;
  whitePlayerId: number;
  blackPlayerId: number;
  version: number;
  currentFen: string;
  status: 'IN_PROGRESS' | 'FINISHED';
  result: string | null;
  terminationReason: string | null;
  finishedAt: string | null;
}


export const baseUrl = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/+$/, '');
export const gameCount = Number(__ENV.GAMES || '1');
if (!Number.isInteger(gameCount) || gameCount < 1 || gameCount > 1000) {
  throw new Error('GAMES must be an integer from 1 to 1000');
}


let phase = 'setup';

export function requireThat(condition: boolean, label: string): void {
  if (!check(condition, { [label]: value => value })) {
    fail(label);
  }
}

export function expectStatus(response: Response, status: number, label: string): void {
  requireThat(response.status === status, `${label}: expected HTTP ${status}, got ${response.status}`);
}

export function readJson<T>(response: Response, label: string): T {
  try {
    return response.json() as unknown as T;
  } catch {
    requireThat(false, `${label}: valid JSON`);
    throw new Error('Unreachable after failed JSON check');
  }
}

export function params(client: Client, name: string): Params {
  return {
    jar: client.jar,
    redirects: 0,
    timeout: '10s',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': client.csrfToken,
    },
    tags: { name, phase },
  };
}

function get(client: Client, path: string, name: string): Response {
  const response = http.get(`${baseUrl}/api/v1${path}`, params(client, name));
  expectStatus(response, 200, name);
  return response;
}

function post(client: Client, path: string, body: Record<string, unknown>, name: string): Response {
  const response = http.post(`${baseUrl}/api/v1${path}`, JSON.stringify(body), params(client, name));
  return response;
}

function refreshCsrf(client: Client): void {
  const response = http.get(`${baseUrl}/api/v1/auth/csrf`, params(client, 'GET /auth/csrf'));
  expectStatus(response, 204, 'Initialize CSRF');
  const values = client.jar.cookiesForURL(`${baseUrl}/api/v1/`)['XSRF-TOKEN'];
  requireThat(!!values && values.length === 1, 'One CSRF cookie is available');
  client.csrfToken = values[0];
}

export function registerAndLogin(client: Client, username: string): void {
  const email = `${username}@example.com`;
  const password = 'LoadTestOnly-123!';
  refreshCsrf(client);

  const registration = post(client, '/auth/register', { username, email, password }, 'POST /auth/register');
  expectStatus(registration, 201, 'Register Player');

  const login = post(client, '/auth/login', { email, password }, 'POST /auth/login');
  expectStatus(login, 200, 'Login Player');
  const body = readJson<{ id: number }>(login, 'Login');
  requireThat(body !== null && Number.isSafeInteger(body.id) && body.id > 0, 'Login supplies a Player ID');
  client.id = body.id;
  refreshCsrf(client);
}

function queueStatus(client: Client): QueueState {
  return readJson<QueueState>(get(client, '/matchmaking/queue/me', 'GET /matchmaking/queue/me'), 'Queue');
}

function readGame(client: Client, gameId: number): GameState {
  return readJson<GameState>(get(client, `/games/${gameId}`, 'GET /games/:id'), 'Game');
}


export interface SessionFixture {
  id: number;
  sessionCookie: string;
  csrfToken: string;
}

export interface GameFixture {
  game: GameState;
  white: SessionFixture;
  black: SessionFixture;
}

export interface Fixture {
  games: GameFixture[];
}

export function serialize(client: Client): SessionFixture {
  const values = client.jar.cookiesForURL(`${baseUrl}/api/v1/`)['CHESS_SESSION'];
  requireThat(!!values && values.length === 1, 'One authenticated session cookie exists');
  return { id: client.id, sessionCookie: values[0], csrfToken: client.csrfToken };
}

export function restore(fixture: SessionFixture): Client {
  const jar = new http.CookieJar();
  jar.set(baseUrl, 'CHESS_SESSION', fixture.sessionCookie, { path: '/' });
  jar.set(baseUrl, 'XSRF-TOKEN', fixture.csrfToken, { path: '/' });
  return { id: fixture.id, jar, csrfToken: fixture.csrfToken };
}

export function setup(): Fixture {
  phase = 'setup';
  const games: GameFixture[] = [];
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 1000000)}`;

  // Pair sequentially before VUs start: no cross-pairing between orchestrators.
  for (let index = 0; index < gameCount; index++) {
    const first: Client = { id: 0, jar: new http.CookieJar(), csrfToken: '' };
    const second: Client = { id: 0, jar: new http.CookieJar(), csrfToken: '' };
    registerAndLogin(first, `lc_${suffix}_${index}a`);
    registerAndLogin(second, `lc_${suffix}_${index}b`);

    const firstJoin = post(first, '/matchmaking/queue', {}, 'POST /matchmaking/queue');
    expectStatus(firstJoin, 200, 'setup: first queue join');
    requireThat(readJson<QueueState>(firstJoin, 'First join').status === 'WAITING', 'setup: first Player waits');

    const secondJoin = post(second, '/matchmaking/queue', {}, 'POST /matchmaking/queue');
    expectStatus(secondJoin, 200, 'setup: second queue join');
    const match = readJson<QueueState>(secondJoin, 'Second join');
    requireThat(match.status === 'MATCHED' && typeof match.gameId === 'number' &&
      Number.isSafeInteger(match.gameId) && match.gameId > 0, 'setup: valid matched game');
    if (typeof match.gameId !== 'number') {
      fail('setup: missing game ID');
    }

    const firstStatus = queueStatus(first);
    requireThat(firstStatus.status === 'MATCHED' && firstStatus.gameId === match.gameId, 'setup: same game discovered by both Players');
    const game = readGame(first, match.gameId);
    requireThat(game.id === match.gameId && game.status === 'IN_PROGRESS' &&
      Number.isSafeInteger(game.version) && game.version >= 0 &&
      ((game.whitePlayerId === first.id && game.blackPlayerId === second.id) ||
        (game.whitePlayerId === second.id && game.blackPlayerId === first.id)), 'setup: correct active participants');

    games.push({
      game,
      white: serialize(game.whitePlayerId === first.id ? first : second),
      black: serialize(game.blackPlayerId === first.id ? first : second),
    });
  }

  console.log(`[ws phase] Setup complete: ${games.length} games paired at ${new Date().toISOString()}`);
  return { games };
}


export function teardown(fixture: Fixture): void {
  phase = 'teardown';
  console.log(`[ws phase] Teardown starts at ${new Date().toISOString()}`);
  for (const game of fixture.games) {
    for (const session of [game.white, game.black]) {
      const client = restore(session);
      const response = post(client, '/auth/logout', {}, 'POST /auth/logout');
      check(response, { 'teardown: logout returns 204': value => value.status === 204 });
    }
  }
}
