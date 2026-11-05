import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import { AuthService } from '../../../auth/services/auth.service';
import { ChessBoard } from '../../../game/components/chess-board/chess-board';
import { MatchmakingPanel } from '../../../matchmaking/components/matchmaking-panel/matchmaking-panel';
import { ProfileEditor } from '../../../player/components/profile-editor/profile-editor';
import { PlayerStatistics } from '../../../player/components/player-statistics/player-statistics';

@Component({
  selector: 'app-home',
  imports: [
    ChessBoard,
    MatchmakingPanel,
    ProfileEditor,
    PlayerStatistics,
  ],
  templateUrl: './home.html',
  styleUrl: './home.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  readonly auth = inject(AuthService);

  readonly initialFen =
    'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
}