import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { PasswordModule } from 'primeng/password';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    CardModule, ButtonModule, InputTextModule,
    ToastModule, DividerModule, PasswordModule
  ],
  providers: [MessageService],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  emailError = '';

  constructor(
    private http: HttpClient,
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
      const res = await firstValueFrom(
        this.http.post<{ token: string }>('/auth/login', {
          email: this.email,
          password: this.password
        })
      );
      const payload = JSON.parse(atob(res.token.split('.')[1]));
      const role = payload.role ?? payload.authorities?.[0] ?? 'USER';
      this.authService.login(res.token, this.email, role);
      const returnUrl =
        this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
      this.router.navigateByUrl(decodeURIComponent(returnUrl));
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
}
