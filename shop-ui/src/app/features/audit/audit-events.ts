import { AuditEventDto } from '../../core/services/audit.service';

// What the audit list and the timeline need to know about events, kept free of Angular for the unit tests

const SUCCESS_EVENTS = new Set([
  'USER_LOGIN', 'USER_REGISTER', 'TOKEN_REFRESHED',
  'ORDER_CONFIRMED', 'PAYMENT_SUCCEEDED',
  'PRODUCT_CREATED', 'PRODUCT_RESTOCKED', 'STOCK_RESTORED',
  'CART_ITEM_ADDED', 'CART_CHECKOUT'
]);

// someone has to act on these: a refund to make by hand, a message to replay, an order event never sent
const FAILED_EVENTS = new Set([
  'USER_LOGIN_FAILED', 'ORDER_CANCELLED', 'PAYMENT_FAILED',
  'PAYMENT_REFUND_FAILED', 'PAYMENT_DEAD_LETTERED', 'OUTBOX_EVENT_FAILED',
  'ORDER_DEAD_LETTERED', 'STOCK_RESERVATION_DEAD_LETTERED', 'STOCK_RESTORE_DEAD_LETTERED'
]);

// handled, but worth a look: an order failed after its payment was captured, and the money went back
const WARN_EVENTS = new Set([
  'PAYMENT_REFUNDED'
]);

// the saga undoing what an earlier step did
const COMPENSATION_EVENTS = new Set(['STOCK_RESTORED', 'PAYMENT_REFUNDED']);

export type AuditSeverity = 'success' | 'warn' | 'danger' | 'info';

export function auditSeverity(eventType: string): AuditSeverity {
  if (SUCCESS_EVENTS.has(eventType)) return 'success';
  if (FAILED_EVENTS.has(eventType)) return 'danger';
  if (WARN_EVENTS.has(eventType)) return 'warn';
  return 'info';
}

export function isCompensation(eventType: string): boolean {
  return COMPENSATION_EVENTS.has(eventType);
}

/**
 * The event's instant as ISO-8601 with its offset, which DatePipe shows in the browser's time zone. Before
 * recordedAt existed, createdAt (UTC, sent without an offset) would have been read as local time.
 */
export function eventInstant(event: Pick<AuditEventDto, 'createdAt' | 'recordedAt'>): string {
  if (event.recordedAt) {
    return event.recordedAt;
  }
  return /([zZ]|[+-]\d\d:\d\d)$/.test(event.createdAt) ? event.createdAt : `${event.createdAt}Z`;
}

// The order an event belongs to, for its timeline link: its correlation id, or, for an event recorded before it, the
// order id an order or payment event has as its entity (a payment intent id is not one: not all digits)
export function orderIdOf(event: Pick<AuditEventDto, 'correlationId' | 'entityType' | 'entityId'>): number | null {
  const id = event.correlationId ?? (['ORDER', 'PAYMENT'].includes(event.entityType) ? event.entityId : null);
  return id && /^\d+$/.test(id) ? Number(id) : null;
}

export type SagaOutcome = 'CONFIRMED' | 'CANCELLED' | 'IN_PROGRESS';

export interface SagaSummary {
  outcome: SagaOutcome;
  compensated: boolean;
  // from the first event to the last, in milliseconds
  durationMs: number;
}

export function sagaSummary(events: AuditEventDto[]): SagaSummary {
  const types = events.map(e => e.eventType);
  const outcome: SagaOutcome = types.includes('ORDER_CONFIRMED') ? 'CONFIRMED'
    : types.includes('ORDER_CANCELLED') ? 'CANCELLED' : 'IN_PROGRESS';
  return {
    outcome,
    compensated: types.some(isCompensation),
    durationMs: events.length ? sinceStartMs(events[0], events[events.length - 1]) : 0
  };
}

export function sinceStartMs(first: AuditEventDto, event: AuditEventDto): number {
  return Date.parse(eventInstant(event)) - Date.parse(eventInstant(first));
}

export function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}
