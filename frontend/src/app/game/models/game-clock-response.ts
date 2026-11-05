export interface GameClockResponse {
  whiteRemainingMillis: number;
  blackRemainingMillis: number;
  runningColor: 'WHITE' | 'BLACK' | null;
  calculatedAt: string; // Timestamp when the clock snapshot was captured on the server.
}