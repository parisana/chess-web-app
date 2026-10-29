import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { GameResponse } from '../models/game-response';
import { SubmitMoveRequest, GameMoveResult } from '../models/game-move';

@Injectable({
  providedIn: 'root',
})
export class GameApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/games';

  getGame(gameId: number) {
    return this.http.get<GameResponse>(
      `${this.baseUrl}/${gameId}`,
    );
  }

  submitMove(gameId: number, request: SubmitMoveRequest) {
    return this.http.post<GameMoveResult>(
      `${this.baseUrl}/${gameId}/moves`,
      request,
    );
  }
}