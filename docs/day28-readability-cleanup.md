# Day 28 — closing readability cleanup

These are source-specific inline instructions for the actual user Day 28 archive. No implementation has been applied. Keep the existing feature behavior and no automatic command retries. User reports green before these proposals; no assistant build or test was run.

## Decision on ng-template

| Choice | Benefit | Cost | Recommendation here |
| --- | --- | --- | --- |
| Inline HTML | State branches and surrounding layout stay together; local narrowing is visible | Deep sections require scrolling | Keep most existing panels inline |
| Local ng-template | Reuses a fragment, or gives one large region a descriptive name without a new component | Adds indirection; file gets slightly longer; dependencies can remain implicit | Keep shared clockRow; one action fragment is optional |
| Child component | Explicit typed inputs/outputs, separate styles and responsibility | More bindings and files; permissions can be duplicated if moved carelessly | Existing MoveHistoryReplay extraction is useful; do not split every small panel |

ng-template declares content; NgTemplateOutlet instantiates it. It does not create a component class, shorten total source, or create a new isolated state boundary. It is not a performance optimization by itself. Readability depends on whether the name/reuse is more useful than the jump to its declaration.

For this checkpoint I recommend the TS cleanup below and retaining the current HTML. The optional action-fragment example shows the safe scope if you prefer a shorter main layout. Home (55 HTML lines), ChessBoard (63) and MoveHistoryReplay (89) have no compelling new fragment to extract merely for length. Keep their existing @if/@for structure.

## 1. Remove duplicate presentation ownership

In frontend/src/app/game/pages/game/game.ts, delete the entire comment beginning “Number moves from the recorded baseline” and its following readonly moveTokens = computed(...) block. Stop immediately before constructor(). The game template no longer reads this field; the active implementation is in MoveHistoryReplay. Keep that child's calculation.

## 2. Name setup responsibilities

Replace the current constructor and ngOnInit blocks in Game with the following code. Leave the following retryLegalMoves method and the remaining class intact. Each new private method contains the existing stream unchanged. The five ngOnInit calls preserve the current subscription order.

initializeLegalMoveHints must still be called synchronously from the constructor: toObservable creates reactive infrastructure that requires an Angular injection context unless an Injector is explicitly supplied. Calling a private method synchronously from the constructor preserves that context; calling the same method later from ngOnInit does not do so automatically. No injector workaround is needed for this mechanical extraction.

```typescript
  constructor() {
    this.initializeLegalMoveHints();
  }

  ngOnInit(): void {
    this.initializeRouteReads();
    this.initializeGameUpdates();
    this.initializeConnectionRecovery();
    this.initializeVisibilityRecovery();
    this.initializePeriodicRecovery();
  }

  private initializeLegalMoveHints(): void {
    const hintScope = computed(() => {
      const current = this.game();
      const revision = this.session.revision();
      const routeRevision = this.hintRouteRevision();

      if (
        current === null ||
        !this.session.isAuthenticated() ||
        current.status !== 'IN_PROGRESS'
      ) {
        return null;
      }

      return {
        gameId: current.id,
        version: current.version,
        revision,
        routeRevision,
      };
    });

    toObservable(hintScope).pipe(
      distinctUntilChanged((previous, current) =>
        previous?.gameId === current?.gameId &&
        previous?.version === current?.version &&
        previous?.revision === current?.revision &&
        previous?.routeRevision === current?.routeRevision
      ),

      switchMap(scope => {
        this.legalMoveHints.set(null);
        this.legalMovesError.set(null);
        this.legalMovesLoading.set(false);

        if (scope === null) {
          return EMPTY;
        }

        const isCurrent = () => {
          const current = this.game();

          return this.session.isAuthenticated() &&
            this.session.revision() === scope.revision &&
            this.hintRouteRevision() === scope.routeRevision &&
            current?.id === scope.gameId &&
            current.version === scope.version &&
            current.status === 'IN_PROGRESS';
        };

        return this.hintRetryRequests.pipe(
          startWith(undefined),

          switchMap(() => defer(() => {
            if (!isCurrent()) {
              return EMPTY;
            }

            this.legalMoveHints.set(null);
            this.legalMovesError.set(null);
            this.legalMovesLoading.set(true);

            return this.gameApi.getLegalMoves(scope.gameId).pipe(
              tap(hints => {
                if (!isCurrent()) {
                  return;
                }

                if (
                  hints.gameId !== scope.gameId ||
                  hints.version !== scope.version
                ) {
                  this.legalMovesError.set(
                    'Move hints do not match the displayed position.',
                  );

                  if (
                    hints.gameId === scope.gameId &&
                    hints.version > scope.version
                  ) {
                    this.requestSynchronization(true);
                  }

                  return;
                }

                this.legalMoveHints.set(hints);
              }),

              catchError(() => {
                if (isCurrent()) {
                  this.legalMovesError.set(
                    'Move hints are unavailable. You can still play.',
                  );
                }

                return EMPTY;
              }),

              finalize(() => {
                this.legalMovesLoading.set(false);
              }),
            );
          })),
        );
      }),

      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private initializeRouteReads(): void {
    this.route.paramMap.pipe(
      switchMap(params => {
        // Navigation establishes a new synchronization scope.
        // switchMap cancels the previous route's read stream first.
        this.activeGameId = null;
        this.latestNotifiedVersion = -1;
        this.readPending = false;

        this.hintRouteRevision.update(value => value + 1);
        this.legalMoveHints.set(null);
        this.rejectedMove.set(null);

        this.replayIndex.set(null);
        this.game.set(null);
        this.clockSnapshot.set(null);
        this.history.set(null);
        this.historyError.set(null);
        this.pendingPromotion.set(null);
        this.pendingLifecycleConfirmation.set(null);
        this.errorMessage.set(null);
        this.moveMessage.set(null);
        this.needsRefresh.set(false);
        this.validGameId.set(false);

        const rawId = params.get('gameId');
        const gameId = rawId === null ? NaN : Number(rawId);

        if (
          rawId === null ||
          !/^[1-9][0-9]*$/.test(rawId) ||
          !Number.isSafeInteger(gameId)
        ) {
          this.errorMessage.set('The game URL is invalid.');
          return EMPTY;
        }

        this.activeGameId = gameId;
        this.validGameId.set(true);

        return this.refreshRequests.pipe(
          startWith(undefined),

          // Only one GET runs for this route at a time.
          // Additional work is remembered by readPending.
          exhaustMap(() => this.readSnapshot(gameId)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private initializeGameUpdates(): void {
    this.realtimeService.updates$.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(update => {
      if (update.gameId !== this.activeGameId) {
        return;
      }

      const current = this.game();

      // Duplicate or delayed notifications need no additional read.
      if (current && update.version <= current.version) {
        return;
      }

      this.latestNotifiedVersion = Math.max(
        this.latestNotifiedVersion,
        update.version,
      );

      this.pendingPromotion.set(null);
      this.requestSynchronization(true);
    });
  }

  private initializeConnectionRecovery(): void {
    this.realtimeService.connections$.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      // Initial connection and reconnect may follow missed notifications.
      this.requestSynchronization(true);
    });
  }

  private initializeVisibilityRecovery(): void {
    merge(
      fromEvent(window, 'focus'),
      fromEvent(document, 'visibilitychange').pipe(
        filter(() => document.visibilityState === 'visible'),
      ),
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.requestSynchronization(true);
    });
  }

  private initializePeriodicRecovery(): void {
    interval(this.realtimeConfig.reconciliationIntervalMs).pipe(
      filter(() => {
        const current = this.game();

        return document.visibilityState === 'visible' &&
          current !== null &&
          (
            current.status === 'IN_PROGRESS' ||
            this.needsRefresh() ||
            this.historyNeedsRefresh()
          );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      // A safety read does not disable the board or cancel promotion.
      // An actual newer snapshot will update them if necessary.
      this.requestSynchronization(false);
    });
  }
```

This improves the entry points rather than reducing the class's line count. Names describe why each stream exists. Keep catchError/finalize/takeUntilDestroyed and cancellation in their current relative positions; moving those operators can change stream lifetimes. Keep injected fields and signal/computed initialization order unchanged in this increment. Use a few responsibility comments around existing fields if useful; do not reorder dependencies just for appearance.

Three short declarations can also become one-liners without changing their bodies:

```typescript
private canPerformLifecycleAction(action: GameLifecycleAction): boolean {
private sendMove(move: ProposedMove, promotion: PromotionPiece | null): void {
private readHistorySnapshot(gameId: number): Observable<GameHistoryResponse | null> {
```

The long executeGameCommand generic signature should remain multiline. In GameRepository, update the stale comment “GameService must reload” to “GameCommandService must reload”. Do not mechanically collapse declarations that become difficult to scan, such as the annotated played-games controller signature.

## 3. Optional single game-actions fragment

Only if you prefer the layout outline to be shorter, import NgTemplateOutlet beside DatePipe and add it to Game's component imports:

```typescript
import { DatePipe, NgTemplateOutlet } from '@angular/common';
```

Inside the existing outer `@if (game(); as current) {` block, declare the fragment near the end, after the closing game-workspace div and before that block's `} @else {`. Cut the **entire** existing `<section class="game-actions" ...>...</section>` there, including all confirmation/offer branches. Wrap that unchanged section:

```html
<ng-template #gameActions>
  <!-- Move the existing complete game-actions section here. -->
</ng-template>
```

At the original section location, retain its surrounding active/finished @if and @else and insert:

```html
@if (current.status === 'IN_PROGRESS') {
  <ng-container [ngTemplateOutlet]="gameActions" />
} @else {
  <app-matchmaking-panel [embedded]="true" searchLabel="Find another match" />
  <a class="text-button" routerLink="/games">Games played</a>
}
```

Both declaration and outlet remain inside the same outer game/current block. Its lexical current variable remains available; do not move the declaration outside that block or add an untyped let-current context. Keep exactly one rendered outlet, the existing section/classes/IDs, and all handlers/disabled conditions. ng-container adds no layout wrapper, so existing CSS continues to target the actual section. This move makes the main branch shorter but adds a declaration/outlet jump; it is optional and not needed to call the milestone complete.

## What to understand and preserve

Game is the page coordinator. Components own bounded presentation/interaction; setup helpers organize that coordinator without introducing another owner for game/session state. A private helper is ordinary synchronous TypeScript, not a lifecycle hook, new RxJS stream or new Angular injection context. Subscription order, teardown and stale-response guards still matter.

Existing user-reported green applies to the supplied archive. Any applied cleanup needs the user's ordinary verification; no verification was executed by the assistant. No additional tests should merely restate method extraction. Relevant existing deep-read tests are listed in day28-ui-review.md. A future larger state-coordination extraction should be chosen in a new milestone when its responsibilities are concrete.

## Framework references

- [Angular 21 template fragments](https://v21.angular.dev/guide/templates/ng-template)
- [Angular RxJS interop](https://angular.dev/ecosystem/rxjs-interop)
