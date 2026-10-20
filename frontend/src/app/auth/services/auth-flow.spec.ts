import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import { AuthService } from './auth.service';
import { AuthSessionStore } from './auth-session.store';
import { authInterceptor } from '../interceptors/auth.interceptor';

describe('Authentication flow', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: AuthSessionStore;
  let auth: AuthService;

  const navigate = vi.fn().mockResolvedValue(true);

  const player = {
    id: 1,
    username: 'chessplayer',
    email: 'player@example.com',
  };

  beforeEach(() => {
    navigate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: Router,
          useValue: { navigate },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSessionStore);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    backend.verify();
  });

  it('attaches the token to protected API requests', () => {
    session.begin('test-token');

    http.get('/api/v1/players/me').subscribe();

    const request = backend.expectOne('/api/v1/players/me');

    expect(request.request.headers.get('Authorization'))
      .toBe('Bearer test-token');

    request.flush(player);
  });

  it('does not send the token to an external service', () => {
    session.begin('test-token');

    http.get('https://example.com/data').subscribe();

    const request = backend.expectOne('https://example.com/data');

    expect(request.request.headers.has('Authorization')).toBe(false);

    request.flush({});
  });

  it('clears an established session when a protected request returns 401', () => {
    session.begin('expired-token');
    session.setPlayer(player);

    const onError = vi.fn();

    http.get('/api/v1/players/me').subscribe({
      error: onError,
    });

    backend.expectOne('/api/v1/players/me').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(onError).toHaveBeenCalled();
    expect(session.token()).toBeNull();
    expect(session.currentPlayer()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);

    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { sessionExpired: 'true' },
      replaceUrl: true,
    });
  });

  it('clears the token when profile loading fails after login', () => {
    const onError = vi.fn();

    auth.login({
      email: player.email,
      password: 'StrongPassword123!',
    }).subscribe({
      error: onError,
    });

    const loginRequest = backend.expectOne('/api/v1/auth/login');

    expect(loginRequest.request.method).toBe('POST');
    expect(loginRequest.request.headers.has('Authorization')).toBe(false);

    loginRequest.flush({
      accessToken: 'new-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    const profileRequest = backend.expectOne('/api/v1/players/me');

    expect(profileRequest.request.headers.get('Authorization'))
      .toBe('Bearer new-token');

    profileRequest.flush(null, {
      status: 500,
      statusText: 'Internal Server Error',
    });

    expect(onError).toHaveBeenCalledWith(
      expect.any(HttpErrorResponse),
    );
    expect(session.token()).toBeNull();
    expect(session.currentPlayer()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logout clears the token and player', () => {
    session.begin('test-token');
    session.setPlayer(player);

    auth.logout();

    expect(session.token()).toBeNull();
    expect(session.currentPlayer()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });
});

// | Tool                       | Rough Java equivalent |
// |---                         |---|
// | `describe(...)`            | Groups related tests |
// | `it(...)`                  | A JUnit test method |
// | `beforeEach(...)`          | `@BeforeEach` |
// | `expect(...)`              | An assertion |
// | `vi.fn()`                  | A Mockito-style mock function |
// | `TestBed`                  | An isolated Angular dependency-injection container |
// | `backend.expectOne(...)`   | Capture the expected outgoing HTTP request |
// | `request.flush(...)`       | Supply the fake server response |