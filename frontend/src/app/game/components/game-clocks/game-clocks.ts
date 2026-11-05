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

import { GameClockResponse } from '../../models/game-clock-response';
import { NgTemplateOutlet } from '@angular/common';
import { BoardColor } from '../../models/fen-display';

interface ClockAnchor {
  snapshot: GameClockResponse;
  receivedAt: number;
}

@Component({
  imports: [NgTemplateOutlet],
  selector: 'app-game-clocks',
  standalone: true,
  templateUrl: './game-clocks.html',
  styleUrl: './game-clocks.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameClocks {
  readonly clock = input<GameClockResponse | null>(null);

  // Orientation identifies the colour displayed at the bottom.
  readonly orientation = input<BoardColor>('WHITE');
  readonly viewerColor = input<BoardColor>('WHITE');

  readonly whiteName = input('White');
  readonly blackName = input('Black');

  private readonly destroyRef = inject(DestroyRef);
  private readonly anchor = signal<ClockAnchor | null>(null);
  private readonly tick = signal(performance.now());
  readonly whiteRating = input<number | null>(null);
  readonly blackRating = input<number | null>(null);

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

      if (snapshot === null) {
        this.anchor.set(null);
        return;
      }

      const receivedAt = performance.now();

      // Only a new clock snapshot resets the countdown anchor.
      // Names, orientation and other display changes do not reset time.
      this.anchor.set({ snapshot, receivedAt });
      this.tick.set(receivedAt);
    });

    interval(100).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.tick.set(performance.now());
    });
  }

  readonly topClock = computed(() =>
    this.clockRow(this.orientation() === 'WHITE' ? 'BLACK' : 'WHITE')
  );

  readonly bottomClock = computed(() =>
    this.clockRow(this.orientation())
  );

  private clockRow(color: BoardColor) {
    const current = this.display();

    return {
      color,
      name: color === 'WHITE' ? this.whiteName() : this.blackName(),
      time: color === 'WHITE'
        ? current?.white ?? '—'
        : current?.black ?? '—',
      running: current?.runningColor === color,
      isYou: color === this.viewerColor(),
      timed: this.clock() !== null,
      rating: color === 'WHITE'
        ? this.whiteRating()
        : this.blackRating(),
    };
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