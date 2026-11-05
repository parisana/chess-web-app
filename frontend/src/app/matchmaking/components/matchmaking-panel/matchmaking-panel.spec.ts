import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../auth/services/auth-session.store';
import { REALTIME_CONFIG } from '../../../notification/config/realtime.config';
import { RealtimeService } from '../../../notification/services/realtime.service';
import { QueueStatusResponse } from '../../models/queue-status-response';
import { MatchmakingApiService } from '../../services/matchmaking-api.service';
import { MatchmakingPanel } from './matchmaking-panel';

describe('Shared matchmaking panel', () => {
  let fixture: ComponentFixture<MatchmakingPanel>;
  let session: AuthSessionStore;
  let connections: Subject<void>;

  const status = vi.fn();
  const join = vi.fn();
  const leave = vi.fn();
  const navigate = vi.fn();

  const idle: QueueStatusResponse = {
    status: 'NOT_QUEUED',
    enqueuedAt: null,
    gameId: null,
  };

  const waiting: QueueStatusResponse = {
    status: 'WAITING',
    enqueuedAt: '2026-11-05T08:00:00Z',
    gameId: null,
  };

  const matched: QueueStatusResponse = {
    status: 'MATCHED',
    enqueuedAt: null,
    gameId: 42,
  };

  beforeEach(() => {
    connections = new Subject<void>();

    status.mockReset().mockReturnValue(of(idle));
    join.mockReset().mockReturnValue(of(waiting));
    leave.mockReset().mockReturnValue(of(undefined));
    navigate.mockReset().mockResolvedValue(true);

    TestBed.configureTestingModule({
      imports: [MatchmakingPanel],
      providers: [
        {
          provide: MatchmakingApiService,
          useValue: { status, join, leave },
        },
        {
          provide: Router,
          useValue: { navigate },
        },
        {
          provide: RealtimeService,
          useValue: {
            connections$: connections.asObservable(),
            updates$: new Subject(),
            state: signal('CONNECTED'),
          },
        },
        {
          provide: REALTIME_CONFIG,
          useValue: { reconciliationIntervalMs: 60_000 },
        },
      ],
    }).overrideTemplate(MatchmakingPanel, '');

    session = TestBed.inject(AuthSessionStore);
    session.begin();
    session.setPlayer({
      id: 1,
      username: 'John Doe',
      email: 'john.doe@example.com',
    });

    fixture = TestBed.createComponent(MatchmakingPanel);
  });

  afterEach(() => {
    fixture.destroy();
    connections.complete();
  });

  it('offers an existing game without redirecting during review', () => {
    status.mockReturnValue(of(matched));

    fixture.detectChanges();

    expect(fixture.componentInstance.queueStatusResponse()).toEqual(matched);
    expect(navigate).not.toHaveBeenCalled();
    expect(join).not.toHaveBeenCalled();

    fixture.componentInstance.openCurrentGame();

    expect(navigate).toHaveBeenCalledWith(
      ['/games', 42],
      { replaceUrl: false },
    );
  });

  it('automatically resumes an existing game from Home', () => {
    status.mockReturnValue(of(matched));
    fixture.componentRef.setInput('autoOpenExistingGame', true);

    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(
      ['/games', 42],
      { replaceUrl: true },
    );
  });

  it('joins immediately and opens the eventual match', () => {
    fixture.detectChanges();

    fixture.componentInstance.join();

    expect(join).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.queueStatusResponse()).toEqual(waiting);
    expect(navigate).not.toHaveBeenCalled();

    status.mockReturnValue(of(matched));
    connections.next();

    expect(navigate).toHaveBeenCalledWith(
      ['/games', 42],
      { replaceUrl: false },
    );
  });

  it('ignores a join response from an earlier authentication revision', () => {
    const response = new Subject<QueueStatusResponse>();
    join.mockReturnValue(response);

    fixture.detectChanges();
    fixture.componentInstance.join();

    session.begin();
    response.next(matched);
    response.complete();

    expect(navigate).not.toHaveBeenCalled();
    expect(fixture.componentInstance.queueStatusResponse()).toEqual(idle);
  });
});