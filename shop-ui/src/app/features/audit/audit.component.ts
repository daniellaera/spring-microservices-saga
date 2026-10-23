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

// someone has to act on these: a refund to make by hand, a result to replay, an order event never sent
const FAILED_EVENTS = new Set([
  'USER_LOGIN_FAILED', 'ORDER_CANCELLED', 'PAYMENT_FAILED',
  'PAYMENT_REFUND_FAILED', 'PAYMENT_DEAD_LETTERED', 'OUTBOX_EVENT_FAILED'
]);

// handled, but worth a look: an order failed after its payment was captured, and the money went back
const WARN_EVENTS = new Set([
  'PAYMENT_REFUNDED'
]);

export type AuditSeverity = 'success' | 'warn' | 'danger' | 'info';

export function auditSeverity(eventType: string): AuditSeverity {
  if (SUCCESS_EVENTS.has(eventType)) return 'success';
  if (FAILED_EVENTS.has(eventType)) return 'danger';
  if (WARN_EVENTS.has(eventType)) return 'warn';
  return 'info';
}

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

  severityFor(eventType: string): AuditSeverity {
    return auditSeverity(eventType);
  }

  formatPayload(payload: string): string {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  }
}
