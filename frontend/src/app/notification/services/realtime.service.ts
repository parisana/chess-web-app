import {
  effect,
  inject,
  Injectable,
  signal,
  untracked,
} from '@angular/core';
import { HttpXsrfTokenExtractor } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, Subject, switchMap, timeout } from 'rxjs';
import { Client } from '@stomp/stompjs';

import { AuthApiService } from '../../auth/services/auth-api.service';
import { AuthSessionStore } from '../../auth/services/auth-session.store';
import { PlayerApiService } from '../../player/services/player-api.service';
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
  private readonly authApi = inject(AuthApiService);
  private readonly playerApi = inject(PlayerApiService);
  private readonly xsrf = inject(HttpXsrfTokenExtractor);
  private readonly router = inject(Router);
  private readonly config = inject(REALTIME_CONFIG);

  private readonly connectionState =
    signal<RealtimeConnectionState>('DISCONNECTED');

  private readonly updatesSubject = new Subject<PlayerUpdate>();
  private readonly connectionsSubject = new Subject<void>();

  readonly state = this.connectionState.asReadonly();
  readonly updates$ = this.updatesSubject.asObservable();
  readonly connections$ = this.connectionsSubject.asObservable();

  constructor() {
    effect(onCleanup => {
      const revision = this.session.revision();
      const authenticated = this.session.isAuthenticated();

      if (!authenticated) {
        this.connectionState.set('DISCONNECTED');
        return;
      }

      const cleanup = untracked(() => this.connect(revision));

      onCleanup(cleanup);
    });
  }

  private connect(revision: number): () => void {
    const playerId = this.session.currentPlayer()?.id;

    let stopped = false;
    let failed = false;
    let hasConnected = false;

    const isCurrentSession = () =>
      !stopped &&
      this.session.revision() === revision &&
      this.session.isAuthenticated();

    const url = new URL(
      this.config.endpoint,
      window.location.href,
    );

    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';

    const client = new Client({
      brokerURL: url.toString(),
      reconnectDelay: this.config.reconnectDelayMs,
      connectionTimeout: this.config.connectionTimeoutMs,
      heartbeatIncoming: this.config.heartbeatIntervalMs,
      heartbeatOutgoing: this.config.heartbeatIntervalMs,
      discardWebsocketOnCommFailure: true,

      // CONNECT now contains a CSRF token; don't log its frames.
      debug: () => { },
    });

    const deactivate = () => {
      void client.deactivate({ force: true }).catch(() => {
        // Callbacks remain guarded against stale local sessions.
      });
    };

    client.beforeConnect = async () => {
      if (!isCurrentSession() || failed) {
        deactivate();
        return;
      }

      this.connectionState.set(
        hasConnected ? 'RECONNECTING' : 'CONNECTING',
      );

      try {
        // Recheck through HTTP before initial connection/reconnection.
        // A 401 is handled by the authentication interceptor.
        const player = await firstValueFrom(
          this.authApi.csrf().pipe(
            switchMap(() => this.playerApi.currentPlayer()),
            timeout(10_000),
          ),
        );

        if (!isCurrentSession()) {
          deactivate();
          return;
        }

        // The shared cookie may have changed in another browser tab.
        if (player.id !== playerId) {
          this.session.clear();
          deactivate();

          void this.router.navigate(['/login'], {
            queryParams: { sessionChanged: 'true' },
            replaceUrl: true,
          });

          return;
        }

        const csrfToken = this.xsrf.getToken();

        if (!csrfToken) {
          throw new Error('WebSocket CSRF token is unavailable');
        }

        client.connectHeaders = {
          'X-XSRF-TOKEN': csrfToken,
        };
      } catch {
        if (isCurrentSession()) {
          failed = true;
          this.connectionState.set('UNAVAILABLE');
        }

        deactivate();
      }
    };

    client.onConnect = () => {
      if (!isCurrentSession() || failed) {
        deactivate();
        return;
      }

      client.subscribe(
        this.config.subscriptionDestination,
        message => {
          if (!isCurrentSession() || failed) {
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

      // Avoid retrying a protocol/security rejection indefinitely.
      failed = true;
      this.connectionState.set('UNAVAILABLE');
      deactivate();

      // HTTP determines whether authentication itself has expired.
      // Failure here is already handled by the interceptor when it is 401.
      void firstValueFrom(
        this.playerApi.currentPlayer().pipe(timeout(10_000)),
      ).catch(() => { });
    };

    this.connectionState.set('CONNECTING');
    client.activate();

    return () => {
      stopped = true;
      this.connectionState.set('DISCONNECTED');
      deactivate();
    };
  }
}