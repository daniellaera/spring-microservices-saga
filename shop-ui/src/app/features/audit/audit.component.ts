import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { Subject, take, takeUntil } from 'rxjs';
import { AuditService, AuditEventDto } from '../../core/services/audit.service';

const SUCCESS_EVENTS = new Set([
  'USER_LOGIN', 'USER_REGISTER', 'TOKEN_REFRESHED',
  'ORDER_CONFIRMED', 'PAYMENT_SUCCEEDED',
  'PRODUCT_CREATED', 'PRODUCT_RESTOCKED', 'STOCK_RESTORED',
  'CART_ITEM_ADDED', 'CART_CHECKOUT'
]);

const FAILED_EVENTS = new Set([
  'USER_LOGIN_FAILED', 'ORDER_CANCELLED', 'PAYMENT_FAILED'
]);

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    CommonModule, DatePipe, TableModule, TagModule, CardModule,
    ButtonModule, InputTextModule, ProgressSpinnerModule
  ],
  templateUrl: './audit.component.html'
})
export class AuditComponent implements OnInit, OnDestroy {
  private auditService = inject(AuditService);
  private destroy$ = new Subject<void>();

  events: AuditEventDto[] = [];
  loading = signal(false);
  totalRecords = 0;
  expandedRows: Record<number, boolean> = {};

  ngOnInit(): void {
    this.loadEvents();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadEvents(): void {
    this.loading.set(true);
    this.auditService.getAll(0, 200)
      .pipe(take(1), takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.events = res.content ?? [];
          this.totalRecords = res.totalElements ?? 0;
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      });
  }

  toggleRow(event: AuditEventDto): void {
    if (this.expandedRows[event.id]) {
      delete this.expandedRows[event.id];
    } else {
      this.expandedRows[event.id] = true;
    }
  }

  severityFor(eventType: string): 'success' | 'danger' | 'info' {
    if (SUCCESS_EVENTS.has(eventType)) return 'success';
    if (FAILED_EVENTS.has(eventType)) return 'danger';
    return 'info';
  }

  formatPayload(payload: string): string {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  }
}
