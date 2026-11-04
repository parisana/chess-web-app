import { DestroyRef, inject, Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AuthSessionEventsService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly changesSubject = new Subject<void>();
  private readonly channel = this.openChannel();

  readonly changes$ = this.changesSubject.asObservable();

  constructor() {
    if (this.channel) {
      this.channel.onmessage = event => {
        const value: unknown = event.data;

        if (
          typeof value === 'object' &&
          value !== null &&
          'type' in value &&
          value.type === 'SESSION_CHANGED'
        ) {
          this.changesSubject.next();
        }
      };
    }

    this.destroyRef.onDestroy(() => {
      this.channel?.close();
      this.changesSubject.complete();
    });
  }

  /**
   * Tell other tabs to recheck the server.
   * This message is not proof of authentication or logout.
   */
  notifyChanged(): void {
    try {
      this.channel?.postMessage({
        type: 'SESSION_CHANGED',
      });
    } catch {
      // A notification failure must not turn a successful HTTP
      // login/logout into a reported authentication failure.
    }
  }

  private openChannel(): BroadcastChannel | null {
    try {
      return typeof BroadcastChannel === 'undefined'
        ? null
        : new BroadcastChannel('chess.auth.session');
    } catch {
      return null;
    }
  }
}