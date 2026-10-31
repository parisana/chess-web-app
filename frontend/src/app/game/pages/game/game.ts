import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
  defer,
  EMPTY,
  exhaustMap,
  filter,
  finalize,
  fromEvent,
  interval,
  map,
  merge,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  takeUntil,
  tap,
} from 'rxjs';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthService } from '../../../auth/services/auth.service';
import { GameResponse } from '../../models/game-response';
import { GameApiService } from '../../services/game-api.service';
import { ChessBoard } from '../../components/chess-board/chess-board';
import { parseFenForDisplay } from '../../models/fen-display';
import { ProposedMove, PromotionPiece } from '../../models/game-move';
import { REALTIME_CONFIG } from '../../../notification/config/realtime.config';
import { RealtimeService } from '../../../notification/services/realtime.service';
import { GameHistoryResponse } from '../../models/game-history';
import { GameLifecycleAction } from '../../models/game-lifecycle';

@Component({
  selector: 'app-game',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    ChessBoard
  ],
  templateUrl: './game.html',
  styleUrl: './game.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Game implements OnInit {
  readonly auth = inject(AuthService);

  private readonly route = inject(ActivatedRoute);
  private readonly gameApi = inject(GameApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshRequests = new Subject<void>();

  readonly game = signal<GameResponse | null>(null);
  readonly loading = signal(false);
  readonly validGameId = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly needsRefresh = signal(false);
  readonly pendingPromotion = signal<ProposedMove | null>(null);
  readonly moveMessage = signal<string | null>(null);
  readonly history = signal<GameHistoryResponse | null>(null);
  readonly historyLoading = signal(false);
  readonly historyError = signal<string | null>(null);
  readonly pendingLifecycleConfirmation = signal<GameLifecycleAction | null>(null);

  readonly historyNeedsRefresh = computed(() => {
    const current = this.game();
    const history = this.history();

    // identifies missing, outdated or failed history reads
    return current !== null && (
      history === null ||
      history.gameId !== current.id ||
      history.version !== current.version ||
      this.historyError() !== null
    );
  });

  readonly realtimeService = inject(RealtimeService);

  private readonly realtimeConfig = inject(REALTIME_CONFIG);
  private readonly cancelReads = new Subject<void>();

  private activeGameId: number | null = null;
  private latestNotifiedVersion = -1;

  private reading = false;
  private readPending = false;
  private readScheduled = false;

  readonly promotionChoices: readonly PromotionPiece[] = [
    'QUEEN',
    'ROOK',
    'BISHOP',
    'KNIGHT',
  ];

  /**
 * Shared permission for commands that mutate an active game.
 * Turn-specific permission belongs only to move submission.
 */
  readonly canSubmitGameCommand = computed(() => {
    const game = this.game();
    const player = this.auth.currentPlayer();

    return game !== null &&
      player !== null &&
      this.auth.isAuthenticated() &&
      game.status === 'IN_PROGRESS' &&
      (
        game.whitePlayerId === player.id ||
        game.blackPlayerId === player.id
      ) &&
      !this.loading() &&
      !this.submitting() &&
      !this.needsRefresh();
  });

  readonly canSubmitLifecycleAction = computed(() =>
    this.canSubmitGameCommand() &&
    this.pendingPromotion() === null
  );

  readonly canSubmitMove = computed(() => {
    if (
      !this.canSubmitGameCommand() ||
      this.pendingLifecycleConfirmation() !== null
    ) {
      return false;
    }

    const game = this.game();
    const player = this.auth.currentPlayer();

    if (!game || !player) {
      return false;
    }

    const color = game.whitePlayerId === player.id
      ? 'WHITE'
      : 'BLACK';

    try {
      return parseFenForDisplay(game.currentFen).sideToMove === color;
    } catch {
      return false;
    }
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(
      switchMap(params => {
        // Navigation establishes a new synchronization scope.
        // switchMap cancels the previous route's read stream first.
        this.activeGameId = null;
        this.latestNotifiedVersion = -1;
        this.readPending = false;

        this.game.set(null);
        this.history.set(null);
        this.historyError.set(null);
        this.pendingPromotion.set(null);
        this.pendingLifecycleConfirmation.set(null);
        this.errorMessage.set(null);
        this.moveMessage.set(null);
        this.needsRefresh.set(false);
        this.validGameId.set(false);

        const rawId = params.get('gameId');
        const gameId = rawId === null ? NaN : Number(rawId);

        if (
          rawId === null ||
          !/^[1-9][0-9]*$/.test(rawId) ||
          !Number.isSafeInteger(gameId)
        ) {
          this.errorMessage.set('The game URL is invalid.');
          return EMPTY;
        }

        this.activeGameId = gameId;
        this.validGameId.set(true);

        return this.refreshRequests.pipe(
          startWith(undefined),

          // Only one GET runs for this route at a time.
          // Additional work is remembered by readPending.
          exhaustMap(() => this.readSnapshot(gameId)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();

    this.realtimeService.updates$.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(update => {
      if (update.gameId !== this.activeGameId) {
        return;
      }

      const current = this.game();

      // Duplicate or delayed notifications need no additional read.
      if (current && update.version <= current.version) {
        return;
      }

      this.latestNotifiedVersion = Math.max(
        this.latestNotifiedVersion,
        update.version,
      );

      this.pendingPromotion.set(null);
      this.requestSynchronization(true);
    });

    this.realtimeService.connections$.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      // Initial connection and reconnect may follow missed notifications.
      this.requestSynchronization(true);
    });

    merge(
      fromEvent(window, 'focus'),
      fromEvent(document, 'visibilitychange').pipe(
        filter(() => document.visibilityState === 'visible'),
      ),
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.requestSynchronization(true);
    });

    interval(this.realtimeConfig.reconciliationIntervalMs).pipe(
      filter(() => {
        const current = this.game();

        return document.visibilityState === 'visible' &&
          current !== null &&
          (
            current.status === 'IN_PROGRESS' ||
            this.needsRefresh() ||
            this.historyNeedsRefresh()
          );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      // A safety read does not disable the board or cancel promotion.
      // An actual newer snapshot will update them if necessary.
      this.requestSynchronization(false);
    });
  }

  refresh(): void {
    if (
      this.loading() ||
      this.submitting() ||
      !this.validGameId()
    ) {
      return;
    }

    this.requestSynchronization(true);
  }

  resultText(game: GameResponse): string {
    switch (game.result) {
      case 'WHITE_WIN':
        return 'White wins';
      case 'BLACK_WIN':
        return 'Black wins';
      case 'DRAW':
        return 'Draw';
      default:
        return 'Game finished';
    }
  }

  proposeMove(move: ProposedMove): void {
    if (!this.canSubmitMove() || this.pendingPromotion() !== null) {
      return;
    }

    const game = this.game();

    if (!game) {
      return;
    }

    this.moveMessage.set(null);

    try {
      const position = parseFenForDisplay(game.currentFen);
      const piece = position.pieces.get(move.from);

      // Defensive interaction check, not chess legality validation.
      if (!piece || piece.color !== position.sideToMove) {
        return;
      }

      const reachesPromotionRank =
        piece.name === 'pawn' &&
        (
          (piece.color === 'WHITE' && move.to.endsWith('8')) ||
          (piece.color === 'BLACK' && move.to.endsWith('1'))
        );

      if (reachesPromotionRank) {
        this.pendingPromotion.set(move);
        return;
      }
    } catch {
      this.needsRefresh.set(true);
      this.moveMessage.set(
        'The position could not be read. Refresh before moving.',
      );
      return;
    }

    this.sendMove(move, null);
  }

  choosePromotion(promotion: PromotionPiece): void {
    const move = this.pendingPromotion();

    if (!move || !this.canSubmitMove()) {
      return;
    }

    this.pendingPromotion.set(null);
    this.sendMove(move, promotion);
  }

  cancelPromotion(): void {
    this.pendingPromotion.set(null);
  }

  requestLifecycleAction(action: GameLifecycleAction): void {
    if (
      this.pendingLifecycleConfirmation() !== null ||
      !this.canPerformLifecycleAction(action)
    ) {
      return;
    }

    this.moveMessage.set(null);

    // These actions finish the game, so ask for an explicit second click.
    if (action === 'resign' || action === 'accept-draw') {
      this.pendingLifecycleConfirmation.set(action);
      return;
    }

    this.sendLifecycleAction(action);
  }

  confirmLifecycleAction(): void {
    const action = this.pendingLifecycleConfirmation();

    if (action === null) {
      return;
    }

    this.pendingLifecycleConfirmation.set(null);

    // Recheck current state rather than trusting the earlier button click.
    this.sendLifecycleAction(action);
  }

  cancelLifecycleConfirmation(): void {
    this.pendingLifecycleConfirmation.set(null);
  }

  private canPerformLifecycleAction(
    action: GameLifecycleAction,
  ): boolean {
    if (!this.canSubmitLifecycleAction()) {
      return false;
    }

    const current = this.game();
    const player = this.auth.currentPlayer();

    if (!current || !player) {
      return false;
    }

    const sender = current.drawOfferedByPlayerId;

    switch (action) {
      case 'resign':
        return true;

      case 'offer-draw':
        return sender === null;

      case 'accept-draw':
      case 'decline-draw':
        return sender !== null && sender !== player.id;

      case 'withdraw-draw':
        return sender === player.id;
    }
  }

  private sendLifecycleAction(action: GameLifecycleAction): void {
    if (!this.canPerformLifecycleAction(action)) {
      return;
    }

    const current = this.game();

    if (!current) {
      return;
    }

    this.executeGameCommand(
      current,
      () => this.gameApi.submitLifecycleAction(current.id, action, {
        expectedVersion: current.version,
      }),
      result => result,
    );
  }

  private sendMove(
    move: ProposedMove,
    promotion: PromotionPiece | null,
  ): void {
    if (!this.canSubmitMove()) {
      return;
    }

    const current = this.game();

    if (!current) {
      return;
    }

    this.executeGameCommand(
      current,
      () => this.gameApi.submitMove(current.id, {
        ...move,
        promotion,
        expectedVersion: current.version,
      }),
      result => ({
        ...current,
        currentFen: result.currentFen,
        version: result.version,
        status: result.status,
        result: result.result,
        terminationReason: result.terminationReason,
        finishedAt: result.finishedAt,

        // Every accepted move clears any pending offer.
        drawOfferedByPlayerId: null,
      }),
    );
  }

  /**
 * Serializes game commands with the existing submission lock.
 * Reads are cancelled before POST and reconciliation runs afterward.
 * An uncertain command is never automatically replayed.
 */
  private executeGameCommand<T>(
    current: GameResponse,
    command: () => Observable<T>,
    toSnapshot: (result: T) => GameResponse,
  ): void {
    if (
      !this.canSubmitGameCommand() ||
      this.game() !== current
    ) {
      return;
    }

    // Only a nonblocking safety read can coexist with command permission.
    if (this.reading) {
      this.readPending = true;
      this.cancelReads.next();
    }

    this.pendingPromotion.set(null);
    this.pendingLifecycleConfirmation.set(null);
    this.moveMessage.set(null);
    this.submitting.set(true);

    defer(command).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.submitting.set(false);
        this.schedulePendingRead();
      }),
    ).subscribe({
      next: result => {
        // Ignore callbacks belonging to a replaced route snapshot.
        if (this.game() !== current) {
          return;
        }

        this.game.set(toSnapshot(result));

        // Lifecycle changes also advance history's captured game version,
        // even when the recorded move list itself has not changed.
        this.requestSynchronization(false);
      },
      error: (error: unknown) => {
        if (this.game() !== current) {
          return;
        }

        this.handleCommandError(error);
      },
    });
  }

  private requestSynchronization(blockMoves: boolean): void {
    if (
      this.destroyRef.destroyed ||
      this.activeGameId === null ||
      !this.auth.isAuthenticated()
    ) {
      return;
    }

    if (blockMoves) {
      this.needsRefresh.set(true);
      this.pendingPromotion.set(null);
      this.pendingLifecycleConfirmation.set(null);
    }

    this.readPending = true;
    this.schedulePendingRead();
  }

  private schedulePendingRead(): void {
    if (this.readScheduled || this.destroyRef.destroyed) {
      return;
    }

    this.readScheduled = true;

    queueMicrotask(() => {
      this.readScheduled = false;

      if (
        !this.readPending ||
        this.reading ||
        this.submitting() ||
        this.destroyRef.destroyed ||
        this.activeGameId === null ||
        !this.auth.isAuthenticated()
      ) {
        return;
      }

      this.readPending = false;
      this.refreshRequests.next();
    });
  }

  private readSnapshot(gameId: number): Observable<GameResponse> {
    return defer(() => {
      this.reading = true;

      // Keep an existing board visible during synchronization.
      this.loading.set(this.game() === null);
      this.errorMessage.set(null);

      return this.gameApi.getGame(gameId).pipe(
        tap(snapshot => {
          const current = this.game();

          // Never replace a newer accepted snapshot with an older read.
          // Preserve object identity for an unchanged version.
          if (
            current === null ||
            current.id !== snapshot.id ||
            snapshot.version > current.version
          ) {
            this.pendingPromotion.set(null);
            this.pendingLifecycleConfirmation.set(null);
            this.game.set(snapshot);
          }

          const observed = this.game();

          const stillBehind =
            observed === null ||
            observed.version < this.latestNotifiedVersion;

          this.needsRefresh.set(stillBehind);

          if (stillBehind) {
            this.errorMessage.set(
              'A newer game update is known. Refresh again if synchronization does not complete.',
            );
          } else {
            this.moveMessage.set(null);
          }
        }),
        // The board is accepted first. History failure is handled separately
        // and therefore does not turn a successful board read into a failure.
        switchMap(snapshot =>
          this.readHistorySnapshot(gameId).pipe(
            map(() => snapshot),
          ),
        ),
        // Cancel either stage before a move POST begins.
        takeUntil(this.cancelReads),
        catchError((error: unknown) => {
          this.needsRefresh.set(true);
          this.errorMessage.set(this.describeError(error));

          if (
            error instanceof HttpErrorResponse &&
            (error.status === 403 || error.status === 404)
          ) {
            this.game.set(null);
            this.history.set(null);
            this.pendingPromotion.set(null);
          }

          return EMPTY;
        }),
        finalize(() => {
          this.reading = false;
          this.loading.set(false);
          this.schedulePendingRead();
        }),
      );
    });
  }

  private readHistorySnapshot(
    gameId: number,
  ): Observable<GameHistoryResponse | null> {
    return defer(() => {
      this.historyLoading.set(true);
      this.historyError.set(null);

      return this.gameApi.getHistory(gameId).pipe(
        tap(history => {
          const current = this.game();

          if (
            !current ||
            current.id !== gameId ||
            history.gameId !== gameId
          ) {
            return;
          }

          if (history.version > current.version) {
            // A move committed between the board and history reads.
            // Synchronize the board before displaying the newer move list.
            this.latestNotifiedVersion = Math.max(
              this.latestNotifiedVersion,
              history.version,
            );

            this.requestSynchronization(true);
            return;
          }

          if (history.version < current.version) {
            // Preserve the previous list rather than accepting an older read.
            // Manual/focus/periodic recovery can retry.
            return;
          }

          const previous = this.history();

          if (
            previous === null ||
            previous.gameId !== history.gameId ||
            previous.version !== history.version
          ) {
            this.history.set(history);
          }
        }),

        catchError((error: unknown) => {
          // History failure does not invalidate an accepted board snapshot.
          this.historyError.set(
            'Unable to load the move list. Refresh the game to try again.',
          );

          if (
            error instanceof HttpErrorResponse &&
            (error.status === 403 || error.status === 404)
          ) {
            this.history.set(null);
          }

          return of(null);
        }),

        finalize(() => {
          this.historyLoading.set(false);
        }),
      );
    });
  }

  private handleCommandError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      const code: unknown = error.error?.code;

      if (
        code === 'STALE_GAME_VERSION' ||
        code === 'NOT_PLAYERS_TURN' ||
        code === 'GAME_ALREADY_FINISHED' ||
        code === 'DRAW_OFFER_ALREADY_PENDING' ||
        code === 'DRAW_OFFER_NOT_FOUND' ||
        code === 'DRAW_OFFER_ACTION_NOT_ALLOWED'
      ) {
        this.moveMessage.set(
          'The action could not be applied to the displayed state. ' +
          'Checking the latest game; the action will not be retried.',
        );

        this.requestSynchronization(true);
        return;
      }

      if (error.status === 422 && code === 'ILLEGAL_GAME_MOVE') {
        this.moveMessage.set(
          'That move is not legal. Choose another move.',
        );
        return;
      }

      if (error.status === 400) {
        this.moveMessage.set(
          'The request was invalid. Refresh the game before trying again.',
        );
        this.requestSynchronization(true);
        return;
      }

      if (error.status === 401) {
        // The session-aware authentication interceptor handles cleanup.
        return;
      }

      if (error.status === 403 || error.status === 404) {
        this.needsRefresh.set(true);
        this.moveMessage.set(this.describeError(error));
        return;
      }
    }

    // A transport/server error can arrive after a successful commit.
    this.moveMessage.set(
      'The action could not be confirmed. Checking the latest game; ' +
      'the action will not be retried.',
    );
    this.requestSynchronization(true);
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 0:
          return 'Unable to reach the server. Please try again.';
        case 401:
          return 'Your session is no longer valid. Please sign in again.';
        case 403:
          return 'You are not a participant in this game.';
        case 404:
          return 'This game could not be found.';
      }
    }

    return 'Unable to load the game. Please try again.';
  }
}