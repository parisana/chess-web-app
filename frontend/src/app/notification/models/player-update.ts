export interface PlayerUpdate {
  type: 'GAME_CREATED' | 'GAME_UPDATED';
  gameId: number;
  version: number;
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

  return {
    type: update['type'],
    gameId,
    version,
  };
}