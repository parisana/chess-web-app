import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { GameResponse } from '../models/game-response';
import { SubmitMoveRequest, GameMoveResult } from '../models/game-move';
import { GameHistoryResponse } from '../models/game-history';
import { GameLifecycleAction, GameLifecycleRequest } from '../models/game-lifecycle';

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

  getHistory(gameId: number) {
    return this.http.get<GameHistoryResponse>(
      `${this.baseUrl}/${gameId}/moves`,
    );
  }

  submitLifecycleAction(
    gameId: number,
    action: GameLifecycleAction,
    request: GameLifecycleRequest,
  ) {
    const paths: Record<GameLifecycleAction, string> = {
      'resign': 'resignation',
      'offer-draw': 'draw-offer',
      'accept-draw': 'draw-offer/accept',
      'decline-draw': 'draw-offer/decline',
      'withdraw-draw': 'draw-offer/withdraw',
    };

    return this.http.post<GameResponse>(
      `${this.baseUrl}/${gameId}/${paths[action]}`,
      request,
    );
  }
}