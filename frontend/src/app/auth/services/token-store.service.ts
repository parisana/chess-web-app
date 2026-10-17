import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
// The interceptor will depend on this small store rather than the authentication service that makes HTTP calls.
export class TokenStore {
  private readonly tokenState = signal<string | null>(null);

  readonly token = this.tokenState.asReadonly();

  set(token: string): void {
    this.tokenState.set(token);
  }

  clear(): void {
    this.tokenState.set(null);
  }
}