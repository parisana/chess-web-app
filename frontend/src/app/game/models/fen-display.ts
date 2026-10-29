export type BoardColor = 'WHITE' | 'BLACK';

export interface DisplayPiece {
  color: BoardColor;
  name: string;
  glyph: string;
}

export interface DisplayPosition {
  pieces: ReadonlyMap<string, DisplayPiece>;
  sideToMove: BoardColor;
}

const PIECES: Readonly<Record<string, DisplayPiece>> = {
  K: { color: 'WHITE', name: 'king', glyph: '♔' },
  Q: { color: 'WHITE', name: 'queen', glyph: '♕' },
  R: { color: 'WHITE', name: 'rook', glyph: '♖' },
  B: { color: 'WHITE', name: 'bishop', glyph: '♗' },
  N: { color: 'WHITE', name: 'knight', glyph: '♘' },
  P: { color: 'WHITE', name: 'pawn', glyph: '♙' },
  k: { color: 'BLACK', name: 'king', glyph: '♚' },
  q: { color: 'BLACK', name: 'queen', glyph: '♛' },
  r: { color: 'BLACK', name: 'rook', glyph: '♜' },
  b: { color: 'BLACK', name: 'bishop', glyph: '♝' },
  n: { color: 'BLACK', name: 'knight', glyph: '♞' },
  p: { color: 'BLACK', name: 'pawn', glyph: '♟' },
};

const FILES = 'abcdefgh';

/**
 * Read piece placement and active color for display.
 * Remaining FEN fields and chess legality belong to the backend.
 */
export function parseFenForDisplay(fen: string): DisplayPosition {
  const fields = fen.trim().split(/\s+/);

  if (fields.length !== 6) {
    throw new Error('Expected six FEN fields.');
  }

  const placement = fields[0];
  const activeColor = fields[1];

  if (activeColor !== 'w' && activeColor !== 'b') {
    throw new Error('Invalid FEN active color.');
  }

  const ranks = placement.split('/');

  if (ranks.length !== 8) {
    throw new Error('Expected eight FEN ranks.');
  }

  const pieces = new Map<string, DisplayPiece>();

  ranks.forEach((rank, rankIndex) => {
    let fileIndex = 0;

    for (const character of rank) {
      if (/^[1-8]$/.test(character)) {
        fileIndex += Number(character);
      } else {
        const piece = PIECES[character];

        if (!piece || fileIndex >= 8) {
          throw new Error('Invalid FEN piece placement.');
        }

        const square = `${FILES[fileIndex]}${8 - rankIndex}`;
        pieces.set(square, piece);
        fileIndex++;
      }

      if (fileIndex > 8) {
        throw new Error('FEN rank exceeds eight squares.');
      }
    }

    if (fileIndex !== 8) {
      throw new Error('FEN rank must contain eight squares.');
    }
  });

  return {
    pieces,
    sideToMove: activeColor === 'w' ? 'WHITE' : 'BLACK',
  };
}