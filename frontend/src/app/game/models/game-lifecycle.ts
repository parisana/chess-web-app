export type GameLifecycleAction =
  | 'resign'
  | 'offer-draw'
  | 'accept-draw'
  | 'decline-draw'
  | 'withdraw-draw';

export interface GameLifecycleRequest {
  expectedVersion: number;
}