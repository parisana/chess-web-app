import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
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
const gameCount = Number(__ENV.GAMES || '1');
if (!Number.isInteger(gameCount) || gameCount < 1 || gameCount > 100) {
  throw new Error('GAMES must be an integer from 1 to 100');
}

const gamesCompleted = new Counter('games_completed');
const moveRequests = new Counter('gameplay_move_requests');
const gameFailures = new Rate('gameplay_failures');
const moveDuration = new Trend('gameplay_move_duration', true);
const readDuration = new Trend('gameplay_game_read_duration', true);
const resignDuration = new Trend('gameplay_resignation_duration', true);
const gameDuration = new Trend('gameplay_game_duration', true);
let phase = 'gameplay';
const line = [
  { from: 'e2', to: 'e4', san: 'e4' },
  { from: 'e7', to: 'e5', san: 'e5' },
  { from: 'g1', to: 'f3', san: 'Nf3' },
  { from: 'b8', to: 'c6', san: 'Nc6' },
];

// Four legal opening plies, then four reversible knight cycles: 20 plies total.
// Current rules do not implement repetition/move-count draws.
for (let cycle = 0; cycle < 4; cycle++) {
  line.push(
    { from: 'f3', to: 'g1', san: 'Ng1' },
    { from: 'c6', to: 'b8', san: 'Nb8' },
    { from: 'g1', to: 'f3', san: 'Nf3' },
    { from: 'b8', to: 'c6', san: 'Nc6' },
  );
}


export const options: Options = {
  setupTimeout: '4m',
  teardownTimeout: '2m',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    independent_games: {
      executor: 'per-vu-iterations',
      vus: gameCount,
      iterations: 1,
      maxDuration: '3m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    games_completed: [`count==${gameCount}`],
    gameplay_move_requests: [`count==${gameCount * line.length}`],
    gameplay_failures: ['rate==0'],
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
    tags: { name, phase },
  };
}

function get(client: Client, path: string, name: string): Response {
  const response = http.get(`${baseUrl}/api/v1${path}`, params(client, name));
  if (phase === 'gameplay' && name === 'GET /games/:id') {
    readDuration.add(response.timings.duration);
  }
  expectStatus(response, 200, name);
  return response;
}

function post(client: Client, path: string, body: Record<string, unknown>, name: string): Response {
  const response = http.post(`${baseUrl}/api/v1${path}`, JSON.stringify(body), params(client, name));
  if (phase === 'gameplay' && path.endsWith('/moves')) {
    moveRequests.add(1);
    moveDuration.add(response.timings.duration);
  } else if (phase === 'gameplay' && path.endsWith('/resignation')) {
    resignDuration.add(response.timings.duration);
  }
  return response;
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


interface SessionFixture {
  id: number;
  sessionCookie: string;
  csrfToken: string;
}

interface GameFixture {
  game: GameState;
  white: SessionFixture;
  black: SessionFixture;
}

interface Fixture {
  games: GameFixture[];
}

function serialize(client: Client): SessionFixture {
  const values = client.jar.cookiesForURL(`${baseUrl}/api/v1/`)['CHESS_SESSION'];
  requireThat(!!values && values.length === 1, 'One authenticated session cookie exists');
  return { id: client.id, sessionCookie: values[0], csrfToken: client.csrfToken };
}

function restore(fixture: SessionFixture): Client {
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

  console.log(`[phase] Setup complete: ${games.length} games paired at ${new Date().toISOString()}`);
  return { games };
}

export default function (fixture: Fixture): void {
  phase = 'gameplay';
  if (execution.vu.idInTest === 1) {
    console.log(`[phase] VU 1 gameplay starts at ${new Date().toISOString()}`);
  }
  const assigned = fixture.games[execution.vu.idInTest - 1];
  requireThat(assigned !== undefined, 'VU has exactly one pre-paired game');
  const white = restore(assigned.white);
  const black = restore(assigned.black);
  let game = assigned.game;
  const gameId = game.id;
  let completed = false;
  const startedAt = Date.now();

  try {
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
      const accepted = readJson<MoveResult>(response, 'Move');
      requireThat(accepted.gameId === gameId && accepted.version === game.version + 1 &&
        accepted.status === 'IN_PROGRESS' && typeof accepted.currentFen === 'string' &&
        accepted.currentFen !== game.currentFen, `Move ${index + 1} advances accepted state`);
      game = readGame(observer, gameId);
      requireThat(game.id === gameId && game.version === accepted.version &&
        game.currentFen === accepted.currentFen && game.status === 'IN_PROGRESS', 'Opponent reads committed move');

      // A shared turn interval per game, not one request/second per Player.
      if (index + 1 < line.length) {
        sleep(1);
      }
    }

    assertHistory(white, gameId, game.version, game.currentFen);
    const versionBeforeResign = game.version;
    const finalFen = game.currentFen;
    const response = post(white, `/games/${gameId}/resignation`, {
      expectedVersion: versionBeforeResign,
    }, 'POST /games/:id/resignation');
    expectStatus(response, 200, 'White resigns');
    game = readJson<GameState>(response, 'Resignation');
    requireThat(game.id === gameId && game.status === 'FINISHED' &&
      game.result === 'BLACK_WIN' && game.terminationReason === 'RESIGNATION' &&
      typeof game.finishedAt === 'string' && game.currentFen === finalFen &&
      game.version === versionBeforeResign + 1, 'Resignation completes this game');

    const finalView = readGame(black, gameId);
    requireThat(finalView.id === gameId && finalView.version === game.version &&
      finalView.status === 'FINISHED' && finalView.result === 'BLACK_WIN' &&
      finalView.terminationReason === 'RESIGNATION', 'Opponent sees committed completion');
    assertHistory(black, gameId, game.version, finalFen);
    requireThat(queueStatus(white).status === 'NOT_QUEUED' &&
      queueStatus(black).status === 'NOT_QUEUED', 'Both reservations released');

    for (const client of [white, black]) {
      const stats = readJson<Statistics>(get(client, '/players/me/statistics', 'GET /players/me/statistics'), 'Statistics');
      requireThat(stats.playerId === client.id && stats.gamesPlayed === 1 && stats.draws === 0 &&
        stats.wins === (client === black ? 1 : 0) &&
        stats.losses === (client === white ? 1 : 0), 'Outcome statistics applied once');
    }
    completed = true;
    gamesCompleted.add(1);
  } finally {
    gameDuration.add(Date.now() - startedAt);
    gameFailures.add(!completed);
  }
}

export function teardown(fixture: Fixture): void {
  phase = 'teardown';
  console.log(`[phase] Teardown starts at ${new Date().toISOString()}`);
  for (const game of fixture.games) {
    for (const session of [game.white, game.black]) {
      const client = restore(session);
      const response = post(client, '/auth/logout', {}, 'POST /auth/logout');
      check(response, { 'teardown: logout returns 204': value => value.status === 204 });
    }
  }
}
