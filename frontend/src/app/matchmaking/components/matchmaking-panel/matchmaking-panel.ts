import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  filter,
  finalize,
  interval,
  merge,
  Observable,
  switchMap,
} from 'rxjs';

import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthSessionStore } from '../../../auth/services/auth-session.store';
import { REALTIME_CONFIG } from '../../../notification/config/realtime.config';
import { RealtimeService } from '../../../notification/services/realtime.service';
import { QueueStatusResponse } from '../../models/queue-status-response';
import { MatchmakingApiService } from '../../services/matchmaking-api.service';

type RequestKind = 'read' | 'join' | 'leave';

@Component({
  selector: 'app-matchmaking-panel',
  imports: [DatePipe, MatProgressBarModule],
  templateUrl: './matchmaking-panel.html',
  styleUrl: './matchmaking-panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchmakingPanel implements OnInit {
  readonly autoOpenExistingGame = input(false);
  readonly embedded = input(false);
  readonly searchLabel = input('Find match');

  private readonly api = inject(MatchmakingApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly config = inject(REALTIME_CONFIG);

  readonly realtime = inject(RealtimeService);
  readonly queueStatusResponse = signal<QueueStatusResponse | null>(null);
  readonly busy = signal(false);
  readonly openingGame = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private statusReadPending = false;

  // Follow a search started here or observed as WAITING.
  // Merely discovering an existing game while reviewing must not redirect.
  private followingSearch = false;
  private attemptedNavigationGameId: number | null = null;

  ngOnInit(): void {
    merge(
      this.realtime.connections$,
      this.realtime.updates$,
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.requestStatusRead());

    interval(this.config.reconciliationIntervalMs).pipe(
      filter(() => {
        const status = this.queueStatusResponse()?.status;

        return status === undefined ||
          status === 'WAITING' ||
          status === 'MATCHED';
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.requestStatusRead());

    this.refresh();
  }

  refresh(): void {
    this.errorMessage.set(null);
    this.requestStatusRead();
  }

  join(): void {
    if (!this.canStartRequest()) {
      return;
    }

    this.followingSearch = true;
    this.runRequest(this.api.join(), 'join');
  }

  leave(): void {
    if (!this.canStartRequest()) {
      return;
    }

    // Read the actual state afterward: pairing may have won the race.
    this.runRequest(
      this.api.leave().pipe(
        switchMap(() => this.api.status()),
      ),
      'leave',
    );
  }

  openCurrentGame(): void {
    const current = this.queueStatusResponse();

    if (!this.canStartRequest() || current?.status !== 'MATCHED') {
      return;
    }

    this.errorMessage.set(null);
    this.navigateToGame(current.gameId, true);
  }

  private canStartRequest(): boolean {
    return !this.busy() &&
      !this.openingGame() &&
      !this.destroyRef.destroyed &&
      this.session.isAuthenticated();
  }

  private requestStatusRead(): void {
    if (this.destroyRef.destroyed || !this.session.isAuthenticated()) {
      return;
    }

    this.statusReadPending = true;
    this.flushPendingStatusRead();
  }

  private flushPendingStatusRead(): void {
    if (!this.statusReadPending || !this.canStartRequest()) {
      return;
    }

    this.statusReadPending = false;
    this.runRequest(this.api.status(), 'read');
  }

  private runRequest(
    request: Observable<QueueStatusResponse>,
    kind: RequestKind,
  ): void {
    if (!this.canStartRequest()) {
      return;
    }

    const revision = this.session.revision();
    const playerId = this.session.currentPlayer()?.id;

    if (playerId === undefined) {
      return;
    }

    const isCurrent = () =>
      !this.destroyRef.destroyed &&
      this.session.revision() === revision &&
      this.session.currentPlayer()?.id === playerId;

    if (kind !== 'read') {
      this.errorMessage.set(null);
    }

    this.busy.set(true);

    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.busy.set(false);

        if (this.statusReadPending) {
          queueMicrotask(() => this.flushPendingStatusRead());
        }
      }),
    ).subscribe({
      next: response => {
        if (!isCurrent()) {
          return;
        }

        this.queueStatusResponse.set(response);

        if (response.status === 'WAITING') {
          this.followingSearch = true;
          this.attemptedNavigationGameId = null;
          return;
        }

        if (response.status === 'NOT_QUEUED') {
          this.followingSearch = false;
          this.attemptedNavigationGameId = null;
          return;
        }

        if (this.autoOpenExistingGame() || this.followingSearch) {
          this.navigateToGame(response.gameId);
        }
      },

      error: (error: unknown) => {
        if (!isCurrent()) {
          return;
        }

        this.errorMessage.set(this.describeError(error));

        if (kind !== 'read') {
          // The command might have committed before its response failed.
          // Reconcile with a GET; never automatically repeat the command.
          this.queueStatusResponse.set(null);
          this.statusReadPending = true;
        }
      },
    });
  }

  private navigateToGame(gameId: number, explicit = false): void {
    if (
      this.openingGame() ||
      this.destroyRef.destroyed ||
      !this.session.isAuthenticated() ||
      (!explicit && this.attemptedNavigationGameId === gameId)
    ) {
      return;
    }

    const revision = this.session.revision();
    const playerId = this.session.currentPlayer()?.id;

    const isCurrent = () =>
      !this.destroyRef.destroyed &&
      this.session.revision() === revision &&
      this.session.currentPlayer()?.id === playerId;

    this.attemptedNavigationGameId = gameId;
    this.openingGame.set(true);

    void this.router.navigate(['/games', gameId], {
      // Home should not become a Back-button redirect loop.
      // When reviewing, preserve the old game in browser history.
      replaceUrl: this.autoOpenExistingGame(),
    }).then(opened => {
      if (isCurrent() && !opened) {
        this.errorMessage.set(
          'The game could not be opened. Select Return to current game.',
        );
      }
    }).catch(() => {
      if (isCurrent()) {
        this.errorMessage.set(
          'The game could not be opened. Select Return to current game.',
        );
      }
    }).finally(() => {
      if (isCurrent()) {
        this.openingGame.set(false);

        if (this.statusReadPending) {
          queueMicrotask(() => this.flushPendingStatusRead());
        }
      }
    });
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'Unable to reach the server. Checking your match status.';
      }

      if (error.status === 401) {
        return 'Your session is no longer valid. Please sign in again.';
      }

      if (error.status === 403) {
        return 'The request was rejected. Refresh the page and try again.';
      }
    }

    return 'Unable to update matchmaking. Refresh its status to continue.';
  }
}