import { expect, test } from '@playwright/test';
import { AuditEvent, loginAsAdmin, sagaEvent, stubAuditApi } from './support/audit';

// One order's saga timeline (/audit/orders/:orderId). The first test goes through the real nginx.conf to the stub
// gateway, which answers order 42's confirmed saga (e2e/stack/timeline-events.json); the others stub the API.

const types = 'timeline-event-type';
const times = 'timeline-event-time';

const compensated: AuditEvent[] = [
  sagaEvent(301, 'ORDER_CREATED', 'order-service', '2026-07-01T08:00:00.000Z', 43),
  sagaEvent(302, 'STOCK_DEDUCTED', 'inventory-service', '2026-07-01T08:00:00.300Z', 43),
  sagaEvent(303, 'PAYMENT_FAILED', 'payment-service', '2026-07-01T08:00:01.000Z', 43),
  sagaEvent(304, 'STOCK_RESTORED', 'inventory-service', '2026-07-01T08:00:01.200Z', 43),
  sagaEvent(305, 'ORDER_CANCELLED', 'order-service', '2026-07-01T08:00:01.250Z', 43)
];

// inventory rejected an order whose payment was captured: no stock to give back, the payment goes back
const refunded: AuditEvent[] = [
  sagaEvent(401, 'ORDER_CREATED', 'order-service', '2026-07-01T09:00:00.000Z', 44),
  sagaEvent(402, 'PAYMENT_REFUNDED', 'payment-service', '2026-07-01T09:00:02.000Z', 44),
  sagaEvent(403, 'ORDER_CANCELLED', 'order-service', '2026-07-01T09:00:02.100Z', 44)
];

test.beforeEach(async ({ page }) => {
  await loginAsAdmin(page);
});

test.describe('in Zurich', () => {
  test.use({ timezoneId: 'Europe/Zurich' });

  test('a page load of an order\'s timeline gets the app, which shows its confirmed saga in order', async ({ page }) => {
    const api = page.waitForResponse(res => res.url().endsWith('/audit/orders/42/timeline'));
    const document = await page.goto('/audit/orders/42');

    expect(document?.status()).toBe(200);
    expect(document?.headers()['content-type']).toContain('text/html');
    const apiResponse = await api;
    expect(apiResponse.status()).toBe(200);
    expect(apiResponse.headers()['content-type']).toContain('application/json');

    await expect(page.getByTestId('timeline-title')).toHaveText('Order #42 · saga timeline');
    await expect(page.getByTestId(types)).toHaveText(
      ['ORDER_CREATED', 'STOCK_DEDUCTED', 'PAYMENT_SUCCEEDED', 'ORDER_CONFIRMED']);
    await expect(page.getByTestId('timeline-outcome')).toHaveText('CONFIRMED');
    await expect(page.getByTestId('timeline-compensated')).toHaveCount(0);
    await expect(page.getByTestId('timeline-summary')).toContainText('4 events in 1.5 s');
    // 08:00 UTC is 10:00 in Zurich on 1 July (CEST)
    await expect(page.getByTestId(times)).toHaveText([
      '2026-07-01 10:00:00.000', '2026-07-01 10:00:00.400', '2026-07-01 10:00:01.200', '2026-07-01 10:00:01.500']);
    await expect(page.getByTestId('timeline-event-offset')).toHaveText(['start', '+400 ms', '+1.2 s', '+1.5 s']);
    await expect(page.locator('time').first()).toHaveAttribute('datetime', '2026-07-01T08:00:00.000Z');

    const reload = await page.reload();
    expect(reload?.headers()['content-type']).toContain('text/html');
    await expect(page.getByTestId(types)).toHaveCount(4);
  });
});

test.describe('in New York', () => {
  test.use({ timezoneId: 'America/New_York' });

  test('the same instants are shown in New York time', async ({ page }) => {
    await page.goto('/audit/orders/42');

    // 08:00 UTC is 04:00 in New York on 1 July (EDT)
    await expect(page.getByTestId(times).first()).toHaveText('2026-07-01 04:00:00.000');
  });
});

test('a failed payment: the compensation gives the stock back and cancels the order', async ({ page }) => {
  await stubAuditApi(page, { events: compensated });

  await page.goto('/audit/orders/43');

  await expect(page.getByTestId(types)).toHaveText(
    ['ORDER_CREATED', 'STOCK_DEDUCTED', 'PAYMENT_FAILED', 'STOCK_RESTORED', 'ORDER_CANCELLED']);
  await expect(page.getByTestId('timeline-outcome')).toHaveText('CANCELLED');
  await expect(page.getByTestId('timeline-compensated')).toHaveText('COMPENSATED');
  await expect(page.getByTestId('timeline-event').nth(3)).toContainText('compensation');
  await expect(page.getByTestId('timeline-event').nth(2)).not.toContainText('compensation');
});

test('a paid order inventory rejected: the payment is refunded', async ({ page }) => {
  await stubAuditApi(page, { events: refunded });

  await page.goto('/audit/orders/44');

  await expect(page.getByTestId(types)).toHaveText(['ORDER_CREATED', 'PAYMENT_REFUNDED', 'ORDER_CANCELLED']);
  await expect(page.getByTestId('timeline-outcome')).toHaveText('CANCELLED');
  await expect(page.getByTestId('timeline-compensated')).toHaveText('COMPENSATED');
});

test('an event\'s payload opens and closes', async ({ page }) => {
  await stubAuditApi(page, { events: [sagaEvent(501, 'PAYMENT_FAILED', 'payment-service',
    '2026-07-01T08:00:00Z', 45, { orderId: 45, status: 'FAILED' })] });
  await page.goto('/audit/orders/45');

  await page.getByRole('button', { name: 'Payload' }).click();

  await expect(page.locator('pre')).toContainText('"status": "FAILED"');

  await page.getByRole('button', { name: 'Hide payload' }).click();

  await expect(page.locator('pre')).toHaveCount(0);
});

test('another order opens from the order id field', async ({ page }) => {
  const api = await stubAuditApi(page, { events: [...compensated, ...refunded] });
  await page.goto('/audit/orders/43');
  await expect(page.getByTestId(types)).toHaveCount(5);

  await page.getByRole('textbox', { name: 'Order id' }).fill('44');
  await page.getByRole('button', { name: 'Open' }).click();

  await expect(page).toHaveURL(/\/audit\/orders\/44$/);
  await expect(page.getByTestId('timeline-title')).toHaveText('Order #44 · saga timeline');
  await expect(page.getByTestId(types)).toHaveCount(3);
  expect(api.timelineRequests).toEqual(['43', '44']);
});

test('an order without events, and an audit service that does not answer', async ({ page }) => {
  await stubAuditApi(page, {
    events: [],
    timelineAnswer: orderId => orderId === '47' ? { status: 503, body: { status: '503' } } : undefined
  });

  await page.goto('/audit/orders/46');
  await expect(page.getByTestId('timeline-empty')).toHaveText('No audit events for order #46.');

  await page.goto('/audit/orders/47');
  await expect(page.getByTestId('timeline-error')).toContainText('could not be loaded');
});

test('after a failed timeline, another order still opens', async ({ page }) => {
  const api = await stubAuditApi(page, {
    events: compensated,
    timelineAnswer: orderId => orderId === '47' ? { status: 503, body: { status: '503' } } : undefined
  });
  await page.goto('/audit/orders/47');
  await expect(page.getByTestId('timeline-error')).toBeVisible();

  await page.getByRole('textbox', { name: 'Order id' }).fill('43');
  await page.getByRole('button', { name: 'Open' }).click();

  await expect(page.getByTestId('timeline-title')).toHaveText('Order #43 · saga timeline');
  await expect(page.getByTestId(types)).toHaveCount(5);
  expect(api.timelineRequests).toEqual(['47', '43']);
});

test('a path that is not an order id asks nothing', async ({ page }) => {
  const api = await stubAuditApi(page);

  await page.goto('/audit/orders/abc');

  await expect(page.getByTestId('timeline-invalid')).toBeVisible();
  await expect(page.getByTestId('timeline-title')).toHaveText('Saga timeline');
  expect(api.timelineRequests).toEqual([]);
});

test('an admin opens an order\'s timeline from the dashboard', async ({ page }) => {
  await stubAuditApi(page, { events: compensated });
  await page.route('**/*', async route => {
    const request = route.request();
    const { pathname } = new URL(request.url());
    if (!['xhr', 'fetch'].includes(request.resourceType()) || pathname.startsWith('/audit')) {
      return route.fallback();
    }
    if (pathname === '/orders/stream') {
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body: '' });
    }
    const body = pathname === '/orders'
      ? { content: [{ id: 43, productName: 'Lamp', quantity: 1, price: 49.9, totalAmount: 49.9, status: 'CANCELLED',
          userEmail: 'buyer@example.com', createdAt: '2026-07-01T08:00:00', items: [] }],
          currentPage: 0, totalPages: 1, totalElements: 1, hasNext: false, hasPrevious: false }
      : pathname === '/products' ? []
      : pathname === '/cart' ? { userEmail: 'admin@example.com', items: [], total: 0, ttlSeconds: 3600, expiresIn: '60m' }
      : {};
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/dashboard');

  await page.getByRole('link', { name: 'Saga timeline of order 43' }).click();

  await expect(page).toHaveURL(/\/audit\/orders\/43$/);
  await expect(page.getByTestId('timeline-outcome')).toHaveText('CANCELLED');
});
