import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

import { GameClockResponse } from '../../models/game-clock';

interface ClockAnchor {
  snapshot: GameClockResponse;
  receivedAt: number;
}

@Component({
  selector: 'app-game-clocks',
  standalone: true,
  templateUrl: './game-clocks.html',
  styleUrl: './game-clocks.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameClocks {
  readonly clock = input.required<GameClockResponse>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly anchor = signal<ClockAnchor | null>(null);
  private readonly tick = signal(performance.now());

  readonly display = computed(() => {
    const anchor = this.anchor();

    if (anchor === null) {
      return null;
    }

    const snapshot = anchor.snapshot;
    const elapsed = Math.max(
      0,
      this.tick() - anchor.receivedAt,
    );

    const white = snapshot.runningColor === 'WHITE'
      ? Math.max(0, snapshot.whiteRemainingMillis - elapsed)
      : snapshot.whiteRemainingMillis;

    const black = snapshot.runningColor === 'BLACK'
      ? Math.max(0, snapshot.blackRemainingMillis - elapsed)
      : snapshot.blackRemainingMillis;

    return {
      white: this.format(white),
      black: this.format(black),
      runningColor: snapshot.runningColor,
      awaitingResult:
        (snapshot.runningColor === 'WHITE' && white <= 0) ||
        (snapshot.runningColor === 'BLACK' && black <= 0),
    };
  });

  constructor() {
    effect(() => {
      const snapshot = this.clock();
      const receivedAt = performance.now();

      // Each accepted server clock response establishes a fresh anchor.
      // Browser wall-clock time is never compared with server timestamps.
      this.anchor.set({ snapshot, receivedAt });
      this.tick.set(receivedAt);
    });

    interval(100).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.tick.set(performance.now());
    });
  }

  private format(milliseconds: number): string {
    // Keep a positive fraction of a second visible as one second.
    const totalSeconds = Math.ceil(
      Math.max(0, milliseconds) / 1_000,
    );

    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }
}