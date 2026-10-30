import { PromotionPiece } from './game-move';

export interface GameMoveResponse {
  sequenceNumber: number;
  playerId: number;
  from: string;
  to: string;
  promotion: PromotionPiece | null;
  resultingFen: string;
  playedAt: string;
}

export interface GameHistoryResponse {
  gameId: number;
  version: number;
  historyStartFen: string;
  historyComplete: boolean;
  recordedMoveCount: number;
  moves: readonly GameMoveResponse[];
}