import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthSessionStore } from '../../../auth/services/auth-session.store';
import { PlayerApiService } from '../../services/player-api.service';

@Component({
  selector: 'app-profile-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  template: `
    <mat-card [attr.aria-busy]="saving()">
      <mat-card-header>
        <mat-card-title>Your profile</mat-card-title>
      </mat-card-header>

      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="save()">
          <mat-form-field appearance="outline">
            <mat-label>Username</mat-label>
            <input
              matInput
              formControlName="username"
              autocomplete="username"
              [readonly]="saving()"
            />

            @if (form.controls.username.hasError('required')) {
              <mat-error>Username is required.</mat-error>
            } @else if (
              form.controls.username.hasError('minlength') ||
              form.controls.username.hasError('maxlength')
            ) {
              <mat-error>Use 3–30 characters.</mat-error>
            } @else if (form.controls.username.hasError('pattern')) {
              <mat-error>Username cannot contain only whitespace.</mat-error>
            }
          </mat-form-field>

          @if (errorMessage(); as message) {
            <p role="alert">{{ message }}</p>
          }

          @if (saved()) {
            <p role="status">Profile saved.</p>
          }

          <button
            mat-flat-button
            type="submit"
            [disabled]="saving() || form.pristine"
          >
            Save username
          </button>
        </form>
      </mat-card-content>

      @if (saving()) {
        <mat-progress-bar
          mode="indeterminate"
          aria-label="Saving profile"
        />
      }
    </mat-card>
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 1.5rem;
    }

    mat-card-content {
      padding-top: 1rem;
    }

    form {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.5rem;
    }

    mat-form-field {
      width: 100%;
    }

    [role="alert"] {
      color: var(--mat-sys-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileEditor {
  private readonly session = inject(AuthSessionStore);
  private readonly playerApi = inject(PlayerApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.group({
    username: [
      this.session.currentPlayer()?.username ?? '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(30),
        Validators.pattern(/\S/),
      ],
    ],
  });

  constructor() {
    this.form.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.saved.set(false);
      this.errorMessage.set(null);
    });
  }

  save(): void {
    if (this.saving()) {
      return;
    }

    this.saved.set(false);
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const token = this.session.token();
    const playerId = this.session.currentPlayer()?.id;

    this.saving.set(true);

    this.playerApi.updateUsername(
      this.form.controls.username.value,
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.saving.set(false)),
    ).subscribe({
      next: player => {
        // Ignore a response belonging to a session that has since changed.
        if (
          this.session.token() !== token ||
          this.session.currentPlayer()?.id !== playerId
        ) {
          return;
        }

        // Use the server response as the authoritative profile.
        this.session.setPlayer(player);
        this.form.reset({ username: player.username });
        this.saved.set(true);
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.describeError(error));
      },
    });
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'Unable to reach the server. Please try again.';
      }

      if (error.status === 401) {
        return 'Your session is no longer valid. Please sign in again.';
      }

      if (
        error.status === 409 &&
        error.error?.code === 'USERNAME_ALREADY_EXISTS'
      ) {
        return 'That username is already taken.';
      }

      if (
        error.status === 400 &&
        error.error?.code === 'VALIDATION_FAILED'
      ) {
        return 'Enter a nonblank username of 3–30 characters.';
      }
    }

    return 'Unable to save your profile. Please try again.';
  }
}