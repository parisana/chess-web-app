import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, defer, switchMap, tap, throwError } from 'rxjs';

import { LoginRequest } from '../models/auth.models';
import { AuthApiService } from './auth-api.service';
import { TokenStore } from './token-store.service';
import { PlayerApiService } from '../../player/services/player-api.service';
import { PlayerResponse } from '../../player/models/player-response';

@Injectable({
  providedIn: 'root',
})
// keep AuthApiService for transport and AuthService for orchestration
export class AuthService {
  private readonly authApi = inject(AuthApiService);
  private readonly playerApi = inject(PlayerApiService);
  private readonly tokenStore = inject(TokenStore);
  // Signals expose state that templates can read reactively
  private readonly playerState = signal<PlayerResponse | null>(null);

  readonly currentPlayer = this.playerState.asReadonly();

  // computed derives authentication status rather than maintaining a separate boolean that could fall out of sync
  readonly isAuthenticated = computed(
    () => this.tokenStore.token() !== null && this.playerState() !== null,
  );


  /**
    Submit credentials to login.
    Store the returned token.
    Request /players/me; the interceptor attaches that token.
    Store the player only after the profile request succeeds.
    If either request fails, clear both token and player.
   */
  login(request: LoginRequest) {
    // defer is being used as a guard to guarantee sequential execution and clean state cleanup at the exact moment the login process begins.
    return defer(() => {
      this.logout();

      return this.authApi.login(request).pipe(
        tap(response => this.tokenStore.set(response.accessToken)),
        switchMap(() => this.playerApi.currentPlayer()),
        tap(player => this.playerState.set(player)),
        catchError((error: unknown) => {
          this.logout();
          return throwError(() => error);
        }),
      );
    });
  }

  logout(): void {
    this.tokenStore.clear();
    this.playerState.set(null);
  }
}