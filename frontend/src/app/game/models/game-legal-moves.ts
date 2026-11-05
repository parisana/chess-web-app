import { PromotionPiece, ProposedMove } from './game-move';

export interface LegalMoveHint {
  from: string;
  to: string;
  promotion: PromotionPiece | null;
  capture: boolean;
}

export interface GameLegalMovesResponse {
  gameId: number;
  version: number;
  moves: readonly LegalMoveHint[];
}

export interface RejectedMoveFeedback extends ProposedMove {
  gameId: number;
  version: number;
}