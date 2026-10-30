import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { RealtimeService } from './notification/services/realtime.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('chess-frontend');

  // Keep one connection lifecycle for the application,
  // independently of Home/Game navigation.
  protected readonly realtime = inject(RealtimeService);
}
