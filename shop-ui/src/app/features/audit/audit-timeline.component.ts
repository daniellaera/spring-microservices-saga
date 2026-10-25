import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { TimelineModule } from 'primeng/timeline';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { Observable, Subject, catchError, of, switchMap, takeUntil, tap } from 'rxjs';
import { AuditEventDto, AuditService } from '../../core/services/audit.service';
import {
  AuditSeverity, SagaSummary, auditSeverity, eventInstant, formatDuration, isCompensation, sagaSummary, sinceStartMs
} from './audit-events';

/**
 * One order's saga, oldest first, from audit-trail-service: ORDER_CREATED, STOCK_DEDUCTED, PAYMENT_*, then ORDER_*
 * or the compensation (STOCK_RESTORED, PAYMENT_REFUNDED). Times are shown in the browser's time zone.
 */
@Component({
  selector: 'app-audit-timeline',
  standalone: true,
  imports: [
    CommonModule, DatePipe, FormsModule, RouterLink, CardModule, TagModule, TimelineModule, ButtonModule,
    InputTextModule, ProgressSpinnerModule
  ],
  templateUrl: './audit-timeline.component.html'
})
export class AuditTimelineComponent implements OnInit, OnDestroy {
  private auditService = inject(AuditService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroy$ = new Subject<void>();

  orderId = signal<number | null>(null);
  events = signal<AuditEventDto[]>([]);
  summary = signal<SagaSummary | null>(null);
  loading = signal(true);
  failed = signal(false);
  invalid = signal(false);
  expanded = new Set<number>();
  otherOrder = '';

  ngOnInit(): void {
    // A failed request is caught inside the switchMap: opening another order (same component) still loads it
    this.route.paramMap.pipe(
      tap(() => {
        this.loading.set(true);
        this.failed.set(false);
        this.invalid.set(false);
        this.expanded.clear();
      }),
      switchMap(params => this.load(params.get('orderId'))),
      takeUntil(this.destroy$)
    ).subscribe(events => {
      this.events.set(events ?? []);
      this.summary.set(events ? sagaSummary(events) : null);
      this.failed.set(events === null && !this.invalid());
      this.loading.set(false);
    });
  }

  // null when there is nothing to show: not an order id (no request), or the request failed
  private load(param: string | null): Observable<AuditEventDto[] | null> {
    if (!param || !/^\d+$/.test(param)) {
      this.orderId.set(null);
      this.invalid.set(true);
      return of(null);
    }
    this.orderId.set(Number(param));
    return this.auditService.getTimeline(Number(param)).pipe(catchError(() => of(null)));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  openOtherOrder(): void {
    const id = this.otherOrder.trim();
    if (/^\d+$/.test(id)) {
      this.otherOrder = '';
      this.router.navigate(['/audit/orders', Number(id)]);
    }
  }

  toggle(event: AuditEventDto): void {
    if (this.expanded.has(event.id)) {
      this.expanded.delete(event.id);
    } else {
      this.expanded.add(event.id);
    }
  }

  severityFor(eventType: string): AuditSeverity {
    return auditSeverity(eventType);
  }

  isCompensation(eventType: string): boolean {
    return isCompensation(eventType);
  }

  instantOf(event: AuditEventDto): string {
    return eventInstant(event);
  }

  sinceStart(event: AuditEventDto): string {
    const first = this.events()[0];
    return first === event ? 'start' : `+${formatDuration(sinceStartMs(first, event))}`;
  }

  formatDuration(ms: number): string {
    return formatDuration(ms);
  }

  // PrimeNG's palette names for the marker dot, after the tag severities
  markerColor(eventType: string): string {
    return { success: 'green', warn: 'orange', danger: 'red', info: 'blue' }[auditSeverity(eventType)];
  }

  outcomeSeverity(summary: SagaSummary): AuditSeverity {
    return summary.outcome === 'CONFIRMED' ? 'success' : summary.outcome === 'CANCELLED' ? 'danger' : 'info';
  }

  formatPayload(payload: string): string {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  }
}
