export type QueueStatusResponse =
  | {
    status: 'NOT_QUEUED';
    enqueuedAt: null;
  }
  | {
    status: 'WAITING';
    enqueuedAt: string;
  };