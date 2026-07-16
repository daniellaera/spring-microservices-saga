import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AuditEventDto {
  id: number;
  eventType: string;
  userEmail: string;
  entityType: string;
  entityId: string;
  payload: string;
  serviceName: string;
  createdAt: string;
}

export interface PagedAuditResponse {
  content: AuditEventDto[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private http = inject(HttpClient);

  getAll(page: number = 0, size: number = 50): Observable<PagedAuditResponse> {
    return this.http.get<PagedAuditResponse>(`/audit?page=${page}&size=${size}`);
  }

  getByUser(email: string): Observable<AuditEventDto[]> {
    return this.http.get<AuditEventDto[]>(`/audit/user/${email}`);
  }

  getByOrder(orderId: string): Observable<AuditEventDto[]> {
    return this.http.get<AuditEventDto[]>(`/audit/order/${orderId}`);
  }
}
