import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from '../models/auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/auth';

  register(request: RegisterRequest) {
    return this.http.post<RegisterResponse>(
      `${this.baseUrl}/register`,
      request,
    );
  }

  login(request: LoginRequest) {
    return this.http.post<LoginResponse>(
      `${this.baseUrl}/login`,
      request,
    );
  }
}