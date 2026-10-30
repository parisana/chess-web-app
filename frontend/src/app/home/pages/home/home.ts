import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, finalize, interval, map, merge, Observable, switchMap } from 'rxjs';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthService } from '../../../auth/services/auth.service';
import { QueueStatusResponse } from '../../../matchmaking/models/queue-status-response';
import { MatchmakingApiService } from '../../../matchmaking/services/matchmaking-api.service';
import { ProfileEditor } from '../../../player/components/profile-editor/profile-editor';
import { REALTIME_CONFIG } from '../../../notification/config/realtime.config';
import { RealtimeService } from '../../../notification/services/realtime.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    ProfileEditor
  ],
  templateUrl: './home.html',
  styleUrl: './home.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home implements OnInit {
  readonly auth = inject(AuthService);

  private readonly router = inject(Router);
  private readonly matchmakingApi = inject(MatchmakingApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly queueStatusResponse = signal<QueueStatusResponse | null>(null);
  readonly busy = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly realtimeService = inject(RealtimeService);

  private readonly realtimeConfig = inject(REALTIME_CONFIG);

  // Coalesce status-read requests received while another request is running.
  private statusReadPending = false;

  ngOnInit(): void {
    merge(
      this.realtimeService.connections$,
      this.realtimeService.updates$,
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.requestStatusRead());

    interval(this.realtimeConfig.reconciliationIntervalMs).pipe(
      filter(() => {
        const status = this.queueStatusResponse()?.status;

        // Recover missed matching/completion notifications and failed reads.
        // An idle Player outside the queue needs no periodic status read.
        return status === undefined ||
          status === 'WAITING' ||
          status === 'MATCHED';
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.requestStatusRead());

    this.loadStatus();
  }

  // Load status when home opens. PostgreSQL owns membership; the component reads that state.
  // We still fetch queue status rather than deriving it from the notification.
  loadStatus(): void {
    this.requestStatusRead();
  }

  private requestStatusRead(): void {
    if (
      this.destroyRef.destroyed ||
      !this.auth.isAuthenticated()
    ) {
      return;
    }

    this.statusReadPending = true;
    this.flushPendingStatusRead();
  }

  private flushPendingStatusRead(): void {
    if (
      !this.statusReadPending ||
      this.busy() ||
      this.destroyRef.destroyed ||
      !this.auth.isAuthenticated()
    ) {
      return;
    }

    this.statusReadPending = false;
    this.runRequest(this.matchmakingApi.status());
  }

  join(): void {
    this.runRequest(this.matchmakingApi.join());
  }

  leave(): void {
    this.runRequest(
      this.matchmakingApi.leave().pipe(
        switchMap(() => this.matchmakingApi.status()),
      ),
    );
  }

  logout(): void {
    this.auth.logout();

    void this.router.navigateByUrl('/login', {
      replaceUrl: true,
    });
  }

  private runRequest(
    request: Observable<QueueStatusResponse>,
  ): void {
    if (
      this.busy() ||
      this.destroyRef.destroyed ||
      !this.auth.isAuthenticated()
    ) {
      return;
    }

    this.errorMessage.set(null);
    this.busy.set(true);

    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.busy.set(false);

        // Let the current subscription finish before draining a queued read.
        // Destruction/logout guards are checked again inside the helper.
        if (this.statusReadPending) {
          queueMicrotask(() => this.flushPendingStatusRead());
        }
      }),
    ).subscribe({
      next: response => this.queueStatusResponse.set(response),
      error: (error: unknown) => {
        this.errorMessage.set(this.describeError(error));
      },
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

    return 'Unable to update the queue. Refresh its status and try again.';
  }
}