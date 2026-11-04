import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpXsrfTokenExtractor,
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
import { AuthSessionEventsService } from './auth-session-events.service';

describe('Session authentication flow', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: AuthSessionStore;
  let auth: AuthService;

  const navigate = vi.fn().mockResolvedValue(true);
  const notifyChanged = vi.fn();

  const player = {
    id: 1,
    username: 'chessplayer',
    email: 'player@example.com',
  };

  beforeEach(() => {
    navigate.mockClear();
    notifyChanged.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: HttpXsrfTokenExtractor,
          useValue: { getToken: () => 'fixture-csrf' },
        },
        {
          provide: Router,
          useValue: { navigate },
        },
        {
          provide: AuthSessionEventsService,
          useValue: { notifyChanged },
        }
      ],
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSessionStore);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => backend.verify());

  it('restores the Player without attaching a bearer token', () => {
    auth.restore().subscribe();

    backend.expectOne('/api/v1/auth/csrf').flush(null);

    const request = backend.expectOne('/api/v1/players/me');

    expect(request.request.headers.has('Authorization')).toBe(false);

    request.flush(player);
    expect(notifyChanged).toHaveBeenCalledTimes(1);

    expect(auth.currentPlayer()).toEqual(player);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('initializes CSRF before login and again after login', () => {
    auth.login({
      email: player.email,
      password: 'StrongPassword123!',
    }).subscribe();

    backend.expectOne('/api/v1/auth/csrf').flush(null);

    const request = backend.expectOne('/api/v1/auth/login');

    expect(request.request.headers.get('X-XSRF-TOKEN'))
      .toBe('fixture-csrf');

    expect(request.request.headers.has('Authorization')).toBe(false);

    request.flush(player);

    // Don't enable the authenticated UI until CSRF initialization finishes.
    expect(auth.isAuthenticated()).toBe(false);
    expect(notifyChanged).not.toHaveBeenCalled();

    backend.expectOne('/api/v1/auth/csrf').flush(null);

    expect(auth.currentPlayer()).toEqual(player);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('does not let an old request clear a newer local session', () => {
    session.begin();
    session.setPlayer(player);

    http.get('/api/v1/players/me').subscribe({
      error: () => { },
    });

    const oldRequest = backend.expectOne('/api/v1/players/me');

    session.begin();
    session.setPlayer({ ...player, id: 2 });

    oldRequest.flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(session.currentPlayer()?.id).toBe(2);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('clears the current session after a protected request returns 401', () => {
    session.begin();
    session.setPlayer(player);

    http.get('/api/v1/players/me').subscribe({
      error: () => { },
    });

    backend.expectOne('/api/v1/players/me').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(auth.isAuthenticated()).toBe(false);

    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { sessionExpired: 'true' },
      replaceUrl: true,
    });
  });

  it('does not claim logout succeeded when its HTTP request fails', () => {
    session.begin();
    session.setPlayer(player);

    auth.logout().subscribe({
      error: () => { },
    });

    backend.expectOne('/api/v1/auth/csrf').flush(null);

    backend.expectOne('/api/v1/auth/logout').flush(null, {
      status: 500,
      statusText: 'Internal Server Error',
    });

    expect(auth.isAuthenticated()).toBe(true);
  });

  it('clears local authentication after successful server logout', () => {
    session.begin();
    session.setPlayer(player);

    auth.logout().subscribe();

    backend.expectOne('/api/v1/auth/csrf').flush(null);
    backend.expectOne('/api/v1/auth/logout').flush(null);

    expect(auth.isAuthenticated()).toBe(false);
    expect(notifyChanged).toHaveBeenCalledTimes(1);

    backend.expectOne('/api/v1/auth/csrf').flush(null);
  });
});