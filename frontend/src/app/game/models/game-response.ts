export type GameStatus = 'IN_PROGRESS' | 'FINISHED';

export type GameResult = 'WHITE_WIN' | 'BLACK_WIN' | 'DRAW';

export type GameTerminationReason = 'CHECKMATE' | 'STALEMATE' | 'RESIGNATION' | 'DRAW_AGREEMENT';

export interface GameResponse {
  id: number;
  whitePlayerId: number;
  blackPlayerId: number;
  status: GameStatus;
  currentFen: string;
  createdAt: string;
  version: number;
  result: GameResult | null;
  terminationReason: GameTerminationReason | null;
  finishedAt: string | null;
  drawOfferedByPlayerId: number | null;
}