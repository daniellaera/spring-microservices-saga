import { AuditEventDto } from '../../core/services/audit.service';
import { eventInstant, formatDuration, isCompensation, orderIdOf, sagaSummary, sinceStartMs } from './audit-events';

const event = (id: number, eventType: string, recordedAt: string, extra: Partial<AuditEventDto> = {}): AuditEventDto => ({
  id, eventType, recordedAt, createdAt: recordedAt.replace('Z', ''), userEmail: 'buyer@test.com',
  entityType: 'ORDER', entityId: '42', payload: '{}', serviceName: 'order-service', correlationId: '42', ...extra
});

describe('eventInstant', () => {
  it('uses recordedAt, the instant with its offset', () => {
    expect(eventInstant({ createdAt: '2026-10-25T08:00:00', recordedAt: '2026-10-25T08:00:00Z' }))
      .toBe('2026-10-25T08:00:00Z');
  });

  it('reads an older createdAt, sent without an offset, as UTC rather than as local time', () => {
    expect(eventInstant({ createdAt: '2026-10-25T08:00:00.123456' })).toBe('2026-10-25T08:00:00.123456Z');
    expect(Date.parse(eventInstant({ createdAt: '2026-10-25T08:00:00' }))).toBe(Date.UTC(2026, 9, 25, 8, 0, 0));
  });

  it('keeps an offset that is already there', () => {
    expect(eventInstant({ createdAt: '2026-10-25T10:00:00+02:00' })).toBe('2026-10-25T10:00:00+02:00');
  });
});

describe('orderIdOf', () => {
  it('is the correlation id of a saga event, whatever its entity', () => {
    expect(orderIdOf({ correlationId: '42', entityType: 'PRODUCT', entityId: '3' })).toBe(42);
  });

  it('falls back on the order id of an order or payment event recorded before the correlation id', () => {
    expect(orderIdOf({ correlationId: null, entityType: 'ORDER', entityId: '65' })).toBe(65);
    expect(orderIdOf({ entityType: 'ORDER', entityId: '65' })).toBe(65);
    expect(orderIdOf({ entityType: 'PAYMENT', entityId: '65' })).toBe(65);
  });

  it('is null outside a saga, and for a payment intent (PAYMENT_INITIATED)', () => {
    expect(orderIdOf({ correlationId: null, entityType: 'USER', entityId: 'admin@example.com' })).toBeNull();
    expect(orderIdOf({ correlationId: null, entityType: 'PAYMENT', entityId: 'pi_123' })).toBeNull();
    expect(orderIdOf({ correlationId: null, entityType: 'PRODUCT', entityId: '3' })).toBeNull();
  });
});

describe('sagaSummary', () => {
  it('a confirmed order', () => {
    const events = [
      event(1, 'ORDER_CREATED', '2026-10-25T08:00:00.000Z'),
      event(2, 'STOCK_DEDUCTED', '2026-10-25T08:00:00.400Z'),
      event(3, 'PAYMENT_SUCCEEDED', '2026-10-25T08:00:01.200Z'),
      event(4, 'ORDER_CONFIRMED', '2026-10-25T08:00:01.500Z')
    ];

    expect(sagaSummary(events)).toEqual({ outcome: 'CONFIRMED', compensated: false, durationMs: 1500 });
    expect(sinceStartMs(events[0], events[2])).toBe(1200);
  });

  it('a cancelled order whose stock went back', () => {
    expect(sagaSummary([
      event(1, 'ORDER_CREATED', '2026-10-25T08:00:00Z'),
      event(2, 'STOCK_DEDUCTED', '2026-10-25T08:00:01Z'),
      event(3, 'PAYMENT_FAILED', '2026-10-25T08:00:02Z'),
      event(4, 'STOCK_RESTORED', '2026-10-25T08:00:03Z'),
      event(5, 'ORDER_CANCELLED', '2026-10-25T08:00:03Z')
    ])).toEqual({ outcome: 'CANCELLED', compensated: true, durationMs: 3000 });
  });

  it('an order still waiting for its payment', () => {
    expect(sagaSummary([event(1, 'ORDER_CREATED', '2026-10-25T08:00:00Z')]).outcome).toBe('IN_PROGRESS');
    expect(sagaSummary([])).toEqual({ outcome: 'IN_PROGRESS', compensated: false, durationMs: 0 });
  });
});

describe('isCompensation and formatDuration', () => {
  it('the steps that undo an earlier one', () => {
    expect(isCompensation('STOCK_RESTORED')).toBe(true);
    expect(isCompensation('PAYMENT_REFUNDED')).toBe(true);
    expect(isCompensation('PAYMENT_FAILED')).toBe(false);
  });

  it('milliseconds under a second, seconds above', () => {
    expect(formatDuration(400)).toBe('400 ms');
    expect(formatDuration(1500)).toBe('1.5 s');
  });
});
