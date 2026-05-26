import { Component, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule, RouterModule,
    ToolbarModule, ButtonModule,
    AvatarModule, MenuModule
  ],
  templateUrl: './navbar.component.html'
})
export class NavbarComponent implements OnInit {
  private authService = inject(AuthService);

  menuItems: MenuItem[] = [];

  isLoggedIn = this.authService.isLoggedIn;
  userInitial = this.authService.userInitial;
  currentEmail = this.authService.currentEmail;

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
        { separator: true },
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
