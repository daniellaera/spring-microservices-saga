import { Page, Route } from '@playwright/test';
import { readFileSync } from 'node:fs';

// An ADMIN with audit-trail-service's API stubbed in the browser (page.route): GET /audit filtered and paginated the
// way the service does it, and GET /audit/orders/{id}/timeline with its rule for events recorded before the
// correlation id. Every request is recorded.

const base64url = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
// Unsigned token: the app only decodes it
const ADMIN_TOKEN = `${base64url({ alg: 'HS256' })}.${base64url({
  sub: 'admin@example.com', role: 'ADMIN', exp: 4102444800
})}.signature`;

export interface AuditEvent {
  id: number;
  eventType: string;
  userEmail: string;
  entityType: string;
  entityId: string;
  payload: string;
  serviceName: string;
  createdAt: string;
  recordedAt?: string;
  correlationId?: string | null;
}

// The events the stub gateway serves for /audit (e2e/stack): recorded before the correlation id existed
export const FIXTURE_EVENTS: AuditEvent[] = JSON.parse(readFileSync('e2e/stack/audit-events.json', 'utf8')).content;

export interface AuditApi {
  listRequests: URLSearchParams[];
  timelineRequests: string[];
}

export interface AuditApiOptions {
  events?: AuditEvent[];
  // how many GET /audit requests fail (503) before the stub answers
  listFailures?: number;
  // the answer for an order's timeline, instead of the one computed from the events
  timelineAnswer?: (orderId: string) => { status: number; body: unknown } | undefined;
}

export async function loginAsAdmin(page: Page): Promise<void> {
  await page.addInitScript(token => {
    localStorage.setItem('token', token);
    localStorage.setItem('refreshToken', 'refresh-token');
    localStorage.setItem('userEmail', 'admin@example.com');
    localStorage.setItem('userRole', 'ADMIN');
  }, ADMIN_TOKEN);
}

export async function stubAuditApi(page: Page, options: AuditApiOptions = {}): Promise<AuditApi> {
  const events = options.events ?? FIXTURE_EVENTS;
  const api: AuditApi = { listRequests: [], timelineRequests: [] };
  let listFailures = options.listFailures ?? 0;
  const json = (route: Route, status: number, body: unknown) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

  await page.route('**/audit**', async route => {
    const request = route.request();
    if (!['xhr', 'fetch'].includes(request.resourceType())) {
      return route.fallback();
    }
    const url = new URL(request.url());
    if (url.pathname === '/audit') {
      api.listRequests.push(url.searchParams);
      if (listFailures > 0) {
        listFailures--;
        return json(route, 503, { status: '503', message: 'Audit trail service is currently unavailable.' });
      }
      return json(route, 200, page_(search(events, url.searchParams), url.searchParams));
    }
    const timeline = url.pathname.match(/^\/audit\/orders\/(\d+)\/timeline$/);
    if (timeline) {
      api.timelineRequests.push(timeline[1]);
      const answer = options.timelineAnswer?.(timeline[1]);
      return answer ? json(route, answer.status, answer.body) : json(route, 200, timelineOf(events, timeline[1]));
    }
    return json(route, 404, {});
  });
  return api;
}

// newest first, ties by id
const newestFirst = (a: AuditEvent, b: AuditEvent) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id;

function search(events: AuditEvent[], params: URLSearchParams): AuditEvent[] {
  const q = params.get('q')?.toLowerCase();
  const type = params.get('eventType');
  const service = params.get('serviceName');
  return events
    .filter(e => !q || [e.userEmail, e.entityType, e.entityId, e.serviceName, e.correlationId ?? '']
      .some(v => v.toLowerCase().includes(q)) || (!!q && e.eventType.toLowerCase().includes(q)))
    .filter(e => !type || e.eventType === type)
    .filter(e => !service || e.serviceName === service)
    .sort(newestFirst);
}

function page_(matching: AuditEvent[], params: URLSearchParams) {
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 50);
  return {
    content: matching.slice(page * size, (page + 1) * size),
    totalElements: matching.length,
    totalPages: Math.ceil(matching.length / size),
    number: page,
    size
  };
}

function timelineOf(events: AuditEvent[], orderId: string): AuditEvent[] {
  return events
    .filter(e => e.correlationId === orderId || (!e.correlationId && (
      (['ORDER', 'PAYMENT'].includes(e.entityType) && e.entityId === orderId && e.eventType !== 'PAYMENT_INITIATED')
      || (e.eventType === 'STOCK_RESTORED' && e.payload.includes(`"orderId":${orderId},`)))))
    .sort((a, b) => -newestFirst(a, b));
}

// A saga event as audit-trail-service sends it since the correlation id: recordedAt is createdAt with its Z
export function sagaEvent(id: number, eventType: string, serviceName: string, recordedAt: string, orderId: number,
                          payload: object = {}): AuditEvent {
  const entityType = serviceName === 'inventory-service' ? 'PRODUCT' : serviceName === 'payment-service' ? 'PAYMENT' : 'ORDER';
  return {
    id, eventType, userEmail: 'buyer@example.com', entityType,
    entityId: entityType === 'PRODUCT' ? '3' : String(orderId), payload: JSON.stringify(payload), serviceName,
    createdAt: recordedAt.replace('Z', ''), recordedAt, correlationId: String(orderId)
  };
}
