import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  defer,
  distinctUntilChanged,
  EMPTY,
  finalize,
  map,
  Observable,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { AuthSessionStore } from '../../../auth/services/auth-session.store';
import { GameTerminationReason } from '../../models/game-response';
import {
  PlayedGameOutcome,
  PlayedGamesResponse,
} from '../../models/played-games';
import { GameApiService } from '../../services/game-api.service';

@Component({
  selector: 'app-played-games',
  imports: [DatePipe, RouterLink],
  templateUrl: './played-games.html',
  styleUrl: './played-games.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayedGames implements OnInit {
  readonly session = inject(AuthSessionStore);

  private readonly api = inject(GameApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshRequests = new Subject<void>();

  readonly pageSize = 20;
  readonly requestedPage = signal<number | null>(0);
  readonly result = signal<PlayedGamesResponse | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly outcomeLabels: Record<PlayedGameOutcome, string> = {
    WIN: 'Win',
    LOSS: 'Loss',
    DRAW: 'Draw',
  };

  readonly terminationLabels: Record<GameTerminationReason, string> = {
    CHECKMATE: 'Checkmate',
    STALEMATE: 'Stalemate',
    RESIGNATION: 'Resignation',
    DRAW_AGREEMENT: 'Agreed draw',
    TIMEOUT: 'Time expired',
  };

  ngOnInit(): void {
    this.route.queryParamMap.pipe(
      map(params => this.parsePage(params.get('page'))),
      distinctUntilChanged(),

      switchMap(page => {
        this.requestedPage.set(page);
        this.result.set(null);
        this.errorMessage.set(null);

        if (page === null) {
          this.loading.set(false);
          this.errorMessage.set('The page number is invalid.');
          return EMPTY;
        }

        return this.refreshRequests.pipe(
          startWith(undefined),
          switchMap(() => this.loadPage(page)),
        );
      }),

      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  refresh(): void {
    if (!this.loading() && this.requestedPage() !== null) {
      this.refreshRequests.next();
    }
  }

  previous(): void {
    const current = this.result();

    if (current !== null && current.page > 0) {
      this.openPage(current.page - 1);
    }
  }

  next(): void {
    const current = this.result();

    if (current?.hasNext) {
      this.openPage(current.page + 1);
    }
  }

  private openPage(page: number): void {
    if (this.loading()) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page === 0 ? null : page },
      queryParamsHandling: 'merge',
    });
  }

  private parsePage(raw: string | null): number | null {
    if (raw === null) {
      return 0;
    }

    if (!/^(0|[1-9][0-9]*)$/.test(raw)) {
      return null;
    }

    const page = Number(raw);

    return Number.isSafeInteger(page) &&
      page * this.pageSize <= 2_147_483_647
      ? page
      : null;
  }

  private loadPage(page: number): Observable<PlayedGamesResponse> {
    return defer(() => {
      const revision = this.session.revision();
      const playerId = this.session.currentPlayer()?.id;

      this.result.set(null);
      this.errorMessage.set(null);

      if (!this.session.isAuthenticated() || playerId === undefined) {
        this.loading.set(false);
        return EMPTY;
      }

      const isCurrent = () =>
        this.session.revision() === revision &&
        this.session.currentPlayer()?.id === playerId;

      this.loading.set(true);

      return this.api.getPlayedGames(page, this.pageSize).pipe(
        tap(response => {
          if (isCurrent()) {
            this.result.set(response);
          }
        }),

        catchError((error: unknown) => {
          if (isCurrent()) {
            this.errorMessage.set(this.describeError(error));
          }

          // Keep route changes and later refresh attempts usable.
          return EMPTY;
        }),

        finalize(() => this.loading.set(false)),
      );
    });
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'Unable to reach the server. Please try again.';
      }

      if (error.status === 401) {
        return 'Your session is no longer valid. Please sign in again.';
      }
    }

    return 'Unable to load your games. Please try again.';
  }
}