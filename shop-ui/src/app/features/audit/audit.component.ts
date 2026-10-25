import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs';
import { AuditEventDto, AuditQuery, AuditService } from '../../core/services/audit.service';
import { AuditSeverity, auditSeverity, eventInstant, orderIdOf } from './audit-events';

// The values of the event type and service filters: every type audit-trail-service stores, and every service
export const EVENT_TYPES = [
  'ORDER_CREATED', 'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'OUTBOX_EVENT_FAILED', 'ORDER_DEAD_LETTERED',
  'STOCK_DEDUCTED', 'STOCK_RESTORED', 'STOCK_RESERVATION_DEAD_LETTERED', 'STOCK_RESTORE_DEAD_LETTERED',
  'PRODUCT_CREATED', 'PRODUCT_RESTOCKED',
  'PAYMENT_INITIATED', 'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'PAYMENT_REFUNDED', 'PAYMENT_REFUND_FAILED',
  'PAYMENT_DEAD_LETTERED',
  'USER_LOGIN', 'USER_LOGIN_INITIATED', 'USER_LOGIN_FAILED', 'USER_LOGOUT', 'USER_REGISTER',
  'USER_PROFILE_UPDATED', 'TOKEN_REFRESHED',
  'CART_ITEM_ADDED', 'CART_CLEARED', 'CART_CHECKOUT'
];
export const SERVICES = ['order-service', 'inventory-service', 'payment-service', 'auth-service', 'cart-service'];

const SEARCH_DEBOUNCE_MS = 300;

/** The audit trail, paginated and filtered by audit-trail-service: every change of page or filter is a request. */
@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    CommonModule, DatePipe, FormsModule, RouterLink, TableModule, TagModule, CardModule,
    ButtonModule, InputTextModule, SelectModule
  ],
  templateUrl: './audit.component.html'
})
export class AuditComponent implements OnInit, OnDestroy {
  private auditService = inject(AuditService);
  private destroy$ = new Subject<void>();
  private queries$ = new Subject<AuditQuery>();
  private searchInput$ = new Subject<string>();

  readonly eventTypes = EVENT_TYPES;
  readonly services = SERVICES;

  events: AuditEventDto[] = [];
  loading = signal(true);
  failed = signal(false);
  totalRecords = 0;
  first = 0;
  rows = 20;

  search = '';
  eventType: string | null = null;
  serviceName: string | null = null;

  ngOnInit(): void {
    // The latest query wins: an answer to an older one is dropped. A failed request is caught inside the switchMap,
    // so the next page, filter or Refresh still asks again.
    this.queries$.pipe(
      tap(() => this.loading.set(true)),
      switchMap(query => this.auditService.search(query).pipe(
        catchError(() => {
          this.events = [];
          this.totalRecords = 0;
          this.failed.set(true);
          this.loading.set(false);
          return EMPTY;
        }))),
      takeUntil(this.destroy$)
    ).subscribe(res => {
      this.events = res.content ?? [];
      this.totalRecords = res.totalElements ?? 0;
      this.failed.set(false);
      this.loading.set(false);
    });
    this.searchInput$.pipe(
      debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntil(this.destroy$)
    ).subscribe(search => {
      this.search = search;
      this.firstPage();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // the table asks for a page: on its first render, and on every paginator change
  onLazyLoad(event: TableLazyLoadEvent): void {
    this.first = event.first ?? 0;
    this.rows = event.rows ?? this.rows;
    this.loadEvents();
  }

  onSearch(value: string): void {
    this.searchInput$.next(value);
  }

  firstPage(): void {
    this.first = 0;
    this.loadEvents();
  }

  loadEvents(): void {
    this.queries$.next({
      page: Math.floor(this.first / this.rows),
      size: this.rows,
      q: this.search,
      eventType: this.eventType ?? undefined,
      serviceName: this.serviceName ?? undefined
    });
  }

  severityFor(eventType: string): AuditSeverity {
    return auditSeverity(eventType);
  }

  instantOf(event: AuditEventDto): string {
    return eventInstant(event);
  }

  orderIdOf(event: AuditEventDto): number | null {
    return orderIdOf(event);
  }

  formatPayload(payload: string): string {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  }
}
