import { Component, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { MenuModule } from 'primeng/menu';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MenuItem } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { CartService } from '../../core/services/cart.service';
import { ProfileService } from '../../core/services/profile.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule,
    ToolbarModule, ButtonModule,
    AvatarModule, MenuModule,
    DialogModule, InputTextModule
  ],
  templateUrl: './navbar.component.html'
})
export class NavbarComponent implements OnInit {
  authService = inject(AuthService);
  private notificationService = inject(NotificationService);
  private cartService = inject(CartService);
  private profileService = inject(ProfileService);

  menuItems: MenuItem[] = [];

  isLoggedIn = this.authService.isLoggedIn;
  isAdmin = this.authService.isAdmin;
  userInitial = this.authService.userInitial;
  currentEmail = this.authService.currentEmail;
  notificationCount = this.notificationService.count;
  sessionTime = this.authService.sessionTimeRemaining;
  sessionTimeLow = this.authService.sessionTimeLow;
  cartCount = this.cartService.itemCount;

  showProfileDialog = false;
  profileFirstName = '';
  profileLastName = '';
  savingProfile = false;

  version = environment.version;
  buildNumber = environment.buildNumber;

  clearNotifications(): void {
    this.notificationService.clear();
  }

  toggleCart(): void {
    this.cartService.toggleCart();
  }

  openProfile(): void {
    const p = this.profileService.profile();
    if (!p) {
      this.profileService.getProfile().subscribe(profile => {
        this.profileFirstName = profile.firstName || '';
        this.profileLastName = profile.lastName || '';
        this.showProfileDialog = true;
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
    }).subscribe({
      next: (profile) => {
        this.savingProfile = false;
        this.showProfileDialog = false;
        if (profile.displayName) {
          this.authService.displayName.set(profile.displayName);
        }
      },
      error: () => {
        this.savingProfile = false;
      }
    });
  }

  constructor() {
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
}
