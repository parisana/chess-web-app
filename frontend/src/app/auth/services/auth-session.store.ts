import { computed, Injectable, signal } from '@angular/core';

import { PlayerResponse } from '../../player/models/player-response';

@Injectable({
  providedIn: 'root',
})
export class AuthSessionStore {
  private readonly tokenState = signal<string | null>(null);
  private readonly playerState = signal<PlayerResponse | null>(null);

  readonly token = this.tokenState.asReadonly();
  readonly currentPlayer = this.playerState.asReadonly();

  readonly isAuthenticated = computed(
    () => this.tokenState() !== null && this.playerState() !== null,
  );

  begin(token: string): void {
    this.playerState.set(null);
    this.tokenState.set(token);
  }

  setPlayer(player: PlayerResponse): void {
    this.playerState.set(player);
  }

  clear(): void {
    this.tokenState.set(null);
    this.playerState.set(null);
  }
}