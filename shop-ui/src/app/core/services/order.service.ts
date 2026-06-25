import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OrderDto {
  id: number;
  productName: string;
  quantity: number;
  price: number;
  totalAmount: number;
  status: string;
  userEmail: string;
  createdAt: string;
  paymentIntentId?: string;
}

export interface PagedResponse<T> {
  content: T[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private http: HttpClient) {}

  getMyOrders(page = 0, size = 10): Observable<PagedResponse<OrderDto>> {
    return this.http.get<PagedResponse<OrderDto>>(`/orders/my?page=${page}&size=${size}`);
  }

  getAllOrders(page = 0, size = 10): Observable<PagedResponse<OrderDto>> {
    return this.http.get<PagedResponse<OrderDto>>(`/orders?page=${page}&size=${size}`);
  }

  createOrder(productName: string, quantity: number, price: number, paymentIntentId: string = ''): Observable<OrderDto> {
    return this.http.post<OrderDto>('/orders', { productName, quantity, price, paymentIntentId });
  }
}
