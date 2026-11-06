import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import type { Options } from 'k6/options';
import type { CookieJar, Params, Response } from 'k6/http';

interface Client {
  id: number;
  jar: CookieJar;
  csrfToken: string;
}

interface QueueState {
  status: 'NOT_QUEUED' | 'WAITING' | 'MATCHED';
  gameId: number | null;
}

interface GameState {
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

interface MoveResult {
  gameId: number;
  version: number;
  currentFen: string;
  status: string;
}

interface RecordedMove {
  sequenceNumber: number;
  playerId: number;
  from: string;
  to: string;
  san: string;
  resultingFen: string;
}

interface History {
  gameId: number;
  version: number;
  historyComplete: boolean;
  recordedMoveCount: number;
  moves: RecordedMove[];
}

interface Statistics {
  playerId: number;
  wins: number;
  draws: number;
  losses: number;
  gamesPlayed: number;
}

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/+$/, '');
const gamesCompleted = new Counter('smoke_games_completed');
const line = [
  { from: 'e2', to: 'e4', san: 'e4' },
  { from: 'e7', to: 'e5', san: 'e5' },
  { from: 'g1', to: 'f3', san: 'Nf3' },
  { from: 'b8', to: 'c6', san: 'Nc6' },
];

export const options: Options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    smoke_games_completed: ['count==1'],
  },
};

function requireThat(condition: boolean, label: string): void {
  if (!check(condition, { [label]: value => value })) {
    fail(label);
  }
}

function expectStatus(response: Response, status: number, label: string): void {
  requireThat(response.status === status, `${label}: expected HTTP ${status}, got ${response.status}`);
}

function readJson<T>(response: Response, label: string): T {
  try {
    return response.json() as unknown as T;
  } catch {
    requireThat(false, `${label}: valid JSON`);
    throw new Error('Unreachable after failed JSON check');
  }
}

function params(client: Client, name: string): Params {
  return {
    jar: client.jar,
    redirects: 0,
    timeout: '10s',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': client.csrfToken,
    },
    tags: { name },
  };
}

function get(client: Client, path: string, name: string): Response {
  const response = http.get(`${baseUrl}/api/v1${path}`, params(client, name));
  expectStatus(response, 200, name);
  return response;
}

function post(client: Client, path: string, body: Record<string, unknown>, name: string): Response {
  return http.post(`${baseUrl}/api/v1${path}`, JSON.stringify(body), params(client, name));
}

function refreshCsrf(client: Client): void {
  const response = http.get(`${baseUrl}/api/v1/auth/csrf`, params(client, 'GET /auth/csrf'));
  expectStatus(response, 204, 'Initialize CSRF');
  const values = client.jar.cookiesForURL(`${baseUrl}/api/v1/`)['XSRF-TOKEN'];
  requireThat(!!values && values.length === 1, 'One CSRF cookie is available');
  client.csrfToken = values[0];
}

function registerAndLogin(client: Client, username: string): void {
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

function assertHistory(client: Client, gameId: number, version: number, fen: string): void {
  const history = readJson<History>(get(client, `/games/${gameId}/moves`, 'GET /games/:id/moves'), 'History');
  requireThat(history !== null && history.gameId === gameId && history.version === version &&
    history.historyComplete && history.recordedMoveCount === line.length &&
    Array.isArray(history.moves) && history.moves.length === line.length, 'History has the expected captured version and count');

  for (let index = 0; index < line.length; index++) {
    const move = history.moves[index];
    requireThat(move !== null && move.sequenceNumber === index + 1 &&
      move.from === line[index].from && move.to === line[index].to &&
      move.san === line[index].san, `History entry ${index + 1} has the expected coordinates and SAN`);
  }
  requireThat(history.moves[line.length - 1].resultingFen === fen, 'History ends at the accepted board');
}

export default function (): void {
  // One VU orchestrates both Players, each using a separate explicit cookie jar.
  const first: Client = { id: 0, jar: new http.CookieJar(), csrfToken: '' };
  const second: Client = { id: 0, jar: new http.CookieJar(), csrfToken: '' };
  const clients = [first, second];
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 1000000)}`;

  try {
    registerAndLogin(first, `lg_${suffix}_a`);
    registerAndLogin(second, `lg_${suffix}_b`);
    requireThat(queueStatus(first).status === 'NOT_QUEUED' &&
      queueStatus(second).status === 'NOT_QUEUED', 'Both new Players start outside matchmaking');

    const firstJoin = post(first, '/matchmaking/queue', {}, 'POST /matchmaking/queue');
    expectStatus(firstJoin, 200, 'First queue join');
    requireThat(readJson<QueueState>(firstJoin, 'First join').status === 'WAITING', 'First Player waits for the second');

    const secondJoin = post(second, '/matchmaking/queue', {}, 'POST /matchmaking/queue');
    expectStatus(secondJoin, 200, 'Second queue join');
    const match = readJson<QueueState>(secondJoin, 'Second join');
    requireThat(match.status === 'MATCHED' && typeof match.gameId === 'number' &&
      Number.isSafeInteger(match.gameId) && match.gameId > 0, 'Second join returns a matched game');
    if (typeof match.gameId !== 'number') {
      fail('Matched game has no numeric ID');
    }
    const gameId = match.gameId;
    const firstStatus = queueStatus(first);
    requireThat(firstStatus.status === 'MATCHED' && firstStatus.gameId === gameId, 'Both Players discover the same game');

    let game = readGame(first, gameId);
    requireThat(game.id === gameId && game.status === 'IN_PROGRESS' &&
      Number.isSafeInteger(game.version) && game.version >= 0 &&
      typeof game.currentFen === 'string' &&
      ((game.whitePlayerId === first.id && game.blackPlayerId === second.id) ||
        (game.whitePlayerId === second.id && game.blackPlayerId === first.id)), 'Game contains precisely the two fixture Players');
    const white = game.whitePlayerId === first.id ? first : second;
    const black = game.blackPlayerId === first.id ? first : second;
    const otherView = readGame(second, gameId);
    requireThat(otherView.id === game.id && otherView.version === game.version &&
      otherView.currentFen === game.currentFen, 'Both participants read the same initial board');

    for (let index = 0; index < line.length; index++) {
      const actor = index % 2 === 0 ? white : black;
      const observer = index % 2 === 0 ? black : white;
      const move = line[index];
      const response = post(actor, `/games/${gameId}/moves`, {
        expectedVersion: game.version,
        from: move.from,
        to: move.to,
        promotion: null,
      }, 'POST /games/:id/moves');
      expectStatus(response, 200, `Accept move ${index + 1}`);
      const accepted = readJson<MoveResult>(response, 'Accepted move');
      requireThat(accepted.gameId === gameId && accepted.version === game.version + 1 &&
        accepted.status === 'IN_PROGRESS' && typeof accepted.currentFen === 'string' &&
        accepted.currentFen !== game.currentFen, `Move ${index + 1} advances the accepted state`);
      game = readGame(observer, gameId);
      requireThat(game.id === gameId && game.version === accepted.version &&
        game.currentFen === accepted.currentFen && game.status === 'IN_PROGRESS', 'Opponent HTTP read sees the accepted move');
    }

    assertHistory(white, gameId, game.version, game.currentFen);
    const beforeResignVersion = game.version;
    const finalFen = game.currentFen;
    const resignation = post(white, `/games/${gameId}/resignation`, {
      expectedVersion: beforeResignVersion,
    }, 'POST /games/:id/resignation');
    expectStatus(resignation, 200, 'White resigns');
    game = readJson<GameState>(resignation, 'Resignation');
    requireThat(game.id === gameId && game.version === beforeResignVersion + 1 &&
      game.status === 'FINISHED' && game.result === 'BLACK_WIN' &&
      game.terminationReason === 'RESIGNATION' && typeof game.finishedAt === 'string' &&
      game.currentFen === finalFen, 'Resignation finishes the game without changing the board');

    const finalView = readGame(black, gameId);
    requireThat(finalView.version === game.version && finalView.status === 'FINISHED' &&
      finalView.result === 'BLACK_WIN' && finalView.terminationReason === 'RESIGNATION', 'Opponent reads the committed result');
    assertHistory(black, gameId, game.version, finalFen);
    requireThat(queueStatus(white).status === 'NOT_QUEUED' &&
      queueStatus(black).status === 'NOT_QUEUED', 'Completion releases both active-game reservations');

    for (const client of clients) {
      const stats = readJson<Statistics>(get(client, '/players/me/statistics', 'GET /players/me/statistics'), 'Statistics');
      requireThat(stats.playerId === client.id && stats.gamesPlayed === 1 && stats.draws === 0 &&
        stats.wins === (client === black ? 1 : 0) &&
        stats.losses === (client === white ? 1 : 0), 'Completion updates each Player outcome exactly once');
    }

    gamesCompleted.add(1);
  } finally {
    // Logout only. Never silently retry a move/resignation or cancel an active game.
    for (const client of clients) {
      if (client.id === 0) {
        continue;
      }
      const logout = post(client, '/auth/logout', {}, 'POST /auth/logout');
      check(logout, { 'Cleanup logout returns 204': response => response.status === 204 });
    }
  }
}
