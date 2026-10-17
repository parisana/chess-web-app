import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../../auth/services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  template: `
    <main>
      @if (auth.currentPlayer(); as player) {
        <h1>Welcome, {{ player.username }}</h1>
        <p>{{ player.email }}</p>
        <button type="button" (click)="logout()">Log out</button>
      }
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/login', {
      replaceUrl: true,
    });
  }
}