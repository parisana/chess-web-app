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
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthApiService } from '../../services/auth-api.service';

interface FieldError {
  field: string;
  message: string;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './register.html',
  styleUrl: '../auth-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<FieldError[]>([]);

  readonly form = this.formBuilder.group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(30),
      ],
    ],
    email: [
      '',
      [
        Validators.required,
        Validators.email,
        Validators.maxLength(255),
      ],
    ],
    password: [
      '',
      [Validators.required, Validators.minLength(8)],
    ],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }

    this.errorMessage.set(null);
    this.fieldErrors.set([]);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);

    this.authApi.register(this.form.getRawValue()).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.submitting.set(false)),
    ).subscribe({
      next: () => {
        this.form.reset();

        void this.router.navigate(['/login'], {
          queryParams: { registered: 'true' },
        });
      },
      error: (error: unknown) => this.handleError(error),
    });
  }

  private handleError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Registration failed. Please try again.');
      return;
    }

    if (error.status === 0) {
      this.errorMessage.set('Unable to reach the server. Please try again.');
      return;
    }

    const body: unknown = error.error;

    if (typeof body !== 'object' || body === null) {
      this.errorMessage.set('Registration failed. Please try again.');
      return;
    }

    if (error.status === 409 && 'code' in body) {
      if (body.code === 'USERNAME_ALREADY_EXISTS') {
        this.fieldErrors.set([
          { field: 'username', message: 'Username is already taken.' },
        ]);
        return;
      }

      if (body.code === 'EMAIL_ALREADY_EXISTS') {
        this.fieldErrors.set([
          { field: 'email', message: 'Email is already registered.' },
        ]);
        return;
      }
    }

    if (
      error.status === 400 &&
      'errors' in body &&
      Array.isArray(body.errors)
    ) {
      const errors = body.errors.filter(
        (entry: unknown): entry is FieldError =>
          typeof entry === 'object' &&
          entry !== null &&
          'field' in entry &&
          typeof entry.field === 'string' &&
          'message' in entry &&
          typeof entry.message === 'string',
      );

      if (errors.length > 0) {
        this.fieldErrors.set(errors);
        return;
      }
    }

    this.errorMessage.set('Registration failed. Please try again later.');
  }
}