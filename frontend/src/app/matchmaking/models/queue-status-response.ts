export type QueueStatusResponse =
  | {
    status: 'NOT_QUEUED';
    enqueuedAt: null;
    gameId: null;
  }
  | {
    status: 'WAITING';
    enqueuedAt: string;
    gameId: null;
  }
  | {
    status: 'MATCHED';
    enqueuedAt: null;
    gameId: number;
  };