import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { Subject, firstValueFrom, timer } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, take, takeUntil } from 'rxjs/operators';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { PasswordModule } from 'primeng/password';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    CardModule, ButtonModule, InputTextModule,
    ToastModule, DividerModule, PasswordModule
  ],
  providers: [MessageService],
  templateUrl: './register.component.html'
})
export class RegisterComponent implements OnDestroy {
  private destroy$ = new Subject<void>();
  private emailInput$ = new Subject<string>();

  email = '';
  password = '';
  confirmPassword = '';
  loading = false;
  emailError = '';
  passwordError = '';
  emailCheckLoading = false;
  emailTaken = false;

  constructor(
    private http: HttpClient,
    public router: Router,
    private messageService: MessageService
  ) {
    this.emailInput$.pipe(
      debounceTime(600),
      distinctUntilChanged(),
      switchMap(email => {
        if (!email || !email.includes('@')) {
          this.emailTaken = false;
          return [];
        }
        this.emailCheckLoading = true;
        return this.http.get<{ available: boolean }>(
          `/auth/check-email?email=${encodeURIComponent(email)}`
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (res: { available: boolean }) => {
        this.emailTaken = !res.available;
        this.emailCheckLoading = false;
      },
      error: () => {
        this.emailCheckLoading = false;
      }
    });
  }

  onEmailInput(value: string): void {
    this.emailInput$.next(value);
  }

  async register(): Promise<void> {
    this.emailError = '';
    this.passwordError = '';

    if (this.emailTaken) {
      return;
    }
    if (!this.email.trim()) {
      this.emailError = 'Email is required';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.passwordError = 'Passwords do not match';
      return;
    }

    this.loading = true;
    try {
      await firstValueFrom(
        this.http.post('/auth/register', {
          email: this.email,
          password: this.password
        })
      );
      this.messageService.add({
        severity: 'success',
        summary: 'Account created',
        detail: 'Redirecting to login...',
        life: 1500
      });
      timer(1500).pipe(take(1), takeUntil(this.destroy$)).subscribe(
        () => this.router.navigate(['/login'])
      );
    } catch (err: any) {
      this.messageService.add({
        severity: 'error',
        summary: 'Registration failed',
        detail: err?.error?.message || 'Could not create account',
        life: 4000
      });
    } finally {
      this.loading = false;
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
