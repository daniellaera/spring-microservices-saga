import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { ToastModule } from 'primeng/toast';
import { SkeletonModule } from 'primeng/skeleton';
import { DividerModule } from 'primeng/divider';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';
import { OrderService, OrderDto, PagedResponse } from '../../core/services/order.service';
import { ProductService, ProductDto } from '../../core/services/product.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe,
    TableModule, ButtonModule, TagModule,
    CardModule, SelectModule, InputNumberModule,
    ToastModule, SkeletonModule, DividerModule
  ],
  providers: [MessageService],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private orderService = inject(OrderService);
  private productService = inject(ProductService);
  private messageService = inject(MessageService);

  isAdmin = this.authService.isAdmin;
  currentEmail = this.authService.currentEmail;

  orders: OrderDto[] = [];
  products: ProductDto[] = [];
  loading = true;
  orderLoading = false;

  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  hasNext = false;
  hasPrevious = false;

  private abortController: AbortController | null = null;
  private sseRetryTimeout: ReturnType<typeof setTimeout> | null = null;

  // place order form
  selectedProduct = signal<ProductDto | null>(null);
  quantity = signal(1);
  estimatedTotal = computed(() => {
    const product = this.selectedProduct();
    const qty = this.quantity();
    if (!product) return '0.00';
    return (product.price * qty).toFixed(2);
  });

  ngOnInit(): void {
    this.loadProducts();
    this.loadOrders(0);
    this.connectSSE();
  }

  loadOrders(page: number = 0): void {
    this.loading = true;
    const obs: Observable<PagedResponse<OrderDto>> = this.isAdmin()
      ? this.orderService.getAllOrders(page)
      : this.orderService.getMyOrders(page);

    obs.subscribe({
      next: (data) => {
        console.log('Orders loaded:', data);
        this.orders = data.content;
        this.currentPage = data.currentPage;
        this.totalPages = data.totalPages;
        this.totalElements = data.totalElements;
        this.hasNext = data.hasNext;
        this.hasPrevious = data.hasPrevious;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  loadProducts(): void {
    this.productService.getAll().subscribe({
      next: products => {
        this.products = products;
        if (products.length > 0) {
          this.selectedProduct.set(products[0]);
        }
      },
      error: () => {}
    });
  }

  placeOrder(): void {
    const product = this.selectedProduct();
    if (!product) return;
    this.orderLoading = true;
    this.orderService.createOrder(
      product.name,
      this.quantity(),
      product.price
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Order placed',
          detail: 'Your order is being processed',
          life: 3000
        });
        this.loadOrders(this.currentPage);
        this.quantity.set(1);
        this.orderLoading = false;
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Failed',
          detail: err?.error?.message || 'Could not place order',
          life: 4000
        });
        this.orderLoading = false;
      }
    });
  }

  connectSSE(): void {
    const token = localStorage.getItem('token');
    if (!token) return;

    if (this.abortController) {
      this.abortController.abort();
    }
    this.abortController = new AbortController();

    fetch('/orders/stream', {
      headers: {
        'Authorization': 'Bearer ' + token,
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache'
      },
      signal: this.abortController.signal
    })
    .then(response => {
      if (!response.ok) {
        console.warn('SSE response not ok:', response.status);
        this.scheduleSSEReconnect();
        return;
      }
      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      const read = (): void => {
        reader.read().then(({ done, value }) => {
          if (done) {
            console.warn('SSE stream closed — reconnecting');
            this.scheduleSSEReconnect();
            return;
          }
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() ?? '';

          for (const line of lines) {
            if (line.startsWith('data:')) {
              try {
                const data = JSON.parse(line.slice(5).trim());
                const order = this.orders.find(o => o.id === data.orderId);
                if (order) {
                  order.status = data.status;
                  console.log('SSE update received:', data);
                }
              } catch {}
            }
          }
          read();
        }).catch(err => {
          if (err.name !== 'AbortError') {
            console.warn('SSE read error — reconnecting:', err.message);
            this.scheduleSSEReconnect();
          }
        });
      };
      read();
    })
    .catch(err => {
      if (err.name !== 'AbortError') {
        console.warn('SSE connect error — reconnecting:', err.message);
        this.scheduleSSEReconnect();
      }
    });
  }

  scheduleSSEReconnect(): void {
    if (this.sseRetryTimeout) clearTimeout(this.sseRetryTimeout);
    this.sseRetryTimeout = setTimeout(() => this.connectSSE(), 3000);
  }

  ngOnDestroy(): void {
    this.abortController?.abort();
    if (this.sseRetryTimeout) clearTimeout(this.sseRetryTimeout);
  }

  getStatusSeverity(status: string): 'success' | 'danger' | 'warn' | 'info' {
    switch (status) {
      case 'CONFIRMED': return 'success';
      case 'CANCELLED': return 'danger';
      case 'PENDING': return 'warn';
      default: return 'info';
    }
  }

  prevPage(): void {
    if (this.hasPrevious) this.loadOrders(this.currentPage - 1);
  }

  nextPage(): void {
    if (this.hasNext) this.loadOrders(this.currentPage + 1);
  }

  goToPage(page: number): void {
    this.loadOrders(page);
  }
}
