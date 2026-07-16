import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { take } from 'rxjs/operators';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { MenuModule } from 'primeng/menu';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PopoverModule } from 'primeng/popover';
import { MenuItem, MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { CartService } from '../../core/services/cart.service';
import { ProfileService } from '../../core/services/profile.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule, DatePipe, RouterModule, FormsModule,
    ToolbarModule, ButtonModule,
    AvatarModule, MenuModule,
    DialogModule, InputTextModule, ToastModule,
    PopoverModule
  ],
  providers: [MessageService],
  templateUrl: './navbar.component.html'
})
export class NavbarComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  authService = inject(AuthService);
  private notificationService = inject(NotificationService);
  private cartService = inject(CartService);
  private profileService = inject(ProfileService);
  private messageService = inject(MessageService);

  menuItems: MenuItem[] = [];

  isLoggedIn = this.authService.isLoggedIn;
  isAdmin = this.authService.isAdmin;
  userInitial = this.authService.userInitial;
  currentEmail = this.authService.currentEmail;
  notifications = this.notificationService.notifications;
  unreadCount = this.notificationService.unreadCount;
  sessionTime = this.authService.sessionTimeRemaining;
  sessionTimeLow = this.authService.sessionTimeLow;
  cartCount = this.cartService.itemCount;

  showProfileDialog = false;
  profileFirstName = '';
  profileLastName = '';
  savingProfile = false;

  version = environment.version;
  buildNumber = environment.buildNumber;

  isDarkMode = signal(localStorage.getItem('theme') === 'dark');

  toggleTheme(): void {
    const dark = !this.isDarkMode();
    this.isDarkMode.set(dark);
    localStorage.setItem('theme', dark ? 'dark' : 'light');

    const element = document.querySelector('html');
    if (dark) {
      element?.classList.add('my-app-dark');
    } else {
      element?.classList.remove('my-app-dark');
    }
  }

  // call on init to restore saved preference
  initTheme(): void {
    const saved = localStorage.getItem('theme');
    if (saved === 'dark') {
      document.querySelector('html')?.classList.add('my-app-dark');
      this.isDarkMode.set(true);
    }
  }

  onBellClick(event: Event, op: any): void {
    this.notificationService.markAllAsRead();
    op.toggle(event);
  }

  removeNotification(id: number): void {
    this.notificationService.remove(id);
  }

  clearAll(): void {
    this.notificationService.clear();
  }

  toggleCart(): void {
    this.cartService.toggleCart();
  }

  openProfile(): void {
    const p = this.profileService.profile();
    if (!p) {
      this.profileService.getProfile().pipe(take(1)).subscribe({
        next: profile => {
          this.profileFirstName = profile.firstName || '';
          this.profileLastName = profile.lastName || '';
          this.showProfileDialog = true;
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Failed',
            detail: 'Could not load profile',
            life: 3000
          });
        }
      });
    } else {
      this.profileFirstName = p.firstName || '';
      this.profileLastName = p.lastName || '';
      this.showProfileDialog = true;
    }
  }

  saveProfile(): void {
    this.savingProfile = true;
    this.profileService.updateProfile({
      firstName: this.profileFirstName,
      lastName: this.profileLastName
    }).pipe(take(1)).subscribe({
      next: (profile) => {
        this.savingProfile = false;
        this.showProfileDialog = false;
        if (profile.displayName) {
          this.authService.displayName.set(profile.displayName);
        }
      },
      error: () => {
        this.savingProfile = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Failed',
          detail: 'Could not update profile',
          life: 3000
        });
      }
    });
  }

  constructor() {
    this.initTheme();

    effect(() => {
      this.menuItems = [
        {
          label: this.currentEmail() ?? 'Account',
          disabled: true
        },
        { separator: true },
        {
          label: 'Dashboard',
          icon: 'pi pi-home',
          routerLink: '/dashboard'
        },
        {
          label: 'My Orders',
          icon: 'pi pi-list',
          routerLink: '/dashboard'
        },
        ...(this.isAdmin() ? [{
          label: 'Manage Products',
          icon: 'pi pi-box',
          routerLink: '/products'
        }, {
          label: 'Audit Trail',
          icon: 'pi pi-shield',
          routerLink: '/audit'
        }] : []),
        { separator: true },
        {
          label: 'Profile',
          icon: 'pi pi-user-edit',
          command: () => this.openProfile()
        },
        {
          label: 'Sign out',
          icon: 'pi pi-sign-out',
          command: () => this.authService.logout()
        }
      ];
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
