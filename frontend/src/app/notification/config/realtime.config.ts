import { InjectionToken } from '@angular/core';

export interface RealtimeConfig {
  endpoint: string;
  subscriptionDestination: string;
  reconnectDelayMs: number;
  connectionTimeoutMs: number;
  heartbeatIntervalMs: number;
  reconciliationIntervalMs: number;
}

export const REALTIME_CONFIG = new InjectionToken<RealtimeConfig>(
  'REALTIME_CONFIG',
  {
    providedIn: 'root',
    factory: () => ({
      // Keep these addresses aligned with app.websocket in backend YAML.
      endpoint: '/api/v1/ws',
      subscriptionDestination: '/user/queue/updates',
      reconnectDelayMs: 5_000,
      connectionTimeoutMs: 10_000,
      heartbeatIntervalMs: 10_000,
      reconciliationIntervalMs: 30_000,
    }),
  },
);