import { GameResponse } from "../../game/models/game-response";

export interface PlayerUpdate {
  type: 'GAME_CREATED' | 'GAME_UPDATED';
  gameId: number;
  version: number;
  snapshot?: GameResponse;
}

/**
 * Check incoming transport data before exposing it to application code.
 */
export function parsePlayerUpdate(body: string): PlayerUpdate | null {
  let value: unknown;

  try {
    value = JSON.parse(body);
  } catch {
    return null;
  }

  if (typeof value !== 'object' || value === null) {
    return null;
  }

  const update = value as Record<string, unknown>;

  if (
    update['type'] !== 'GAME_CREATED' &&
    update['type'] !== 'GAME_UPDATED'
  ) {
    return null;
  }

  const gameId = update['gameId'];
  const version = update['version'];

  if (
    typeof gameId !== 'number' ||
    !Number.isSafeInteger(gameId) ||
    gameId <= 0 ||
    typeof version !== 'number' ||
    !Number.isSafeInteger(version) ||
    version < 0
  ) {
    return null;
  }
  const snapshot = update['snapshot'];
  return {
    type: update['type'],
    gameId,
    version,
    snapshot: update['type'] === 'GAME_UPDATED' &&
      isGameSnapshot(snapshot) &&
      snapshot.id === gameId &&
      snapshot.version === version
      ? snapshot
      : undefined,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value);
}

function isPlayerId(value: unknown): value is number {
  return isInteger(value) && value > 0;
}

function isTimestamp(value: unknown): value is string {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}

function isGameSnapshot(value: unknown): value is GameResponse {
  if (!isRecord(value)) {
    return false;
  }

  const clock = value['clock'];
  const status = value['status'];
  const result = value['result'];
  const reason = value['terminationReason'];
  const finishedAt = value['finishedAt'];
  const drawOffer = value['drawOfferedByPlayerId'];

  const validStatus = status === 'IN_PROGRESS'
    ? result === null && reason === null && finishedAt === null
    : status === 'FINISHED' &&
    (result === 'WHITE_WIN' || result === 'BLACK_WIN' || result === 'DRAW') &&
    (reason === 'CHECKMATE' || reason === 'STALEMATE' ||
      reason === 'RESIGNATION' || reason === 'DRAW_AGREEMENT' ||
      reason === 'TIMEOUT') &&
    isTimestamp(finishedAt);

  return isPlayerId(value['id']) &&
    isPlayerId(value['whitePlayerId']) &&
    isPlayerId(value['blackPlayerId']) &&
    isInteger(value['version']) && value['version'] >= 0 &&
    typeof value['currentFen'] === 'string' && value['currentFen'].trim() !== '' &&
    isTimestamp(value['createdAt']) &&
    validStatus &&
    (drawOffer === null || isPlayerId(drawOffer)) &&
    (clock === null || (
      isRecord(clock) &&
      isInteger(clock['whiteRemainingMillis']) && clock['whiteRemainingMillis'] >= 0 &&
      isInteger(clock['blackRemainingMillis']) && clock['blackRemainingMillis'] >= 0 &&
      (status === 'FINISHED'
        ? clock['runningColor'] === null
        : clock['runningColor'] === 'WHITE' || clock['runningColor'] === 'BLACK') &&
      isTimestamp(clock['calculatedAt'])
    )) &&
    (value['whiteUsernameAtStart'] === null || typeof value['whiteUsernameAtStart'] === 'string') &&
    (value['blackUsernameAtStart'] === null || typeof value['blackUsernameAtStart'] === 'string') &&
    (value['whiteRatingAtStart'] === null || isInteger(value['whiteRatingAtStart'])) &&
    (value['blackRatingAtStart'] === null || isInteger(value['blackRatingAtStart']));
}