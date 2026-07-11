import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface CartItem {
  productName: string;
  quantity: number;
  price: number;
}

export interface CartResponse {
  userEmail: string;
  items: CartItem[];
  total: number;
  ttlSeconds: number;
  expiresIn: string;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private http = inject(HttpClient);

  cart = signal<CartResponse | null>(null);
  itemCount = signal(0);
  cartVisible = signal(false);

  toggleCart(): void {
    this.cartVisible.update(v => !v);
  }

  getCart(): Observable<CartResponse> {
    return this.http.get<CartResponse>('/cart').pipe(
      tap(cart => {
        this.cart.set(cart);
        this.itemCount.set(cart.items.length);
      })
    );
  }

  addItem(productName: string, quantity: number, price: number): Observable<CartResponse> {
    return this.http.post<CartResponse>('/cart/items', { productName, quantity, price }).pipe(
      tap(cart => {
        this.cart.set(cart);
        this.itemCount.set(cart.items.length);
      })
    );
  }

  updateQuantity(productName: string, quantity: number): Observable<CartResponse> {
    return this.http.put<CartResponse>(
      `/cart/items/${encodeURIComponent(productName)}`,
      null,
      { params: { quantity: quantity.toString() } }
    ).pipe(
      tap(cart => {
        this.cart.set(cart);
        this.itemCount.set(cart.items.length);
      })
    );
  }

  removeItem(productName: string): Observable<CartResponse> {
    return this.http.delete<CartResponse>(`/cart/items/${encodeURIComponent(productName)}`).pipe(
      tap(cart => {
        this.cart.set(cart);
        this.itemCount.set(cart.items.length);
      })
    );
  }

  clearCart(): Observable<void> {
    return this.http.delete<void>('/cart').pipe(
      tap(() => {
        this.cart.set(null);
        this.itemCount.set(0);
      })
    );
  }
}
