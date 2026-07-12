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
  displayName = signal<string>('');
  userInitial = computed(() => {
    const name = this.displayName();
    if (name) return name.charAt(0).toUpperCase();
    return this.emailSignal()?.charAt(0)?.toUpperCase() ?? 'U';
  });
  isAdmin = computed(() => this.roleSignal() === 'ADMIN');

  sessionTimeRemaining = signal('');
  sessionTimeLow = signal(false);
  private sessionInterval: ReturnType<typeof setInterval> | null = null;

  constructor(private router: Router) {
    if (this.loggedIn() && this.isTokenExpired()) {
      this.logout();
    } else if (this.loggedIn()) {
      this.startSessionTimer();
      this.displayName.set(this.emailSignal()?.split('@')[0] ?? '');
    }
  }

  isTokenExpired(): boolean {
    const token = localStorage.getItem('token');
    if (!token) return true;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (!payload.exp) return false;
      return Date.now() >= payload.exp * 1000;
    } catch {
      return true;
    }
  }

  getTokenExpirationTime(): number | null {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (!payload.exp) return null;
      return payload.exp * 1000;
    } catch {
      return null;
    }
  }

  getRemainingTime(): number {
    const exp = this.getTokenExpirationTime();
    if (!exp) return 0;
    return Math.max(0, exp - Date.now());
  }

  startSessionTimer(): void {
    this.stopSessionTimer();
    this.updateSessionTime();
    this.sessionInterval = setInterval(() => {
      this.updateSessionTime();
    }, 1000);
  }

  stopSessionTimer(): void {
    if (this.sessionInterval) {
      clearInterval(this.sessionInterval);
      this.sessionInterval = null;
    }
  }

  private updateSessionTime(): void {
    const remaining = this.getRemainingTime();
    if (remaining <= 0) {
      this.sessionTimeRemaining.set('Expired');
      this.sessionTimeLow.set(true);
      this.stopSessionTimer();
      this.logout();
      return;
    }

    const totalSeconds = Math.floor(remaining / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    this.sessionTimeLow.set(totalSeconds < 300);

    if (hours > 0) {
      this.sessionTimeRemaining.set(
        `${hours}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s`
      );
    } else {
      this.sessionTimeRemaining.set(
        `${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s`
      );
    }
  }

  login(token: string, email: string, role: string): void {
    localStorage.setItem('token', token);
    localStorage.setItem('userEmail', email);
    localStorage.setItem('userRole', role);
    this.loggedIn.set(true);
    this.emailSignal.set(email);
    this.roleSignal.set(role);
    this.displayName.set(email.split('@')[0] ?? '');
    this.startSessionTimer();
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userRole');
    this.loggedIn.set(false);
    this.emailSignal.set(null);
    this.roleSignal.set('USER');
    this.displayName.set('');
    this.stopSessionTimer();
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }
}
