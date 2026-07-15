import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OrderItemDto {
  id: number;
  productName: string;
  quantity: number;
  price: number;
  totalAmount: number;
}

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
  items: OrderItemDto[];
}

export interface PagedResponse<T> {
  content: T[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface OrderItemInput {
  productName: string;
  quantity: number;
  price: number;
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

  createOrder(items: OrderItemInput[], paymentIntentId: string = ''): Observable<OrderDto> {
    return this.http.post<OrderDto>('/orders', { items, paymentIntentId });
  }

  getOrderById(orderId: number): Observable<OrderDto> {
    return this.http.get<OrderDto>(`/orders/${orderId}`);
  }
}
