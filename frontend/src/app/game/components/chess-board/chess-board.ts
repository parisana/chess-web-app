import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';

import {
  BoardColor,
  DisplayPiece,
  DisplayPosition,
  parseFenForDisplay,
} from '../../models/fen-display';
import { ProposedMove } from '../../models/game-move';

interface BoardCell {
  square: string;
  dark: boolean;
  piece: DisplayPiece | null;
  label: string;
}

interface BoardRow {
  rank: number;
  cells: BoardCell[];
}

@Component({
  selector: 'app-chess-board',
  standalone: true,
  templateUrl: './chess-board.html',
  styleUrl: './chess-board.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChessBoard {
  readonly fen = input.required<string>();
  readonly orientation = input<BoardColor>('WHITE');
  readonly enabled = input(false);

  readonly moveProposed = output<ProposedMove>();
  readonly selectedSquare = signal<string | null>(null);

  readonly position = computed<DisplayPosition | null>(() => {
    try {
      return parseFenForDisplay(this.fen());
    } catch {
      return null;
    }
  });

  readonly rows = computed<BoardRow[]>(() => {
    const position = this.position();

    if (!position) {
      return [];
    }

    const whiteOrientation = this.orientation() === 'WHITE';

    const files = whiteOrientation
      ? [...'abcdefgh']
      : [...'hgfedcba'];

    const ranks = whiteOrientation
      ? [8, 7, 6, 5, 4, 3, 2, 1]
      : [1, 2, 3, 4, 5, 6, 7, 8];

    return ranks.map(rank => ({
      rank,
      cells: files.map(file => {
        const square = `${file}${rank}`;
        const piece = position.pieces.get(square) ?? null;
        const fileIndex = 'abcdefgh'.indexOf(file);

        return {
          square,
          // a1 is dark, regardless of the displayed orientation.
          dark: (fileIndex + rank) % 2 === 1,
          piece,
          label: piece
            ? `${square}: ${piece.color === 'WHITE' ? 'White' : 'Black'
            } ${piece.name}`
            : `${square}: empty`,
        };
      }),
    }));
  });

  constructor() {
    effect(() => {
      // A changed position, orientation or interaction state clears selection.
      this.fen();
      this.orientation();
      this.enabled();

      this.selectedSquare.set(null);
    });
  }

  selectSquare(square: string): void {
    if (!this.enabled()) {
      return;
    }

    const position = this.position();

    if (!position) {
      return;
    }

    const selected = this.selectedSquare();
    const piece = position.pieces.get(square);

    // selected square is clicked again, deselect it.
    if (selected === square) {
      this.selectedSquare.set(null);
      return;
    }

    // Clicking an own piece selects it or replaces the previous selection.
    if (piece?.color === this.orientation()) {
      this.selectedSquare.set(square);
      return;
    }

    // selected [previous] square is an own piece, and a different square is clicked. Propose a move if the clicked square is empty or contains an opponent's piece.
    if (selected !== null) {
      this.selectedSquare.set(null);
      this.moveProposed.emit({
        from: selected,
        to: square,
      });
    }
  }
}