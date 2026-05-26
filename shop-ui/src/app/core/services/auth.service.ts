import { Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private loggedIn = signal(!!localStorage.getItem('token'));
  private emailSignal = signal<string | null>(
    localStorage.getItem('userEmail')
  );
  private roleSignal = signal<string>(
    localStorage.getItem('userRole') ?? 'USER'
  );

  isLoggedIn = this.loggedIn.asReadonly();
  currentEmail = this.emailSignal.asReadonly();
  userInitial = computed(() =>
    this.emailSignal()?.charAt(0)?.toUpperCase() ?? 'U'
  );
  isAdmin = computed(() => this.roleSignal() === 'ADMIN');

  constructor(private router: Router) {}

  login(token: string, email: string, role: string): void {
    localStorage.setItem('token', token);
    localStorage.setItem('userEmail', email);
    localStorage.setItem('userRole', role);
    this.loggedIn.set(true);
    this.emailSignal.set(email);
    this.roleSignal.set(role);
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userRole');
    this.loggedIn.set(false);
    this.emailSignal.set(null);
    this.roleSignal.set('USER');
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }
}
