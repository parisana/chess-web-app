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
import { finalize, map, Observable, switchMap } from 'rxjs';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthService } from '../../../auth/services/auth.service';
import { QueueStatusResponse } from '../../../matchmaking/models/queue-status-response';
import { MatchmakingApiService } from '../../../matchmaking/services/matchmaking-api.service';
import { ProfileEditor } from '../../../player/components/profile-editor/profile-editor';

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
  template: `
    <main class="home-page">
      @if (auth.currentPlayer(); as player) {
        <header>
          <h1>Welcome, {{ player.username }}</h1>
          <button mat-button type="button" (click)="logout()">
            Log out
          </button>
        </header>
        <app-profile-editor />
        <mat-card [attr.aria-busy]="busy()">
          <mat-card-header>
            <mat-card-title>Matchmaking</mat-card-title>
            <mat-card-subtitle>
              Join or leave the waiting queue
            </mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>
            @if (queue(); as current) {
              @if (current.status === 'WAITING') {
                <p role="status">
                  You are in the queue.
                  Joined at {{ current.enqueuedAt | date:'mediumTime' }}.
                </p>
              } @else if (current.status === 'MATCHED') {
                <p role="status">
                  Match found. Your game ID is {{ current.gameId }}.
                </p>
                <a mat-flat-button [routerLink]="['/games', current.gameId]" >
                  Open game
                </a>
              } @else {
                <p role="status">You are not in the queue.</p>
              }
            } @else {
              <p>Queue status has not been loaded.</p>
            }

            <p>Refresh your status to check whether a match has been found.</p>

            @if (errorMessage(); as message) {
              <p role="alert">{{ message }}</p>
            }
          </mat-card-content>

          <mat-card-actions>
            @if (queue()?.status === 'NOT_QUEUED') {
              <button
                mat-flat-button
                type="button"
                [disabled]="busy()"
                (click)="join()"
              >
                Join queue
              </button>
            }

            @if (queue()?.status === 'WAITING') {
              <button
                mat-flat-button
                type="button"
                [disabled]="busy()"
                (click)="leave()"
              >
                Leave queue
              </button>
            }

            <button
              mat-button
              type="button"
              [disabled]="busy()"
              (click)="loadStatus()"
            >
              Refresh status
            </button>
          </mat-card-actions>

          @if (busy()) {
            <mat-progress-bar
              mode="indeterminate"
              aria-label="Updating queue status"
            />
          }
        </mat-card>
      }
    </main>
  `,
  styles: `
    .home-page {
      max-width: 48rem;
      margin: 2rem auto;
      padding: 0 1rem;
    }

    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
    }

    mat-card-content {
      padding-top: 1rem;
    }

    mat-card-actions {
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    [role="alert"] {
      color: var(--mat-sys-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home implements OnInit {
  readonly auth = inject(AuthService);

  private readonly router = inject(Router);
  private readonly matchmakingApi = inject(MatchmakingApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly queue = signal<QueueStatusResponse | null>(null);
  readonly busy = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadStatus();
  }

  // Load status when home opens. PostgreSQL owns membership; the component reads that state.
  loadStatus(): void {
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
    if (this.busy()) {
      return;
    }

    this.errorMessage.set(null);
    this.busy.set(true);

    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.busy.set(false)),
    ).subscribe({
      next: response => this.queue.set(response),
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