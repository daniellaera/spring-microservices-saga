import { expect, Page, test } from '@playwright/test';
import { fakeStripe, loginAsUser, stubShopApi } from './support/shop';

const IPAD = { id: 3, name: 'iPad Pro', quantity: 7, price: 799.99 };
const MACBOOK = { id: 2, name: 'MacBook Pro', quantity: 11, price: 1299.99 };

const quantityField = (page: Page) => page.locator('#order-quantity');
const total = (page: Page) => page.getByText('Estimated total:').locator('strong');
const buyNow = (page: Page) => page.getByRole('button', { name: 'Buy now' });
const addToCart = (page: Page) => page.getByRole('button', { name: 'Add to cart' });

async function typeQuantity(page: Page, quantity: string): Promise<void> {
  await quantityField(page).click();
  await quantityField(page).press('ControlOrMeta+a');
  await quantityField(page).pressSequentially(quantity);
}

test.beforeEach(async ({ page }) => {
  await loginAsUser(page);
  await fakeStripe(page);
});

test('typed 8 with 7 in stock: Buy now is blocked with "Only 7 left"; with 7, the create-intent amount equals the total shown',
  async ({ page }) => {
    const api = await stubShopApi(page, { products: [IPAD, MACBOOK] });
    await page.goto('/dashboard');
    await expect(page.locator('p-select')).toContainText('iPad Pro');

    await typeQuantity(page, '8');

    await expect(page.getByTestId('stock-limit')).toHaveText('Only 7 left');
    await expect(buyNow(page)).toBeDisabled();
    await expect(addToCart(page)).toBeDisabled();
    await expect(total(page)).toHaveText('6399.92 €');

    // leaving the field doesn't silently change the quantity
    await page.getByText('Place an order').click();
    await expect(quantityField(page)).toHaveValue('8');
    await expect(buyNow(page)).toBeDisabled();

    await typeQuantity(page, '7');

    await expect(page.getByTestId('stock-limit')).toHaveCount(0);
    await expect(buyNow(page)).toBeEnabled();
    await expect(total(page)).toHaveText('5599.93 €');
    const shown = Number((await total(page).textContent())!.replace('€', '').trim());

    await buyNow(page).click();

    await expect(page.getByRole('dialog')).toContainText('7 × 799.99 € = 5599.93 €');
    expect(api.createIntentRequests).toHaveLength(1);
    expect(api.createIntentRequests[0].amount).toBe(Math.round(shown * 100));
    expect(api.createIntentRequests[0].amount).toBe(559993);
  });

test('pre-selects the first product in stock, and out-of-stock products are not offered', async ({ page }) => {
  await stubShopApi(page, { products: [{ ...MACBOOK, quantity: 0 }, IPAD] });
  await page.goto('/dashboard');

  await expect(page.locator('p-select')).toContainText('iPad Pro');
  await expect(buyNow(page)).toBeEnabled();

  await page.locator('p-select').click();
  await expect(page.getByRole('option')).toHaveText([/iPad Pro/]);
});

test('with nothing in stock, nothing is selected and Buy now is disabled', async ({ page }) => {
  await stubShopApi(page, { products: [{ ...IPAD, quantity: 0 }] });
  await page.goto('/dashboard');

  await expect(page.locator('p-select')).toContainText('Select a product');
  await expect(buyNow(page)).toBeDisabled();
  await expect(addToCart(page)).toBeDisabled();
});
