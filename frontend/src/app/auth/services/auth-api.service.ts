import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap } from 'rxjs';

import {
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
} from '../models/auth.models';
import { PlayerResponse } from '../../player/models/player-response';

@Injectable({
  providedIn: 'root',
})
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/auth';

  csrf() {
    return this.http.get<void>(`${this.baseUrl}/csrf`);
  }

  register(request: RegisterRequest) {
    // Registration also requires CSRF protection.
    return this.csrf().pipe(
      switchMap(() => this.http.post<RegisterResponse>(
        `${this.baseUrl}/register`,
        request,
      )),
    );
  }

  login(request: LoginRequest) {
    return this.http.post<PlayerResponse>(
      `${this.baseUrl}/login`,
      request,
    );
  }

  logout() {
    return this.http.post<void>(
      `${this.baseUrl}/logout`,
      {},
    );
  }
}