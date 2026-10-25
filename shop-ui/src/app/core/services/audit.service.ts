import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AuditEventDto {
  id: number;
  eventType: string;
  userEmail: string;
  entityType: string;
  entityId: string;
  payload: string;
  serviceName: string;
  // UTC without an offset (as the API has always sent it); occurredAt is the same instant with its Z
  createdAt: string;
  occurredAt?: string;
  // the order id of a saga event; absent before audit-trail-service recorded it
  correlationId?: string | null;
}

export interface PagedAuditResponse {
  content: AuditEventDto[];
  totalElements: number;
  totalPages: number;
  number: number;
}

// GET /audit's server-side filters; empty ones are not sent
export interface AuditQuery {
  page: number;
  size: number;
  q?: string;
  eventType?: string;
  serviceName?: string;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private http = inject(HttpClient);

  search(query: AuditQuery): Observable<PagedAuditResponse> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const name of ['q', 'eventType', 'serviceName'] as const) {
      const value = query[name]?.trim();
      if (value) {
        params = params.set(name, value);
      }
    }
    return this.http.get<PagedAuditResponse>('/audit', { params });
  }

  // one order's saga, oldest first
  getTimeline(orderId: number): Observable<AuditEventDto[]> {
    return this.http.get<AuditEventDto[]>(`/audit/orders/${orderId}/timeline`);
  }

  getByUser(email: string): Observable<AuditEventDto[]> {
    return this.http.get<AuditEventDto[]>(`/audit/user/${email}`);
  }

  getByOrder(orderId: string): Observable<AuditEventDto[]> {
    return this.http.get<AuditEventDto[]>(`/audit/order/${orderId}`);
  }
}
