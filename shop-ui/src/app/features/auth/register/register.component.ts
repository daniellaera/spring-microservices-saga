import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
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
    CommonModule, FormsModule,
    CardModule, ButtonModule, InputTextModule,
    ToastModule, DividerModule, PasswordModule
  ],
  providers: [MessageService],
  templateUrl: './register.component.html'
})
export class RegisterComponent {
  email = '';
  password = '';
  confirmPassword = '';
  loading = false;
  emailError = '';
  passwordError = '';

  constructor(
    private http: HttpClient,
    public router: Router,
    private messageService: MessageService
  ) {}

  async register(): Promise<void> {
    this.emailError = '';
    this.passwordError = '';

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
        detail: 'You can now sign in',
        life: 3000
      });
      setTimeout(() => this.router.navigate(['/login']), 1500);
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
}
