import { BoardColor } from './fen-display';
import { GameTerminationReason } from './game-response';

export type PlayedGameOutcome = 'WIN' | 'LOSS' | 'DRAW';

export interface PlayedGameResponse {
  gameId: number;
  playerColor: BoardColor;
  opponentPlayerId: number;
  opponentUsernameAtStart: string | null;
  opponentRatingAtStart: number | null;
  outcome: PlayedGameOutcome;
  terminationReason: GameTerminationReason;
  finishedAt: string;
}

export interface PlayedGamesResponse {
  games: readonly PlayedGameResponse[];
  page: number;
  size: number;
  hasNext: boolean;
}