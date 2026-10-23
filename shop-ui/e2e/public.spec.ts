import { expect, test } from '@playwright/test';

// @public: no login, no payment, nothing written. Safe against production:
//   E2E_BASE_URL=https://shop-staging.daniellaera.com E2E_BUILD_SHA=<deployed sha> npx playwright test --grep @public

test('the navbar shows the build of the deployed bundle', { tag: '@public' }, async ({ page }) => {
  await page.goto('/login');

  const badge = page.locator('[title^="Build:"]');
  await expect(badge).toBeVisible();
  const expected = process.env['E2E_BUILD_SHA'];
  if (expected) {
    await expect(badge).toHaveText(`prod · ${expected}`);
  } else {
    await expect(badge).toHaveText(/^\s*prod · ([0-9a-f]{7}|dev)\s*$/);
  }
});

test('without a session, /dashboard leads to the login form', { tag: '@public' }, async ({ page }) => {
  await page.goto('/dashboard');

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder('daniel@example.com')).toBeVisible();
  await expect(page.locator('main').getByRole('button', { name: 'Sign in' })).toBeVisible();
});

for (const path of ['/products', '/audit', '/orders/1']) {
  test(`a page load of ${path} (also an API path) gets the app`, { tag: '@public' }, async ({ page }) => {
    const document = await page.goto(path);

    expect(document?.status()).toBe(200);
    expect(document?.headers()['content-type']).toContain('text/html');
    await expect(page.locator('app-root app-navbar')).toBeAttached();
  });
}

test('an API call without a token gets 401 from the gateway, not the app', { tag: '@public' }, async ({ request }) => {
  const response = await request.get('/products', { headers: { Accept: 'application/json' } });

  expect(response.status()).toBe(401);
  expect(await response.text()).not.toContain('<app-root');
});
