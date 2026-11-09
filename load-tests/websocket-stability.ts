import http from 'k6/http';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { WebSocket } from 'k6/websockets';
import { clearInterval, clearTimeout, setInterval, setTimeout } from 'k6/timers';
import type { Options } from 'k6/options';
import type { Client, Fixture, GameState, SessionFixture } from './websocket-fixture.ts';
import { baseUrl, expectStatus, gameCount, readJson, requireThat, restore } from './websocket-fixture.ts';

export { setup, teardown } from './websocket-fixture.ts';

const socketUrl = __ENV.WS_URL || `${baseUrl.replace(/^http/, 'ws')}/api/v1/ws`;
const origin = __ENV.WS_ORIGIN || 'http://localhost:4200';
const destination = __ENV.WS_DESTINATION || '/user/queue/updates';
const settleMs = integerSetting('WS_SUBSCRIBE_SETTLE_MS', 2000, 0, 10000);
const deliveryTimeoutMs = integerSetting('WS_DELIVERY_TIMEOUT_MS', 5000, 100, 30000);
const readyTimeoutMs = 10000;
const holdMs = 180000;
if (gameCount > 1000) throw new Error('Stability workload is capped at 1000 games');
const heartbeatSent = new Counter('ws_heartbeats_sent');
const heartbeatReceived = new Counter('ws_heartbeats_received');
const idleHeartbeatConnections = new Counter('ws_idle_heartbeat_connections');
const livenessTimeouts = new Counter('ws_liveness_timeouts');
const heldDuration = new Trend('ws_stability_hold_duration', true);

const connections = new Counter('ws_stomp_connected');
const notifications = new Counter('ws_expected_notifications');
const duplicates = new Counter('ws_duplicate_notifications');
const unexpected = new Counter('ws_unexpected_notifications');
const socketErrors = new Counter('ws_connection_errors');
const deliveryFailures = new Rate('ws_delivery_failures');
const deliveryDuration = new Trend('ws_command_to_notification', true);
const moveDuration = new Trend('ws_gameplay_move_duration', true);
const reconciledDuration = new Trend('ws_command_to_reconciled_state', true);
const completed = new Counter('ws_games_completed');
const gameFailures = new Rate('ws_gameplay_failures');
const moves = [
  { from: 'e2', to: 'e4' },
  { from: 'e7', to: 'e5' },
  { from: 'g1', to: 'f3' },
  { from: 'b8', to: 'c6' },
];

// As in the established HTTP gameplay batch, use four reversible knight cycles.
for (let cycle = 0; cycle < 4; cycle++) {
  moves.push(
    { from: 'f3', to: 'g1' },
    { from: 'c6', to: 'b8' },
    { from: 'g1', to: 'f3' },
    { from: 'b8', to: 'c6' },
  );
}

export const options: Options = {
  setupTimeout: '6m',
  teardownTimeout: '2m',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    websocket_stability: {
      executor: 'per-vu-iterations',
      vus: gameCount,
      iterations: 1,
      maxDuration: '5m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    ws_stomp_connected: [`count==${gameCount * 2}`],
    ws_expected_notifications: [`count==${gameCount * 2 * (moves.length + 1)}`],
    ws_unexpected_notifications: ['count==0'],
    ws_connection_errors: ['count==0'],
    ws_delivery_failures: ['rate==0'],
    ws_games_completed: [`count==${gameCount}`],
    ws_gameplay_failures: ['rate==0'],
    ws_idle_heartbeat_connections: [`count==${gameCount * 2}`],
    ws_liveness_timeouts: ['count==0'],
    ws_heartbeats_sent: ['count>0'],
    ws_heartbeats_received: ['count>0'],
    ws_stability_hold_duration: ['min>=180000'],
  },
};

function integerSetting(name: string, fallback: number, minimum: number, maximum: number): number {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} to ${maximum}`);
  }
  return value;
}

function delay(milliseconds: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function frame(command: string, headers: Record<string, string>): string {
  // Only these controlled CONNECT/SUBSCRIBE headers are emitted; no user text.
  for (const value of Object.values(headers)) {
    if (/[\r\n\x00]/.test(value)) {
      throw new Error('STOMP header contains an invalid delimiter');
    }
  }
  return `${command}\n${Object.entries(headers).map(([key, value]) => `${key}:${value}`).join('\n')}\n\n\x00`;
}

interface Update {
  type: string;
  gameId: number;
  version: number;
}

interface Pending {
  version: number;
  startedAt: number;
  timer: ReturnType<typeof setTimeout>;
  resolve: (received: boolean) => void;
}

class PlayerSocket {
  readonly ready: Promise<boolean>;
  private readonly socket: WebSocket;
  private readonly readyTimer: ReturnType<typeof setTimeout>;
  private settleTimer?: ReturnType<typeof setTimeout>;
  private heartbeat?: ReturnType<typeof setInterval>;
  private watchdog?: ReturnType<typeof setInterval>;
  private lastReceivedAt = Date.now();
  private observingIdle = false;
  private idleHeartbeats = 0;
  private resolveReady!: (ready: boolean) => void;
  private readyFinished = false;
  private connected = false;
  private stopped = false;
  private broken = false;
  private buffer = '';
  private pending?: Pending;
  private readonly seen = new Set<number>();

  constructor(private readonly session: SessionFixture, private readonly gameId: number) {
    this.ready = new Promise(resolve => { this.resolveReady = resolve; });
    this.readyTimer = setTimeout(() => this.breakConnection(), readyTimeoutMs);
    this.socket = new WebSocket(socketUrl, null, {
      headers: {
        Origin: origin,
        Cookie: `CHESS_SESSION=${session.sessionCookie}; XSRF-TOKEN=${session.csrfToken}`,
      },
      tags: { name: 'WS /api/v1/ws', phase: 'websocket_stability' },
    });

    this.socket.addEventListener('open', () => {
      this.socket.send(frame('CONNECT', {
        'accept-version': '1.2',
        host: 'localhost',
        'heart-beat': '10000,10000',
        'X-XSRF-TOKEN': session.csrfToken,
      }));
    });
    this.socket.addEventListener('message', event => {
      try {
        if (!('data' in event) || typeof event.data !== 'string') {
          this.breakConnection();
          return;
        }
        this.lastReceivedAt = Date.now();
        this.consume(event.data);
      } catch {
        // Do not log frame bodies/headers, which may contain session details.
        this.breakConnection();
      }
    });
    this.socket.addEventListener('error', () => this.breakConnection());
    this.socket.addEventListener('close', () => {
      if (!this.stopped) this.breakConnection();
    });
  }

  healthy(): boolean {
    return this.connected && !this.broken && !this.stopped;
  }

  observeIdle(active: boolean): void {
    this.observingIdle = active;
  }

  observedIdleHeartbeat(): boolean {
    return this.idleHeartbeats > 0;
  }

  expect(version: number, startedAt: number): Promise<boolean> {
    if (!this.healthy() || this.pending !== undefined) {
      console.warn(
        `[ws expectation rejected] vu=${execution.vu.idInTest} ` +
        `gameId=${this.gameId} version=${version} playerId=${this.session.id} ` +
        `healthy=${this.healthy()} alreadyPending=${this.pending !== undefined}`,
      );
      deliveryFailures.add(true);
      return Promise.resolve(false);
    }

    // Arm before issuing HTTP because the notification can arrive first.
    return new Promise(resolve => {
      const timer = setTimeout(() => {
        console.warn(
          `[ws delivery timeout] vu=${execution.vu.idInTest} ` +
          `gameId=${this.gameId} version=${version} playerId=${this.session.id} ` +
          `elapsedMs=${Date.now() - startedAt} healthy=${this.healthy()}`,
        );
        this.finishPending(false);
      }, deliveryTimeoutMs);

      this.pending = { version, startedAt, timer, resolve };
    });
  }

  stop(): void {
    this.stopped = true;
    clearTimeout(this.readyTimer);
    if (this.settleTimer !== undefined) clearTimeout(this.settleTimer);
    if (this.heartbeat !== undefined) clearInterval(this.heartbeat);
    if (this.watchdog !== undefined) clearInterval(this.watchdog);
    this.finishReady(false);
    this.finishPending(false);
    if (this.socket.readyState === 1) {
      this.socket.send('DISCONNECT\n\n\x00');
    }
    if (this.socket.readyState < 2) this.socket.close();
  }

  private finishReady(ready: boolean): void {
    if (this.readyFinished) return;
    this.readyFinished = true;
    clearTimeout(this.readyTimer);
    this.resolveReady(ready);
  }

  private finishPending(received: boolean): void {
    const pending = this.pending;
    if (pending === undefined) return;
    this.pending = undefined;
    clearTimeout(pending.timer);
    deliveryFailures.add(!received);
    if (received) {
      notifications.add(1);
      deliveryDuration.add(Date.now() - pending.startedAt);
    }
    pending.resolve(received);
  }

  private breakConnection(): void {
    if (this.stopped || this.broken) return;
    this.broken = true;
    socketErrors.add(1);
    if (this.heartbeat !== undefined) clearInterval(this.heartbeat);
    if (this.watchdog !== undefined) clearInterval(this.watchdog);
    if (this.settleTimer !== undefined) clearTimeout(this.settleTimer);
    this.finishReady(false);
    this.finishPending(false);
    if (this.socket.readyState < 2) this.socket.close();
  }

  private consume(chunk: string): void {
    this.buffer += chunk;
    if (this.buffer.length > 65536) throw new Error('Unexpected STOMP frame size');
    this.consumeHeartbeatLines();
    let end = this.buffer.indexOf('\x00');
    while (end >= 0) {
      const raw = this.buffer.slice(0, end).replace(/\r\n/g, '\n');
      this.buffer = this.buffer.slice(end + 1);
      const split = raw.indexOf('\n\n');
      if (split < 0) throw new Error('Malformed STOMP frame');
      const lines = raw.slice(0, split).split('\n');
      const command = lines[0];
      if (command === 'CONNECTED') {
        if (this.connected) throw new Error('Duplicate CONNECTED');
        this.connected = true;
        connections.add(1);
        this.socket.send(frame('SUBSCRIBE', { id: 'updates', destination, ack: 'auto' }));
        this.heartbeat = setInterval(() => {
          if (this.healthy() && this.socket.readyState === 1) {
            this.socket.send('\n');
            heartbeatSent.add(1);
          }
        }, 10000);
        this.watchdog = setInterval(() => {
          // Traffic or heartbeat data must arrive within a generous 30-second window.
          if (this.healthy() && Date.now() - this.lastReceivedAt > 30000) {
            livenessTimeouts.add(1);
            this.breakConnection();
          }
        }, 5000);
        // The simple broker has no SUBSCRIBE receipt barrier. This is a disclosed
        // settling interval, not proof of subscription readiness or a retry.
        this.settleTimer = setTimeout(() => this.finishReady(this.healthy()), settleMs);
      } else if (command === 'MESSAGE') {
        if (!lines.includes('subscription:updates')) throw new Error('Wrong subscription');
        this.receive(JSON.parse(raw.slice(split + 2)) as Update);
      } else {
        // ERROR and any unsupported frame fail this socket; never reconnect silently.
        throw new Error('Unexpected STOMP command');
      }
      this.consumeHeartbeatLines();
      end = this.buffer.indexOf('\x00');
    }
  }

  private consumeHeartbeatLines(): void {
    const leading = this.buffer.match(/^(?:\r?\n)+/);
    if (leading === null) return;
    const count = leading[0].split('\n').length - 1;
    heartbeatReceived.add(count);
    if (this.observingIdle) this.idleHeartbeats += count;
    this.buffer = this.buffer.slice(leading[0].length);
  }

  private receive(update: Update): void {
    if (!update || update.gameId !== this.gameId || update.type !== 'GAME_UPDATED' ||
      !Number.isSafeInteger(update.version) || update.version < 0) {
      unexpected.add(1);
      this.breakConnection();
      return;
    }
    if (this.seen.has(update.version)) {
      duplicates.add(1);
      return;
    }
    this.seen.add(update.version);
    if (this.pending === undefined || this.pending.version !== update.version) {
      unexpected.add(1);
      this.breakConnection();
      return;
    }
    this.finishPending(true);
  }
}

async function request(client: Client, method: string, path: string, body: Record<string, unknown> | null, name: string) {
  return http.asyncRequest(method, `${baseUrl}/api/v1${path}`, body === null ? null : JSON.stringify(body), {
    jar: client.jar,
    redirects: 0,
    timeout: '10s',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': client.csrfToken },
    tags: { name, phase: 'websocket_stability' },
  });
}

export default async function (fixture: Fixture): Promise<void> {
  // Seed zero-valued counters so zero-error thresholds are present in summaries.
  for (const metric of [connections, notifications, duplicates, unexpected, socketErrors, completed,
    heartbeatSent, heartbeatReceived, idleHeartbeatConnections, livenessTimeouts]) metric.add(0);
  const assigned = fixture.games[execution.vu.idInTest - 1];
  requireThat(assigned !== undefined, 'WS VU has one assigned game');
  const white = restore(assigned.white);
  const black = restore(assigned.black);
  let game = assigned.game;
  let success = false;
  const sockets: PlayerSocket[] = [];
  let holdStartedAt: number | undefined;
  // console.log(`[stability phase] VU ${execution.vu.idInTest} connects at ${new Date().toISOString()}`);

  try {
    sockets.push(new PlayerSocket(assigned.white, game.id));
    sockets.push(new PlayerSocket(assigned.black, game.id));
    const ready = await Promise.all(sockets.map(socket => socket.ready));
    requireThat(ready.every(Boolean), 'Both Players establish STOMP and finish subscription settling');

    const holdStart = Date.now();
    holdStartedAt = holdStart;
    // console.log(`[stability phase] VU ${execution.vu.idInTest} hold begins at ${new Date().toISOString()}`);
    const waitUntil = async (targetOffset: number): Promise<void> => {
      // Async delays keep socket/heartbeat callbacks running. Fail promptly if a socket breaks.
      while (Date.now() - holdStart < targetOffset) {
        requireThat(sockets.every(socket => socket.healthy()), 'Sockets stay healthy during the stability hold');
        await delay(Math.min(1000, targetOffset - (Date.now() - holdStart)));
      }
      requireThat(sockets.every(socket => socket.healthy()), 'Sockets are healthy at scheduled activity');
    };

    for (let index = 0; index <= moves.length; index++) {
      const resigning = index === moves.length;
      // Opening moves at 0/1/2/3 seconds; then no commands until 45 seconds.
      // Sixteen cycle plies run at 45,53,...,165 seconds; resign no earlier than 180.
      const offset = resigning ? holdMs : index < 4 ? index * 1000 : 45000 + (index - 4) * 8000;
      await waitUntil(offset);
      if (index === 4) {
        for (const socket of sockets) {
          requireThat(socket.observedIdleHeartbeat(), 'Each socket receives a heartbeat during the idle window');
          idleHeartbeatConnections.add(1);
          socket.observeIdle(false);
        }
      }
      const actor = resigning || index % 2 === 0 ? white : black;
      const observer = actor === white ? black : white;
      const expectedVersion = game.version + 1;
      const startedAt = Date.now();
      const deliveries = sockets.map(socket => socket.expect(expectedVersion, startedAt));
      const path = resigning ? `/games/${game.id}/resignation` : `/games/${game.id}/moves`;
      const body = resigning ? { expectedVersion: game.version } : {
        expectedVersion: game.version, ...moves[index], promotion: null,
      };
      // Async HTTP keeps the VU event loop available for both sockets.
      const response = await request(actor, 'POST', path, body,
        resigning ? 'POST /games/:id/resignation' : 'POST /games/:id/moves');
      expectStatus(response, 200, resigning ? 'WS game resignation' : 'WS game move');
      if (!resigning) moveDuration.add(response.timings.duration);
      const accepted = readJson<{ gameId?: number; id?: number; version: number; currentFen: string; status: string }>(response, 'WS command');
      requireThat((accepted.gameId ?? accepted.id) === game.id && accepted.version === expectedVersion &&
        accepted.status === (resigning ? 'FINISHED' : 'IN_PROGRESS'), 'Command advances the expected game version');
      requireThat((await Promise.all(deliveries)).every(Boolean), 'Both Players receive this game/version notification');

      const read = await request(observer, 'GET', `/games/${game.id}`, null, 'GET /games/:id after notification');
      expectStatus(read, 200, 'Notification-triggered game read');
      const view = readJson<GameState>(read, 'WS reconciled game');
      requireThat(view.id === game.id && view.version === expectedVersion &&
        view.currentFen === accepted.currentFen && view.status === accepted.status,
        'Opponent reconciles authoritative state after notification');
      if (resigning) {
        requireThat(view.result === 'BLACK_WIN' && view.terminationReason === 'RESIGNATION', 'WS game has expected final result');
      }
      reconciledDuration.add(Date.now() - startedAt);
      game = view;
      if (index === 3) {
        for (const socket of sockets) socket.observeIdle(true);
      }
    }
    requireThat(sockets.every(socket => socket.healthy()), 'Both sockets remain healthy through completion');
    requireThat(Date.now() - holdStart >= holdMs, 'Connections remain open for at least three minutes');
    // console.log(`[stability phase] VU ${execution.vu.idInTest} completes at ${new Date().toISOString()}`);
    completed.add(1);
    success = true;
  } finally {
    if (holdStartedAt !== undefined) heldDuration.add(Date.now() - holdStartedAt);
    for (const socket of sockets) socket.stop();
    gameFailures.add(!success);
  }
}
