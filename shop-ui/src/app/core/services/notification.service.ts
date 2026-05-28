import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  count = signal(0);

  add(): void {
    this.count.update(c => c + 1);
  }

  clear(): void {
    this.count.set(0);
  }
}
