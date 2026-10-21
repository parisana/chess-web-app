import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { QueueStatusResponse } from '../models/queue-status-response';

@Injectable({
  providedIn: 'root',
})
export class MatchmakingApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/matchmaking/queue';

  status() {
    return this.http.get<QueueStatusResponse>(
      `${this.baseUrl}/me`,
    );
  }

  join() {
    return this.http.post<QueueStatusResponse>(
      this.baseUrl,
      null,
    );
  }

  leave() {
    return this.http.delete<void>(
      `${this.baseUrl}/me`,
    );
  }
}