import { expect, test } from '@playwright/test';
import { loginAsAdmin, stubAuditApi } from './support/audit';

// The audit trail is paginated and filtered by audit-trail-service: the first and last tests go through the real
// nginx.conf to the stub gateway (e2e/stack), the others stub the API in the browser and check what is asked for.

const rows = '.p-datatable-tbody > tr';

test.beforeEach(async ({ page }) => {
  await loginAsAdmin(page);
});

test('a page load of /audit gets the app, which loads the events from the API', async ({ page }) => {
  const api = page.waitForResponse(res => res.url().includes('/audit?page='));
  const document = await page.goto('/audit');

  expect(document?.status()).toBe(200);
  expect(document?.headers()['content-type']).toContain('text/html');
  const apiResponse = await api;
  expect(apiResponse.status()).toBe(200);
  expect(apiResponse.headers()['content-type']).toContain('application/json');
  expect(new URL(apiResponse.url()).searchParams.toString()).toBe('page=0&size=20');

  await expect(page.locator(rows).first().locator('td:nth-child(3)')).toHaveText('USER_LOGIN');
  await expect(page.locator('.p-paginator-current')).toHaveText('1 to 20 of 31');
});

test('the paginator asks the server for the next page', async ({ page }) => {
  const api = await stubAuditApi(page);
  await page.goto('/audit');
  await expect(page.locator(rows)).toHaveCount(20);

  await page.locator('.p-paginator-next').click();

  await expect(page.locator(rows)).toHaveCount(11);
  await expect(page.locator('.p-paginator-current')).toHaveText('21 to 31 of 31');
  expect(api.listRequests.map(params => params.toString())).toEqual(['page=0&size=20', 'page=1&size=20']);
});

test('searching an order id asks the server, from the first page', async ({ page }) => {
  const api = await stubAuditApi(page);
  await page.goto('/audit');
  await page.locator('.p-paginator-next').click();
  await expect(page.locator('.p-paginator-current')).toHaveText('21 to 31 of 31');

  await page.getByPlaceholder('Search...').fill('65');

  await expect(page.locator(rows)).toHaveCount(3);
  await expect(page.locator(rows).locator('td:nth-child(3)')).toHaveText(
    ['ORDER_CONFIRMED', 'PAYMENT_SUCCEEDED', 'ORDER_CREATED']);
  for (const entity of await page.locator(rows).locator('td:nth-child(5)').allTextContents()) {
    expect(entity.trim()).toMatch(/\/ 65$/);
  }
  // typing is debounced: one request for the whole search
  expect(api.listRequests.at(-1)?.toString()).toBe('page=0&size=20&q=65');
  expect(api.listRequests.filter(params => params.has('q'))).toHaveLength(1);
});

test('the event type and service filters are sent to the server', async ({ page }) => {
  const api = await stubAuditApi(page);
  await page.goto('/audit');
  await expect(page.locator(rows)).toHaveCount(20);

  await page.getByRole('combobox', { name: 'Event type' }).click();
  await page.getByRole('option', { name: 'PAYMENT_SUCCEEDED', exact: true }).click();

  await expect(page.locator(rows)).toHaveCount(5);
  expect(api.listRequests.at(-1)?.toString()).toBe('page=0&size=20&eventType=PAYMENT_SUCCEEDED');

  await page.getByRole('combobox', { name: 'Service' }).click();
  await page.getByRole('option', { name: 'order-service', exact: true }).click();

  await expect(page.locator(rows)).toHaveCount(1);
  await expect(page.locator(rows).first()).toContainText('No audit events found.');
  expect(api.listRequests.at(-1)?.toString()).toBe('page=0&size=20&eventType=PAYMENT_SUCCEEDED&serviceName=order-service');
});

test('after a failed request, Refresh asks again and the list comes back', async ({ page }) => {
  const api = await stubAuditApi(page, { listFailures: 1 });
  await page.goto('/audit');
  await expect(page.locator(rows).first()).toContainText('could not be loaded');

  await page.getByTitle('Refresh').click();

  await expect(page.locator(rows)).toHaveCount(20);
  expect(api.listRequests).toHaveLength(2);
});

test.describe('in Zurich', () => {
  test.use({ timezoneId: 'Europe/Zurich' });

  test('timestamps are shown in the browser\'s time zone', async ({ page }) => {
    await stubAuditApi(page);
    await page.goto('/audit');

    // recorded at 07:59 UTC, sent without an offset (before recordedAt): 09:59 in Zurich on 23 October (CEST)
    await expect(page.locator(rows).first().locator('td:nth-child(2)')).toHaveText('2026-10-23 09:59:00');
  });
});

test('an order event links to the order\'s saga timeline', async ({ page }) => {
  await stubAuditApi(page);
  await page.goto('/audit');
  await page.getByPlaceholder('Search...').fill('65');
  await expect(page.locator(rows)).toHaveCount(3);

  await page.getByRole('link', { name: 'Timeline of order 65' }).first().click();

  await expect(page).toHaveURL(/\/audit\/orders\/65$/);
  await expect(page.getByTestId('timeline-event-type')).toHaveText(['ORDER_CREATED', 'PAYMENT_SUCCEEDED', 'ORDER_CONFIRMED']);
});

test('a row expands to show its payload and collapses again', async ({ page }) => {
  await stubAuditApi(page);
  await page.goto('/audit');
  await page.getByPlaceholder('Search...').fill('65');
  await expect(page.locator(rows)).toHaveCount(3);
  const toggler = page.locator(rows).first().locator('p-button button');

  await toggler.click();

  const payload = page.locator(`${rows} pre`);
  await expect(payload).toBeVisible();
  await expect(payload).toContainText('"orderId": 65');
  await expect(payload).toContainText('"status": "SUCCESS"');
  await expect(toggler.locator('.pi-chevron-down')).toBeVisible();

  await toggler.click();

  await expect(payload).toHaveCount(0);
});

test('Entity and Service columns are visible at desktop width only', async ({ page }) => {
  await stubAuditApi(page);
  await page.goto('/audit');
  const header = (name: string) => page.locator('.p-datatable-thead th', { hasText: name });
  const firstRowCell = (n: number) => page.locator(rows).first().locator(`td:nth-child(${n})`);

  await expect(header('Entity')).toBeVisible();
  await expect(header('Service')).toBeVisible();
  await expect(firstRowCell(5)).toHaveText('USER / admin@example.com');
  await expect(firstRowCell(6)).toHaveText('auth-service');

  await page.setViewportSize({ width: 390, height: 844 });

  await expect(header('Entity')).toBeHidden();
  await expect(header('Service')).toBeHidden();
});

test('reloading /audit still renders the app', async ({ page }) => {
  await page.goto('/audit');
  await expect(page.locator('.p-paginator-current')).toHaveText('1 to 20 of 31');

  const reload = await page.reload();

  expect(reload?.status()).toBe(200);
  expect(reload?.headers()['content-type']).toContain('text/html');
  await expect(page).toHaveURL(/\/audit$/);
  await expect(page.locator('.p-paginator-current')).toHaveText('1 to 20 of 31');
});
