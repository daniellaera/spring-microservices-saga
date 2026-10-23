import { Page, Route } from '@playwright/test';

// A logged-in USER with the shop API and Stripe.js stubbed in the browser (page.route): no backend, no Stripe.

const base64url = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
const USER_EMAIL = 'shopper@example.com';
// Unsigned token: the app only decodes it
const USER_TOKEN = `${base64url({ alg: 'HS256' })}.${base64url({
  sub: USER_EMAIL, role: 'USER', exp: 4102444800
})}.signature`;

export interface Product {
  id: number;
  name: string;
  quantity: number;
  price: number;
}

export interface StubResponse {
  status: number;
  body: unknown;
}

export interface ShopApiOptions {
  products: Product[];
  // answers of POST /payments/create-intent, in order; after them, a new intent each time
  createIntentResponses?: StubResponse[];
}

export interface ShopApi {
  createIntentRequests: { amount: number; currency: string; productName: string }[];
  orderRequests: { items: { productName: string; quantity: number; price: number }[]; paymentIntentId: string }[];
}

export async function loginAsUser(page: Page): Promise<void> {
  await page.addInitScript(token => {
    localStorage.setItem('token', token);
    localStorage.setItem('refreshToken', 'refresh-token');
    localStorage.setItem('userEmail', 'shopper@example.com');
    localStorage.setItem('userRole', 'USER');
  }, USER_TOKEN);
}

export async function stubShopApi(page: Page, options: ShopApiOptions): Promise<ShopApi> {
  const api: ShopApi = { createIntentRequests: [], orderRequests: [] };
  const responses = [...(options.createIntentResponses ?? [])];
  let cartItems: { productName: string; quantity: number; price: number }[] = [];
  let intents = 0;
  const cart = () => ({
    userEmail: USER_EMAIL,
    items: cartItems,
    total: cartItems.reduce((sum, i) => sum + i.price * i.quantity, 0),
    ttlSeconds: 3600,
    expiresIn: '60m'
  });
  const json = (route: Route, status: number, body: unknown) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

  // API calls only: page loads and assets still come from the server under test
  await page.route('**/*', async route => {
    const request = route.request();
    if (!['xhr', 'fetch'].includes(request.resourceType()) || request.url().startsWith('https://js.stripe.com')) {
      return route.fallback();
    }
    const { pathname } = new URL(request.url());
    const method = request.method();

    if (pathname === '/products' && method === 'GET') {
      return json(route, 200, options.products);
    }
    if (pathname === '/orders/stream') {
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body: '' });
    }
    if (pathname === '/orders/my') {
      return json(route, 200, {
        content: [], currentPage: 0, totalPages: 0, totalElements: 0, hasNext: false, hasPrevious: false
      });
    }
    if (pathname === '/orders' && method === 'POST') {
      const body = request.postDataJSON();
      api.orderRequests.push(body);
      const id = 100 + api.orderRequests.length;
      const total = body.items.reduce((sum: number, i: { price: number; quantity: number }) => sum + i.price * i.quantity, 0);
      return json(route, 200, {
        id, productName: body.items[0].productName, quantity: body.items[0].quantity, price: body.items[0].price,
        totalAmount: total, status: 'PENDING', userEmail: USER_EMAIL, createdAt: new Date().toISOString(),
        paymentIntentId: body.paymentIntentId, items: []
      });
    }
    const orderById = pathname.match(/^\/orders\/(\d+)$/);
    if (orderById) {
      return json(route, 200, {
        id: Number(orderById[1]), productName: 'x', quantity: 1, price: 1, totalAmount: 1, status: 'CONFIRMED',
        userEmail: USER_EMAIL, createdAt: new Date().toISOString(), items: []
      });
    }
    if (pathname === '/cart') {
      if (method === 'DELETE') {
        cartItems = [];
        return route.fulfill({ status: 204 });
      }
      return json(route, 200, cart());
    }
    if (pathname === '/cart/items' && method === 'POST') {
      cartItems = [...cartItems, request.postDataJSON()];
      return json(route, 200, cart());
    }
    if (pathname === '/payments/create-intent') {
      api.createIntentRequests.push(request.postDataJSON());
      const answer = responses.shift();
      if (answer) {
        return json(route, answer.status, answer.body);
      }
      intents++;
      return json(route, 200, {
        clientSecret: `pi_e2e_${intents}_secret_e2e`,
        paymentIntentId: `pi_e2e_${intents}`,
        status: 'requires_payment_method',
        publishableKey: 'pk_test_e2e'
      });
    }
    return json(route, 200, {});
  });
  return api;
}

// The gateway's answer when its time limit on /payments/** ran out before the /fallback/payments fix
export const GATEWAY_TIMEOUT_404: StubResponse = {
  status: 404,
  body: { timestamp: '2026-10-23T14:53:49.338+00:00', path: '/fallback/payments', status: 404, error: 'Not Found' }
};

export interface FakeStripeOptions {
  // results of stripe.confirmPayment, in order: 'declined' or a PaymentIntent status; 'succeeded' after them
  confirmResults?: string[];
  // delay before the Stripe.js script is served, as on a cold cache
  scriptDelayMs?: number;
}

// Serves a fake Stripe.js: elements().create('payment').mount() renders a placeholder and emits 'ready', and
// confirmPayment answers from confirmResults. window.__fakeStripe records what the page did.
export async function fakeStripe(page: Page, options: FakeStripeOptions = {}): Promise<void> {
  await page.addInitScript(confirmResults => {
    (window as any).__fakeStripe = { confirmResults, mounts: 0, confirms: [] };
  }, options.confirmResults ?? []);
  await page.route('https://js.stripe.com/**', async route => {
    if (options.scriptDelayMs) {
      await new Promise(resolve => setTimeout(resolve, options.scriptDelayMs));
    }
    await route.fulfill({ status: 200, contentType: 'application/javascript', body: FAKE_STRIPE_JS });
  });
}

const FAKE_STRIPE_JS = `
(() => {
  const state = window.__fakeStripe;
  window.Stripe = function () {
    return {
      elements(options) {
        const clientSecret = options.clientSecret;
        return {
          create(type) {
            const handlers = {};
            let node = null;
            return {
              on(event, handler) { (handlers[event] = handlers[event] || []).push(handler); },
              mount(selector) {
                const container = document.querySelector(selector);
                if (!container) {
                  throw new Error('IntegrationError: The selector you specified (' + selector
                    + ') applies to no DOM elements that are currently on the page.');
                }
                node = document.createElement('div');
                node.setAttribute('data-testid', 'fake-card');
                node.dataset.clientSecret = clientSecret;
                node.textContent = 'Card 4242 4242 4242 4242';
                container.appendChild(node);
                state.mounts++;
                setTimeout(() => (handlers.ready || []).forEach(h => h({ elementType: type })), 50);
              },
              destroy() { if (node) { node.remove(); node = null; } }
            };
          }
        };
      },
      async confirmPayment(args) {
        const clientSecret = args.elements && document.querySelector('[data-testid=fake-card]').dataset.clientSecret;
        state.confirms.push(clientSecret);
        const result = state.confirmResults.shift() || 'succeeded';
        if (result === 'declined') {
          return { error: { type: 'card_error', code: 'card_declined', message: 'Your card was declined.' } };
        }
        return { paymentIntent: { id: clientSecret.split('_secret')[0], status: result } };
      }
    };
  };
})();
`;
