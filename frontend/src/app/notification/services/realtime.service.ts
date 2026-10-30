import {
  effect,
  inject,
  Injectable,
  signal,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { Client } from '@stomp/stompjs';

import { AuthSessionStore } from '../../auth/services/auth-session.store';
import { REALTIME_CONFIG } from '../config/realtime.config';
import {
  parsePlayerUpdate,
  PlayerUpdate,
} from '../models/player-update';

export type RealtimeConnectionState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'RECONNECTING'
  | 'UNAVAILABLE';

@Injectable({
  providedIn: 'root',
})
export class RealtimeService {
  private readonly session = inject(AuthSessionStore);
  private readonly router = inject(Router);
  private readonly config = inject(REALTIME_CONFIG);

  private readonly connectionState =
    signal<RealtimeConnectionState>('DISCONNECTED');

  private readonly updatesSubject = new Subject<PlayerUpdate>();
  private readonly connectionsSubject = new Subject<void>();

  readonly state = this.connectionState.asReadonly();
  readonly updates$ = this.updatesSubject.asObservable();

  // Emits on initial connection and every successful reconnection.
  // It does not acknowledge server-side subscription registration.
  readonly connections$ = this.connectionsSubject.asObservable();

  constructor() {
    effect(onCleanup => {
      const token = this.session.token();
      const authenticated = this.session.isAuthenticated();

      if (!token || !authenticated) {
        this.connectionState.set('DISCONNECTED');
        return;
      }

      // Only token/authentication changes should restart this effect.
      // Reads made while setting up the transport are not dependencies.
      const cleanup = untracked(() => this.connect(token));

      onCleanup(cleanup);
    });
  }

  private connect(token: string): () => void {
    const expiresAt = this.readExpiry(token);

    if (expiresAt === null) {
      // HTTP remains available to establish whether the session is valid.
      this.connectionState.set('UNAVAILABLE');
      return () => { };
    }

    let stopped = false;
    let failed = false;
    let hasConnected = false;
    let expiryTimer: ReturnType<typeof setTimeout> | undefined;

    const isCurrentSession = () =>
      !stopped &&
      this.session.token() === token &&
      this.session.isAuthenticated();

    const url = new URL(
      this.config.endpoint,
      window.location.href,
    );

    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';

    const client = new Client({
      brokerURL: url.toString(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: this.config.reconnectDelayMs,
      connectionTimeout: this.config.connectionTimeoutMs,

      heartbeatIncoming: this.config.heartbeatIntervalMs,
      heartbeatOutgoing: this.config.heartbeatIntervalMs,

      // Close an unresponsive transport so automatic reconnect can proceed.
      discardWebsocketOnCommFailure: true,

      // Never log CONNECT frames containing the bearer token.
      debug: () => { },
    });

    const deactivate = () => {
      void client.deactivate({ force: true }).catch(() => {
        // Local callbacks are already guarded against stale sessions.
      });
    };

    const expireSession = () => {
      if (!isCurrentSession()) {
        return;
      }

      this.connectionState.set('DISCONNECTED');
      this.session.clear();
      deactivate();

      void this.router.navigate(['/login'], {
        queryParams: { sessionExpired: 'true' },
        replaceUrl: true,
      });
    };

    const armExpiryTimer = () => {
      if (!isCurrentSession()) {
        return;
      }

      const remaining = expiresAt - Date.now();

      if (remaining <= 0) {
        expireSession();
        return;
      }

      // Browser timers have a maximum signed 32-bit delay.
      expiryTimer = setTimeout(
        armExpiryTimer,
        Math.min(remaining, 2_147_483_647),
      );
    };

    client.beforeConnect = () => {
      if (!isCurrentSession() || failed) {
        deactivate();
        return;
      }

      if (Date.now() >= expiresAt) {
        expireSession();
        return;
      }

      this.connectionState.set(
        hasConnected ? 'RECONNECTING' : 'CONNECTING',
      );
    };

    client.onConnect = () => {
      if (!isCurrentSession() || failed) {
        deactivate();
        return;
      }

      if (Date.now() >= expiresAt) {
        expireSession();
        return;
      }

      // Subscriptions belong to a connection and must be recreated
      // after reconnecting.
      client.subscribe(
        this.config.subscriptionDestination,
        message => {
          if (!isCurrentSession() || failed) {
            return;
          }

          if (Date.now() >= expiresAt) {
            expireSession();
            return;
          }

          const update = parsePlayerUpdate(message.body);

          if (update !== null) {
            this.updatesSubject.next(update);
          }
        },
      );

      hasConnected = true;
      this.connectionState.set('CONNECTED');
      this.connectionsSubject.next();
    };

    client.onWebSocketClose = () => {
      if (isCurrentSession() && !failed) {
        this.connectionState.set('RECONNECTING');
      }
    };

    client.onWebSocketError = () => {
      if (isCurrentSession() && !failed) {
        this.connectionState.set('RECONNECTING');
      }
    };

    client.onStompError = () => {
      if (!isCurrentSession()) {
        return;
      }

      if (Date.now() >= expiresAt) {
        expireSession();
        return;
      }

      // A STOMP error may indicate authentication or configuration
      // rejection. Avoid retrying that rejection indefinitely.
      // HTTP still determines whether the session is valid.
      failed = true;
      this.connectionState.set('UNAVAILABLE');
      deactivate();
    };

    armExpiryTimer();

    if (isCurrentSession()) {
      this.connectionState.set('CONNECTING');
      client.activate();
    }

    return () => {
      // Ignore callbacks from this client before starting a replacement.
      stopped = true;

      if (expiryTimer !== undefined) {
        clearTimeout(expiryTimer);
      }

      this.connectionState.set('DISCONNECTED');
      deactivate();
    };
  }

  private readExpiry(token: string): number | null {
    try {
      const parts = token.split('.');

      if (parts.length !== 3) {
        return null;
      }

      const base64 = parts[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/');

      const padded = base64.padEnd(
        Math.ceil(base64.length / 4) * 4,
        '=',
      );

      const value: unknown = JSON.parse(atob(padded));

      if (typeof value !== 'object' || value === null) {
        return null;
      }

      const exp = (value as Record<string, unknown>)['exp'];

      if (
        typeof exp !== 'number' ||
        !Number.isSafeInteger(exp) ||
        !Number.isSafeInteger(exp * 1_000)
      ) {
        return null;
      }

      // Reading expiry schedules local cleanup; it does not verify JWTs.
      // Signature and authorization checks remain on the backend.
      return exp * 1_000;
    } catch {
      return null;
    }
  }
}