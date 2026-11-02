import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  catchError,
  EMPTY,
  finalize,
  merge,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthSessionStore } from '../../../auth/services/auth-session.store';
import { RealtimeService } from '../../../notification/services/realtime.service';
import { PlayerStatisticsResponse } from '../../models/player-statistics-response';
import { PlayerApiService } from '../../services/player-api.service';

@Component({
  selector: 'app-player-statistics',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
  ],
  templateUrl: './player-statistics.html',
  styleUrl: './player-statistics.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerStatistics implements OnInit {
  private readonly api = inject(PlayerApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly realtime = inject(RealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly refreshRequests = new Subject<void>();

  readonly statistics = signal<PlayerStatisticsResponse | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    merge(
      this.refreshRequests,
      this.realtime.connections$,
      this.realtime.updates$,
    ).pipe(
      startWith(undefined),

      // These are reads, so a newer refresh may cancel an older request.
      switchMap(() => {
        const token = this.session.token();
        const playerId = this.session.currentPlayer()?.id;

        if (!token || playerId === undefined) {
          this.statistics.set(null);
          return EMPTY;
        }

        const isCurrentSession = () =>
          this.session.token() === token &&
          this.session.currentPlayer()?.id === playerId;

        this.loading.set(true);
        this.errorMessage.set(null);

        return this.api.currentStatistics().pipe(
          tap(response => {
            if (
              isCurrentSession() &&
              response.playerId === playerId
            ) {
              this.statistics.set(response);
            }
          }),
          catchError((error: unknown) => {
            if (isCurrentSession()) {
              this.errorMessage.set(this.describeError(error));
            }

            return EMPTY;
          }),
          finalize(() => this.loading.set(false)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  refresh(): void {
    this.refreshRequests.next();
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

    return 'Unable to load statistics. Please try again.';
  }
}