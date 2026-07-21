import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { PasswordModule } from 'primeng/password';
import { InputOtpModule } from 'primeng/inputotp';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    CardModule, ButtonModule, InputTextModule,
    ToastModule, DividerModule, PasswordModule, InputOtpModule
  ],
  providers: [MessageService],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  emailError = '';

  showOtpStep = false;
  pendingEmail = '';
  otpCode = '';
  otpLoading = false;
  otpError = '';

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private messageService: MessageService
  ) {}

  async signIn(): Promise<void> {
    if (!this.email.trim()) {
      this.emailError = 'Email is required';
      return;
    }
    this.emailError = '';
    this.loading = true;
    try {
      const response = await firstValueFrom(
        this.authService.initiateLogin(this.email, this.password)
      );
      if (response.requiresOtp) {
        this.pendingEmail = response.email;
        this.showOtpStep = true;
      }
    } catch (err: any) {
      this.messageService.add({
        severity: 'error',
        summary: 'Login failed',
        detail: err?.error?.message || 'Invalid credentials',
        life: 4000
      });
    } finally {
      this.loading = false;
    }
  }

  async verifyOtp(): Promise<void> {
    this.otpLoading = true;
    this.otpError = '';
    try {
      await firstValueFrom(this.authService.verifyOtp(this.pendingEmail, this.otpCode));
      const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
      this.router.navigateByUrl(decodeURIComponent(returnUrl));
    } catch (err: any) {
      this.otpError = err?.error?.message || 'Invalid or expired code';
    } finally {
      this.otpLoading = false;
    }
  }

  backToLogin(): void {
    this.showOtpStep = false;
    this.otpCode = '';
    this.otpError = '';
  }
}
