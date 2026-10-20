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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './login.html',
  styleUrl: '../auth-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  readonly sessionExpired =
    inject(ActivatedRoute).snapshot.queryParamMap.get('sessionExpired') === 'true';
  readonly registrationComplete =
    inject(ActivatedRoute).snapshot.queryParamMap.get('registered') === 'true';
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.group({
    email: [
      '',
      [
        Validators.required,
        Validators.email,
        Validators.maxLength(255),
      ],
    ],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }

    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);

    this.auth.login(this.form.getRawValue()).pipe(
      takeUntilDestroyed(this.destroyRef), // automatically cancels the HTTP subscription if the component or service is destroyed while the login request is still running.
      finalize(() => this.submitting.set(false)),
    ).subscribe({
      next: () => {
        this.form.controls.password.reset();
        void this.router.navigateByUrl('/home');
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.describeError(error));
      },
    });
  }

  private describeError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Something went wrong. Please try again.';
    }

    if (error.status === 0) {
      return 'Unable to reach the server. Please try again.';
    }

    if (error.status === 401) {
      return 'Unable to sign in. Check your email and password.';
    }

    if (error.status === 400) {
      return 'Check your email and password. Passwords must not exceed 72 UTF-8 bytes.';
    }

    return 'Sign-in failed. Please try again later.';
  }
}