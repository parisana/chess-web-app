import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
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
import { AuthSessionEventsService } from './auth-session-events.service';
import { SessionSyncService } from './session-sync.service';

describe('Cross-tab session synchronization', () => {
  let changes: Subject<void>;
  let session: AuthSessionStore;

  const navigateByUrl = vi.fn();
  const restore = vi.fn();

  const player = {
    id: 1,
    username: 'chessplayer',
    email: 'player@example.com',
  };

  beforeEach(() => {
    changes = new Subject<void>();

    navigateByUrl.mockReset();
    navigateByUrl.mockResolvedValue(true);

    restore.mockReset();
    restore.mockReturnValue(of(false));

    TestBed.configureTestingModule({
      providers: [
        {
          provide: Router,
          useValue: { navigateByUrl },
        },
        {
          provide: AuthService,
          useValue: { restore },
        },
        {
          provide: AuthSessionEventsService,
          useValue: { changes$: changes.asObservable() },
        },
      ],
    });

    session = TestBed.inject(AuthSessionStore);
    session.begin();
    session.setPlayer(player);

    TestBed.inject(SessionSyncService);
  });

  afterEach(() => {
    changes.complete();
  });

  it('clears old identity immediately and asks the server', async () => {
    const previousRevision = session.revision();

    changes.next();

    // Clear before the navigation promise or HTTP restoration finishes.
    expect(session.isAuthenticated()).toBe(false);
    expect(session.revision()).toBeGreaterThan(previousRevision);

    await vi.waitFor(() => {
      expect(restore).toHaveBeenCalledTimes(1);
    });

    expect(navigateByUrl).toHaveBeenCalledWith('/login', {
      replaceUrl: true,
    });

    // The server reported no authenticated session.
    expect(navigateByUrl).not.toHaveBeenCalledWith('/home', {
      replaceUrl: true,
    });
  });

  it('opens Home when the server restores an authenticated session', async () => {
    restore.mockReturnValue(of(true));

    changes.next();

    await vi.waitFor(() => {
      expect(navigateByUrl).toHaveBeenCalledWith('/home', {
        replaceUrl: true,
      });
    });

    expect(restore).toHaveBeenCalledTimes(1);
  });

  it('does not restore over a newer local login', async () => {
    let finishNavigation!: (value: boolean) => void;

    const pendingNavigation = new Promise<boolean>(resolve => {
      finishNavigation = resolve;
    });

    navigateByUrl.mockReturnValueOnce(pendingNavigation);

    changes.next();

    // Another local authentication operation begins while navigation waits.
    session.begin();
    session.setPlayer({ ...player, id: 2 });

    finishNavigation(true);
    await pendingNavigation;
    await Promise.resolve();

    expect(restore).not.toHaveBeenCalled();
    expect(session.currentPlayer()?.id).toBe(2);
  });
});