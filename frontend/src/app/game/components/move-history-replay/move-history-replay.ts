import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';

import { GameHistoryResponse } from '../../models/game-history';

@Component({
  selector: 'app-move-history-replay',
  imports: [],
  templateUrl: './move-history-replay.html',
  styleUrl: './move-history-replay.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MoveHistoryReplay {
  readonly history = input<GameHistoryResponse | null>(null);
  readonly historyLoading = input(false);
  readonly historyError = input<string | null>(null);
  readonly historyNeedsRefresh = input(false);

  readonly replayIndex = input<number | null>(null);

  // Game owns permission decisions; this component only displays them.
  readonly canNavigate = input(false);
  readonly canPrevious = input(false);
  readonly canNext = input(false);
  readonly canReturnLatest = input(false);

  readonly positionSelected = output<number>();
  readonly previousRequested = output<void>();
  readonly nextRequested = output<void>();
  readonly latestRequested = output<void>();

  readonly isReplaying = computed(() => this.replayIndex() !== null);

  /**
   * Presentation numbering begins at the recorded baseline.
   * Sequence numbers remain the identifiers emitted for replay selection.
   */
  readonly moveTokens = computed(() => {
    const recorded = this.history();

    if (recorded === null) {
      return [];
    }

    const fields = recorded.historyStartFen.trim().split(/\s+/);
    const startsWithBlack = fields[1] === 'b';
    const firstMoveNumber = Number(fields[5]);

    return recorded.moves.map((move, index) => {
      const halfMoveOffset = index + (startsWithBlack ? 1 : 0);
      const whiteMove = halfMoveOffset % 2 === 0;
      const moveNumber = firstMoveNumber + Math.floor(halfMoveOffset / 2);

      return {
        ...move,
        numberLabel: whiteMove
          ? `${moveNumber}.`
          : index === 0 ? `${moveNumber}…` : '',
        notation: move.san ??
          `${move.from}-${move.to}${move.promotion ? '=' + move.promotion : ''}`,
      };
    });
  });
}