import {
  GameResult,
  GameStatus,
  GameTerminationReason,
} from './game-response';

export type PromotionPiece = 'QUEEN' | 'ROOK' | 'BISHOP' | 'KNIGHT';

export interface ProposedMove {
  from: string;
  to: string;
}

export interface SubmitMoveRequest extends ProposedMove {
  expectedVersion: number;
  promotion: PromotionPiece | null;
}

export interface GameMoveResult {
  gameId: number;
  currentFen: string;
  version: number;
  positionStatus: 'NORMAL' | 'CHECK' | 'CHECKMATE' | 'STALEMATE';
  status: GameStatus;
  result: GameResult | null;
  terminationReason: GameTerminationReason | null;
  finishedAt: string | null;
}