import { auditSeverity } from './audit-events';

describe('auditSeverity', () => {
  it('shows the events someone has to act on as danger', () => {
    for (const type of ['PAYMENT_FAILED', 'ORDER_CANCELLED', 'PAYMENT_REFUND_FAILED', 'PAYMENT_DEAD_LETTERED',
      'OUTBOX_EVENT_FAILED']) {
      expect(auditSeverity(type)).toBe('danger');
    }
  });

  it('shows a refund as warn, a confirmed order as success, anything else as info', () => {
    expect(auditSeverity('PAYMENT_REFUNDED')).toBe('warn');
    expect(auditSeverity('ORDER_CONFIRMED')).toBe('success');
    expect(auditSeverity('STOCK_DEDUCTED')).toBe('info');
  });
});
