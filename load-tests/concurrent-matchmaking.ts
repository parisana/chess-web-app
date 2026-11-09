import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import type { Options } from 'k6/options';
import type { Response } from 'k6/http';
import type { Client, GameState, SessionFixture } from './websocket-fixture.ts';
import { baseUrl, gameCount, registerAndLogin, requireThat, restore, serialize } from './websocket-fixture.ts';

interface Fixture {
  players: SessionFixture[];
}

interface QueueState {
  status: 'NOT_QUEUED' | 'WAITING' | 'MATCHED';
  gameId: number | null;
}

const playerCount = gameCount * 2;
const joinDuration = new Trend('matchmaking_join_duration', true);
const matchDuration = new Trend('matchmaking_observed_match_duration', true);
const matchedPlayers = new Counter('matchmaking_players_matched');
const uniqueGames = new Counter('matchmaking_unique_games');
const playerFailures = new Rate('matchmaking_player_failures');
const matchTimeoutMs = 15000;
let phase = 'setup';

export const options: Options = {
  setupTimeout: '4m',
  teardownTimeout: '3m',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    queue_players: {
      executor: 'per-vu-iterations',
      vus: playerCount,
      iterations: 1,
      maxDuration: '1m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    matchmaking_players_matched: [`count==${playerCount}`],
    matchmaking_unique_games: [`count==${gameCount}`],
    matchmaking_player_failures: ['rate==0'],
  },
};

function request(client: Client, method: string, path: string, body: Record<string, unknown> | null, name: string): Response {
  return http.request(method, `${baseUrl}/api/v1${path}`, body === null ? null : JSON.stringify(body), {
    jar: client.jar,
    redirects: 0,
    timeout: '10s',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': client.csrfToken },
    tags: { name, phase },
  });
}

function json<T>(response: Response): T | null {
  try {
    return response.json() as unknown as T;
  } catch {
    check(false, { 'Matchmaking response is valid JSON': value => value });
    return null;
  }
}

function verify(condition: boolean, label: string): boolean {
  return check(condition, { [label]: value => value });
}

export function setup(): Fixture {
  const players: SessionFixture[] = [];
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
  for (let index = 0; index < playerCount; index++) {
    const client: Client = { id: 0, jar: new http.CookieJar(), csrfToken: '' };
    registerAndLogin(client, `lm_${suffix}_${index}`);
    players.push(serialize(client));
  }
  console.log(`[matchmaking phase] ${players.length} authenticated Players ready at ${new Date().toISOString()}`);
  return { players };
}

export default function (fixture: Fixture): void {
  phase = 'matchmaking';
  const session = fixture.players[execution.vu.idInTest - 1];
  requireThat(session !== undefined, 'Matchmaking VU owns one distinct Player');
  const client = restore(session);
  const cohort = new Set(fixture.players.map(player => player.id));
  let success = false;
  const startedAt = Date.now();
  if (execution.vu.idInTest === 1) console.log(`[matchmaking phase] Queue entry begins at ${new Date().toISOString()}`);

  try {
    // All VUs enter independently. No sequential pre-pairing and no assumed partner.
    const joined = request(client, 'POST', '/matchmaking/queue', {}, 'POST /matchmaking/queue');
    joinDuration.add(joined.timings.duration);
    requireThat(joined.status === 200, 'Concurrent queue join returns 200');
    let state = json<QueueState>(joined);
    if (state === null) fail('Queue join did not supply JSON');

    while (state.status === 'WAITING' && Date.now() - startedAt < matchTimeoutMs) {
      sleep(0.25);
      const response = request(client, 'GET', '/matchmaking/queue/me', null, 'GET /matchmaking/queue/me');
      requireThat(response.status === 200, 'Queue discovery returns 200');
      state = json<QueueState>(response);
      if (state === null) fail('Queue discovery did not supply JSON');
    }
    requireThat(state.status === 'MATCHED' && Number.isSafeInteger(state.gameId) &&
      (state.gameId as number) > 0, 'Player discovers a matched game within the observation deadline');
    requireThat(Date.now() - startedAt <= matchTimeoutMs, 'Match observation is within the deadline');
    const response = request(client, 'GET', `/games/${state.gameId}`, null, 'GET /games/:id');
    requireThat(response.status === 200, 'Matched game is readable by participant');
    const game = json<GameState>(response);
    requireThat(game !== null && game.id === state.gameId && game.status === 'IN_PROGRESS' &&
      game.whitePlayerId !== game.blackPlayerId && cohort.has(game.whitePlayerId) &&
      cohort.has(game.blackPlayerId) &&
      (game.whitePlayerId === client.id || game.blackPlayerId === client.id),
      'Matched game contains this Player and another distinct cohort Player');

    // Includes join, polling and game verification, not pure server pairing time.
    matchDuration.add(Date.now() - startedAt);
    matchedPlayers.add(1);
    success = true;
  } finally {
    playerFailures.add(!success);
  }
}

export function teardown(fixture: Fixture): void {
  phase = 'teardown';
  uniqueGames.add(0);
  console.log(`[matchmaking phase] Validation and cleanup begin at ${new Date().toISOString()}`);
  const clients = new Map(fixture.players.map(session => [session.id, restore(session)]));
  const games = new Map<number, GameState>();
  const observedAssignments = new Map<number, number>();

  try {
    // Wait until all VUs finish before checking cross-Player invariants or resigning.
    for (const client of clients.values()) {
      const response = request(client, 'GET', '/matchmaking/queue/me', null, 'GET /matchmaking/queue/me validation');
      if (!verify(response.status === 200, 'Validation queue read returns 200')) continue;
      const state = json<QueueState>(response);
      if (!verify(state !== null && state.status === 'MATCHED' && Number.isSafeInteger(state.gameId),
        'Every cohort Player retains one observed matched assignment')) continue;
      if (state === null || state.gameId === null) continue;
      observedAssignments.set(client.id, state.gameId);
      const read = request(client, 'GET', `/games/${state.gameId}`, null, 'GET /games/:id validation');
      if (!verify(read.status === 200, 'Validation game read returns 200')) continue;
      const game = json<GameState>(read);
      const valid = game !== null && game.id === state.gameId && game.status === 'IN_PROGRESS' &&
        game.whitePlayerId !== game.blackPlayerId && clients.has(game.whitePlayerId) && clients.has(game.blackPlayerId) &&
        (game.whitePlayerId === client.id || game.blackPlayerId === client.id);
      if (!verify(valid, 'Every validation game has distinct cohort participants')) continue;
      if (game !== null) games.set(game.id, game);
    }

    verify(observedAssignments.size === playerCount, 'All cohort Players have discovered assignments');
    verify(games.size === gameCount, 'Exactly the expected number of distinct games is observed');
    const appearances = new Map<number, number>();
    for (const game of games.values()) {
      for (const playerId of [game.whitePlayerId, game.blackPlayerId]) {
        appearances.set(playerId, (appearances.get(playerId) || 0) + 1);
        verify(observedAssignments.get(playerId) === game.id, 'Both participants discover the same game');
      }
    }
    for (const playerId of clients.keys()) {
      verify(appearances.get(playerId) === 1, 'Each cohort Player appears in exactly one observed game');
    }
    uniqueGames.add(games.size);
  } finally {
    // Clean up only games whose two participants belong to this fixture.
    // Never mutate an unrelated Player's game if an old queue entry contaminated pairing.
    for (const game of games.values()) {
      const white = clients.get(game.whitePlayerId);
      if (white === undefined) continue;
      const response = request(white, 'POST', `/games/${game.id}/resignation`, {
        expectedVersion: game.version,
      }, 'POST /games/:id/resignation cleanup');
      if (!verify(response.status === 200, 'Cleanup resignation returns 200')) continue;
      const final = json<GameState>(response);
      verify(final !== null && final.id === game.id && final.status === 'FINISHED' &&
        final.result === 'BLACK_WIN' && final.terminationReason === 'RESIGNATION',
        'Cleanup completes the observed game');
    }
    for (const client of clients.values()) {
      const response = request(client, 'GET', '/matchmaking/queue/me', null, 'GET /matchmaking/queue/me cleanup');
      if (verify(response.status === 200, 'Cleanup queue read returns 200')) {
        const state = json<QueueState>(response);
        if (state?.status === 'WAITING') {
          // A partially failed cohort may leave waiting entries. Remove them explicitly.
          const left = request(client, 'DELETE', '/matchmaking/queue/me', null, 'DELETE /matchmaking/queue/me cleanup');
          verify(left.status === 204, 'Unmatched Player can leave queue during cleanup');
        } else {
          verify(state !== null && state.status === 'NOT_QUEUED', 'Completed game releases observed Player reservation');
        }
      }
      const logout = request(client, 'POST', '/auth/logout', {}, 'POST /auth/logout');
      verify(logout.status === 204, 'Cleanup logout returns 204');
    }
  }
}
