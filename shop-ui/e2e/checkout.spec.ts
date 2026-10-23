import { expect, Page, test } from '@playwright/test';
import { fakeStripe, GATEWAY_TIMEOUT_404, loginAsUser, stubShopApi } from './support/shop';

const IPAD = { id: 3, name: 'iPad Pro', quantity: 7, price: 799.99 };

const dialog = (page: Page) => page.getByRole('dialog');
const card = (page: Page) => page.getByTestId('fake-card');
const buyNow = (page: Page) => page.getByRole('button', { name: 'Buy now' });
const payNow = (page: Page) => page.getByRole('button', { name: 'Pay now' });
const cancel = (page: Page) => dialog(page).getByRole('button', { name: 'Cancel' });
const toast = (page: Page) => page.locator('app-dashboard p-toast');

async function setQuantity(page: Page, quantity: string): Promise<void> {
  const field = page.locator('#order-quantity');
  await field.click();
  await field.press('ControlOrMeta+a');
  await field.pressSequentially(quantity);
}

async function openPaymentForm(page: Page): Promise<void> {
  await buyNow(page).click();
  await expect(card(page)).toBeVisible();
  await expect(payNow(page)).toBeEnabled();
}

test.beforeEach(async ({ page }) => {
  await loginAsUser(page);
});

test('the first Buy now after a fresh load mounts the payment form, even with a slow Stripe.js', async ({ page }) => {
  await fakeStripe(page, { scriptDelayMs: 1500 });
  const api = await stubShopApi(page, { products: [IPAD] });
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => message.type() === 'error' && pageErrors.push(message.text()));
  await page.goto('/dashboard');

  await buyNow(page).click();

  await expect(dialog(page)).toContainText('1 × 799.99 € = 799.99 €');
  await expect(card(page)).toBeVisible();
  await expect(payNow(page)).toBeEnabled();
  expect(await page.evaluate(() => (window as any).__fakeStripe.mounts)).toBe(1);
  expect(api.createIntentRequests).toHaveLength(1);
  expect(pageErrors).toEqual([]);
});

test('a failed create-intent says why, and Buy now works again without a reload', async ({ page }) => {
  await fakeStripe(page);
  const api = await stubShopApi(page, {
    products: [IPAD],
    createIntentResponses: [GATEWAY_TIMEOUT_404, { status: 503, body: { status: '503', message: 'unavailable' } }]
  });
  await page.goto('/dashboard');

  for (const attempt of [1, 2]) {
    await buyNow(page).click();

    await expect(page.getByTestId('checkout-error')).toHaveText('The payment service did not answer. Please try again.');
    await expect(toast(page)).toContainText('Payment setup failed');
    await expect(dialog(page)).toHaveCount(0);
    await expect(buyNow(page)).toBeEnabled();
    expect(api.createIntentRequests).toHaveLength(attempt);
  }

  await openPaymentForm(page);

  await expect(page.getByTestId('checkout-error')).toHaveCount(0);
  expect(api.createIntentRequests).toHaveLength(3);
});

test('one PaymentIntent per purchase: Cancel and Buy now again reuse it, another quantity gets a new one',
  async ({ page }) => {
    await fakeStripe(page);
    const api = await stubShopApi(page, { products: [IPAD] });
    await page.goto('/dashboard');

    await openPaymentForm(page);
    await cancel(page).click();
    await expect(dialog(page)).toHaveCount(0);
    await openPaymentForm(page);

    expect(api.createIntentRequests).toHaveLength(1);
    await expect(card(page)).toHaveAttribute('data-client-secret', 'pi_e2e_1_secret_e2e');
    await cancel(page).click();

    await setQuantity(page, '2');
    await openPaymentForm(page);

    expect(api.createIntentRequests).toHaveLength(2);
    expect(api.createIntentRequests[1].amount).toBe(159998);
    await expect(card(page)).toHaveAttribute('data-client-secret', 'pi_e2e_2_secret_e2e');
  });

test('a Stripe.js that fails to load is shown in the dialog, and Retry mounts the form', async ({ page }) => {
  await fakeStripe(page);
  // registered last, so it answers first: Stripe.js can't be downloaded until it is removed
  const blockStripe = (route: any) => route.abort('connectionfailed');
  await page.route('https://js.stripe.com/**', blockStripe);
  await stubShopApi(page, { products: [IPAD] });
  await page.goto('/dashboard');

  await buyNow(page).click();

  await expect(page.getByTestId('payment-error')).toContainText('The payment form could not be loaded');
  await expect(toast(page)).toContainText('Payment form unavailable');
  await expect(payNow(page)).toBeDisabled();

  await page.unroute('https://js.stripe.com/**', blockStripe);
  await page.getByRole('button', { name: 'Retry' }).click();

  await expect(card(page)).toBeVisible();
  await expect(payNow(page)).toBeEnabled();
  await expect(page.getByTestId('payment-error')).toHaveCount(0);
});

test('a declined card is shown, the spinner stops, and paying again creates the order', async ({ page }) => {
  await fakeStripe(page, { confirmResults: ['declined'] });
  const api = await stubShopApi(page, { products: [IPAD] });
  await page.goto('/dashboard');
  await setQuantity(page, '2');
  await openPaymentForm(page);

  await payNow(page).click();

  await expect(page.getByTestId('payment-error')).toHaveText('Your card was declined.');
  await expect(toast(page)).toContainText('Payment failed');
  await expect(payNow(page)).toBeEnabled();
  await expect(payNow(page).locator('.p-button-loading-icon, .pi-spin')).toHaveCount(0);
  expect(api.orderRequests).toHaveLength(0);

  await payNow(page).click();

  await expect(dialog(page)).toHaveCount(0);
  expect(api.orderRequests).toEqual([
    { items: [{ productName: 'iPad Pro', quantity: 2, price: 799.99 }], paymentIntentId: 'pi_e2e_1' }
  ]);
  expect(api.createIntentRequests).toHaveLength(1);
});

test('after a paid order the form starts clean, and the next purchase gets a new intent', async ({ page }) => {
  await fakeStripe(page, { confirmResults: ['declined'] });
  const api = await stubShopApi(page, { products: [IPAD] });
  await page.goto('/dashboard');
  await setQuantity(page, '3');
  await openPaymentForm(page);
  await payNow(page).click();
  await expect(page.getByTestId('payment-error')).toBeVisible();
  await payNow(page).click();
  await expect(dialog(page)).toHaveCount(0);

  await expect(page.locator('#order-quantity')).toHaveValue('1');
  await expect(page.getByText('Estimated total:').locator('strong')).toHaveText('799.99 €');
  await setQuantity(page, '3');
  await openPaymentForm(page);

  // the same purchase as the paid one, yet a new intent: a paid intent is never reused
  expect(api.createIntentRequests).toHaveLength(2);
  await expect(page.getByTestId('payment-error')).toHaveCount(0);
  await expect(card(page)).toHaveCount(1);
  await expect(card(page)).toHaveAttribute('data-client-secret', 'pi_e2e_2_secret_e2e');
});

test('Cancel after an error closes the dialog, and the next one starts clean', async ({ page }) => {
  await fakeStripe(page, { confirmResults: ['declined'] });
  await stubShopApi(page, { products: [IPAD] });
  await page.goto('/dashboard');
  await openPaymentForm(page);
  await payNow(page).click();
  await expect(page.getByTestId('payment-error')).toBeVisible();

  await cancel(page).click();
  await expect(dialog(page)).toHaveCount(0);
  await openPaymentForm(page);

  await expect(page.getByTestId('payment-error')).toHaveCount(0);
  await expect(card(page)).toHaveCount(1);
  expect(await page.evaluate(() => (window as any).__fakeStripe.mounts)).toBe(2);
});

test('cart checkout: one intent for the cart, and the order is created from the items paid for', async ({ page }) => {
  await fakeStripe(page);
  const api = await stubShopApi(page, { products: [IPAD] });
  await page.goto('/dashboard');
  await setQuantity(page, '2');
  await page.getByRole('button', { name: 'Add to cart' }).click();
  const checkout = page.getByRole('button', { name: 'Checkout' });

  await checkout.click();
  await expect(card(page)).toBeVisible();
  await cancel(page).click();
  await page.locator('p-button[icon="pi pi-shopping-cart"], [aria-label*="cart" i]').first().click();
  await checkout.click();
  await expect(card(page)).toBeVisible();
  await payNow(page).click();
  await expect(dialog(page)).toHaveCount(0);

  expect(api.createIntentRequests).toEqual([{ amount: 159998, currency: 'eur', productName: 'iPad Pro' }]);
  expect(api.orderRequests).toEqual([
    { items: [{ productName: 'iPad Pro', quantity: 2, price: 799.99 }], paymentIntentId: 'pi_e2e_1' }
  ]);
});
