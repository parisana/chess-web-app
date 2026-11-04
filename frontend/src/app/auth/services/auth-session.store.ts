import { computed, Injectable, signal } from '@angular/core';

import { PlayerResponse } from '../../player/models/player-response';

// The browser manages CHESS_SESSION. Angular only stores the profile and a local revision counter.
@Injectable({
  providedIn: 'root',
})
export class AuthSessionStore {
  private readonly playerState = signal<PlayerResponse | null>(null);
  private readonly revisionState = signal(0);

  readonly currentPlayer = this.playerState.asReadonly();
  readonly revision = this.revisionState.asReadonly();

  readonly isAuthenticated = computed(
    () => this.playerState() !== null,
  );

  /**
   * Invalidate callbacks belonging to an earlier local session.
   * This number is a frontend coordination value, not a credential.
   */
  begin(): number {
    this.revisionState.update(value => value + 1);
    this.playerState.set(null); // immediately clear player to prevent stale reads after login/logout.

    return this.revisionState();
  }

  setPlayer(player: PlayerResponse): void {
    this.playerState.set(player);
  }

  clear(): void {
    this.begin();
  }
}