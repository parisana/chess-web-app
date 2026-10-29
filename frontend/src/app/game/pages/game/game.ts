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
  finalize,
  startWith,
  Subject,
  switchMap,
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

  readonly promotionChoices: readonly PromotionPiece[] = [
    'QUEEN',
    'ROOK',
    'BISHOP',
    'KNIGHT',
  ];

  readonly canSubmitMove = computed(() => {
    const game = this.game();
    const player = this.auth.currentPlayer();

    if (
      !game ||
      !player ||
      game.status !== 'IN_PROGRESS' ||
      this.loading() ||
      this.submitting() ||
      this.needsRefresh()
    ) {
      return false;
    }

    const color = game.whitePlayerId === player.id
      ? 'WHITE'
      : game.blackPlayerId === player.id
        ? 'BLACK'
        : null;

    if (color === null) {
      return false;
    }

    try {
      return parseFenForDisplay(game.currentFen).sideToMove === color;
    } catch {
      return false;
    }
  });

  ngOnInit(): void {
    combineLatest([
      this.route.paramMap,
      this.refreshRequests.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([params]) => defer(() => {
        // Clear the previous game's data before loading another snapshot.
        this.game.set(null);
        this.pendingPromotion.set(null);
        this.errorMessage.set(null);
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

        this.validGameId.set(true);
        this.loading.set(true);

        return this.gameApi.getGame(gameId).pipe(
          catchError((error: unknown) => {
            this.errorMessage.set(this.describeError(error));
            return EMPTY;
          }),
          finalize(() => this.loading.set(false)),
        );
      })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(game => {
      this.game.set(game);
      this.needsRefresh.set(false);
      this.moveMessage.set(null);
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

    this.refreshRequests.next();
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

    this.moveMessage.set(null);
    this.submitting.set(true);

    this.gameApi.submitMove(current.id, {
      ...move,
      promotion,
      expectedVersion: current.version,
    }).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.submitting.set(false)),
    ).subscribe({
      next: result => {
        // Ignore a response if navigation or a reload replaced its snapshot.
        if (this.game() !== current) {
          return;
        }

        this.game.set({
          ...current,
          currentFen: result.currentFen,
          version: result.version,
          status: result.status,
          result: result.result,
          terminationReason: result.terminationReason,
          finishedAt: result.finishedAt,
        });
      },
      error: (error: unknown) => {
        if (this.game() !== current) {
          return;
        }

        this.handleMoveError(error);
      },
    });
  }

  private handleMoveError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      const code: unknown = error.error?.code;

      if (
        code === 'STALE_GAME_VERSION' ||
        code === 'NOT_PLAYERS_TURN' ||
        code === 'GAME_ALREADY_FINISHED'
      ) {
        this.needsRefresh.set(true);
        this.moveMessage.set(
          'The displayed game state is out of date. Reloading it; your move will not be retried.',
        );

        this.refreshRequests.next();
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
          'The move request was invalid. Check the selected move.',
        );
        return;
      }

      if (error.status === 401) {
        // The existing authentication interceptor handles session cleanup.
        return;
      }

      if (error.status === 403 || error.status === 404) {
        this.needsRefresh.set(true);
        this.moveMessage.set(this.describeError(error));
        return;
      }
    }

    // A transport/server failure may occur after the move committed.
    // Read the server state before allowing another proposal.
    this.needsRefresh.set(true);
    this.moveMessage.set(
      'The move could not be confirmed. Refresh the game before trying another move.',
    );
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