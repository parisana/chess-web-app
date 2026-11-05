import { Component, DestroyRef, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { AuthService } from './auth/services/auth.service';
import { AuthSessionStore } from './auth/services/auth-session.store';
import { SessionSyncService } from './auth/services/session-sync.service';
import { RealtimeService } from './notification/services/realtime.service';

type ChessTheme = 'amber' | 'blue' | 'green';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
  host: {
    '[attr.data-theme]': 'theme',
  },
})
export class App {
  // Change this value to switch the application and board palette.
  readonly theme: ChessTheme = 'amber';

  readonly auth = inject(AuthService);
  readonly signingOut = signal(false);
  readonly logoutError = signal<string | null>(null);

  private readonly session = inject(AuthSessionStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // These lifecycles remain independent of Home/Game navigation.
  protected readonly realtime = inject(RealtimeService);

  constructor() {
    inject(SessionSyncService);
  }

  logout(): void {
    if (this.signingOut() || !this.auth.isAuthenticated()) {
      return;
    }

    const revision = this.session.revision();

    this.signingOut.set(true);
    this.logoutError.set(null);

    this.auth.logout().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.signingOut.set(false)),
    ).subscribe({
      next: () => {
        // A newer login must not be redirected by an older logout callback.
        if (!this.auth.isAuthenticated()) {
          void this.router.navigateByUrl('/login', {
            replaceUrl: true,
          });
        }
      },
      error: () => {
        if (this.session.revision() === revision) {
          this.logoutError.set(
            'Unable to complete sign-out. Please try again.',
          );
        }
      },
    });
  }
}