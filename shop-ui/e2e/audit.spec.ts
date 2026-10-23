import { expect, test } from '@playwright/test';

// Unsigned ADMIN token: the app only decodes it, and the stub gateway only checks it is present
const base64url = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
const ADMIN_TOKEN = `${base64url({ alg: 'HS256' })}.${base64url({
  sub: 'admin@example.com', role: 'ADMIN', exp: 4102444800
})}.signature`;

const rows = '.p-datatable-tbody > tr';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(token => {
    localStorage.setItem('token', token);
    localStorage.setItem('refreshToken', 'refresh-token');
    localStorage.setItem('userEmail', 'admin@example.com');
    localStorage.setItem('userRole', 'ADMIN');
  }, ADMIN_TOKEN);
});

test('a page load of /audit gets the app, which loads the events from the API', async ({ page }) => {
  const api = page.waitForResponse(res => res.url().includes('/audit?page='));
  const document = await page.goto('/audit');

  expect(document?.status()).toBe(200);
  expect(document?.headers()['content-type']).toContain('text/html');
  const apiResponse = await api;
  expect(apiResponse.status()).toBe(200);
  expect(apiResponse.headers()['content-type']).toContain('application/json');

  await expect(page.locator(rows)).toHaveCount(20);
  await expect(page.locator('.p-paginator-current')).toHaveText('1 to 20 of 31');
});

test('searching an order id finds its events', async ({ page }) => {
  await page.goto('/audit');
  await expect(page.locator(rows)).toHaveCount(20);

  await page.getByPlaceholder('Search...').fill('65');

  await expect(page.locator(rows)).toHaveCount(3);
  await expect(page.locator(rows).locator('td:nth-child(3)')).toHaveText(
    ['ORDER_CONFIRMED', 'PAYMENT_SUCCEEDED', 'ORDER_CREATED']);
  for (const entity of await page.locator(rows).locator('td:nth-child(5)').allTextContents()) {
    expect(entity.trim()).toMatch(/\/ 65$/);
  }
});

test('a row expands to show its payload and collapses again', async ({ page }) => {
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
  await expect(page.locator(rows)).toHaveCount(20);

  const reload = await page.reload();

  expect(reload?.status()).toBe(200);
  expect(reload?.headers()['content-type']).toContain('text/html');
  await expect(page).toHaveURL(/\/audit$/);
  await expect(page.locator(rows)).toHaveCount(20);
});
