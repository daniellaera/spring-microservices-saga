import { Injectable, signal, computed } from '@angular/core';

export interface AppNotification {
  id: number;
  type: 'success' | 'error' | 'info';
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
  orderId?: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private static readonly MAX_NOTIFICATIONS = 20;

  private notificationList = signal<AppNotification[]>([]);
  private nextId = 1;

  notifications = this.notificationList.asReadonly();

  unreadCount = computed(() =>
    this.notificationList().filter(n => !n.read).length
  );

  add(notification: Omit<AppNotification, 'id' | 'timestamp' | 'read'>): void {
    const newNotif: AppNotification = {
      ...notification,
      id: this.nextId++,
      timestamp: new Date(),
      read: false
    };
    this.notificationList.update(list =>
      [newNotif, ...list].slice(0, NotificationService.MAX_NOTIFICATIONS)
    );
  }

  markAllAsRead(): void {
    this.notificationList.update(list =>
      list.map(n => ({ ...n, read: true }))
    );
  }

  markAsRead(id: number): void {
    this.notificationList.update(list =>
      list.map(n => n.id === id ? { ...n, read: true } : n)
    );
  }

  clear(): void {
    this.notificationList.set([]);
  }

  remove(id: number): void {
    this.notificationList.update(list =>
      list.filter(n => n.id !== id)
    );
  }
}
