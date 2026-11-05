import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  HostListener,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import {
  BoardColor,
  DisplayPiece,
  DisplayPosition,
  parseFenForDisplay,
} from '../../models/fen-display';
import { PromotionPiece, ProposedMove } from '../../models/game-move';
import { A11yModule } from '@angular/cdk/a11y';
import { LegalMoveHint } from '../../models/game-legal-moves';

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

const PROMOTION_GLYPHS: Record<
  BoardColor,
  Record<PromotionPiece, string>
> = {
  WHITE: {
    QUEEN: '♕',
    ROOK: '♖',
    BISHOP: '♗',
    KNIGHT: '♘',
  },
  BLACK: {
    QUEEN: '♛',
    ROOK: '♜',
    BISHOP: '♝',
    KNIGHT: '♞',
  },
};

@Component({
  imports: [A11yModule],
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

  readonly promotionMove = input<ProposedMove | null>(null);
  readonly promotionEnabled = input(false);

  readonly promotionChosen = output<PromotionPiece>();
  readonly promotionCancelled = output<void>();

  private readonly boardSurface =
    viewChild<ElementRef<HTMLElement>>('boardSurface');

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

  /**
   * Locate the destination in the displayed board, accounting for orientation.
   * The queen sits on that square; the other options extend toward the centre.
   */
  readonly promotionMenu = computed(() => {
    const move = this.promotionMove();
    const position = this.position();

    if (move === null || position === null) {
      return null;
    }

    const pawn = position.pieces.get(move.from);

    if (pawn?.name !== 'pawn') {
      return null;
    }

    const rows = this.rows();
    const rowIndex = rows.findIndex(row =>
      row.cells.some(cell => cell.square === move.to)
    );

    if (rowIndex < 0) {
      return null;
    }

    const columnIndex = rows[rowIndex].cells.findIndex(
      cell => cell.square === move.to
    );

    const opensUp = rowIndex >= 4;
    const pieces: readonly PromotionPiece[] = [
      'QUEEN',
      'ROOK',
      'BISHOP',
      'KNIGHT',
    ];

    return {
      color: pawn.color,
      destination: move.to,
      left: columnIndex * 12.5,
      top: (opensUp ? rowIndex - 3 : rowIndex) * 12.5,
      opensUp,
      choices: pieces.map(piece => ({
        piece,
        label: piece.toLowerCase(),
        glyph: PROMOTION_GLYPHS[pawn.color][piece],
      })),
    };
  });

  readonly legalMoves = input<readonly LegalMoveHint[] | null>(null);
  readonly rejectedMove = input<ProposedMove | null>(null);

  readonly failedMove = signal<ProposedMove | null>(null);

  /**
   * Multiple promotion choices share one destination.
   * A Map keeps one marker per destination and records whether it is a capture.
   */
  readonly legalDestinations = computed(() => {
    const selected = this.selectedSquare();
    const moves = this.legalMoves();

    const destinations = new Map<string, boolean>();

    if (!this.enabled() || selected === null || moves === null) {
      return destinations;
    }

    for (const move of moves) {
      if (move.from === selected) {
        destinations.set(move.to, move.capture);
      }
    }

    return destinations;
  });

  constructor() {
    effect(() => {
      // A changed position, orientation or interaction state clears selection.
      this.fen();
      this.orientation();
      this.enabled();

      this.selectedSquare.set(null);
    });

    effect(onCleanup => {
      const rejected = this.rejectedMove();

      this.failedMove.set(rejected);

      if (rejected === null) {
        return;
      }

      const timeout = setTimeout(() => {
        this.failedMove.set(null);
      }, 700);

      onCleanup(() => clearTimeout(timeout));
    });
  }

  selectSquare(square: string): void {
    if (!this.enabled() || this.promotionMove() !== null) {
      return;
    }
    this.failedMove.set(null);
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

  selectPromotion(piece: PromotionPiece): void {
    if (!this.promotionEnabled() || this.promotionMenu() === null) {
      return;
    }

    this.promotionChosen.emit(piece);
    this.restoreBoardFocus();
  }

  dismissPromotion(): void {
    if (this.promotionMove() === null) {
      return;
    }

    this.promotionCancelled.emit();
    this.restoreBoardFocus();
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: Event): void {
    if (this.promotionMove() !== null) {
      event.preventDefault();
      event.stopPropagation();
      this.dismissPromotion();
    }
  }

  private restoreBoardFocus(): void {
    // Wait for the popup and its focus trap to be removed.
    requestAnimationFrame(() => {
      this.boardSurface()?.nativeElement.focus();
    });
  }
}