import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { PlayerResponse } from '../models/player-response';
import { PlayerStatisticsResponse } from '../models/player-statistics-response';

@Injectable({
  providedIn: 'root',
})
export class PlayerApiService {
  private readonly http = inject(HttpClient);

  currentPlayer() {
    return this.http.get<PlayerResponse>('/api/v1/players/me');
  }
  updateUsername(username: string) {
    return this.http.patch<PlayerResponse>(
      '/api/v1/players/me',
      { username },
    );
  }

  currentStatistics() {
    return this.http.get<PlayerStatisticsResponse>(
      '/api/v1/players/me/statistics',
    );
  }
}