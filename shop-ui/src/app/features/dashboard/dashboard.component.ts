import { Component, NgZone, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
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
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { BadgeModule } from 'primeng/badge';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';
import { OrderService, OrderDto, PagedResponse } from '../../core/services/order.service';
import { ProductService, ProductDto } from '../../core/services/product.service';
import { NotificationService } from '../../core/services/notification.service';
import { PaymentService, PaymentIntentResponse } from '../../core/services/payment.service';
import { CartService } from '../../core/services/cart.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, RouterModule,
    TableModule, ButtonModule, TagModule,
    CardModule, SelectModule, InputNumberModule,
    ToastModule, SkeletonModule, DividerModule, DialogModule,
    DrawerModule, BadgeModule
  ],
  providers: [MessageService],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private orderService = inject(OrderService);
  private productService = inject(ProductService);
  private messageService = inject(MessageService);
  private notificationService = inject(NotificationService);
  private paymentService = inject(PaymentService);
  private zone = inject(NgZone);
  private router = inject(Router);
  cartService = inject(CartService);

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
  private pendingSSEUpdates = new Map<number, string>();

  // payment dialog state
  showPaymentDialog = false;
  paymentLoading = false;
  currentPaymentIntent: PaymentIntentResponse | null = null;
  cartCheckoutIntent: PaymentIntentResponse | null = null;
  isCartCheckout = false;
  private stripeElements: any = null;
  private cardElement: any = null;

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
    if (this.authService.isTokenExpired()) {
      this.authService.logout();
      this.router.navigate(['/login']);
      return;
    }
    this.loadProducts();
    this.loadOrders(0);
    this.loadCart();
    this.connectSSE();
  }

  loadCart(): void {
    this.cartService.getCart().subscribe();
  }

  addToCart(product: ProductDto): void {
    this.cartService.addItem(product.name, this.quantity(), product.price).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: '🛒 Added to cart',
          detail: `${product.name} added`,
          life: 2000
        });
        this.cartService.cartVisible.set(true);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Failed',
          detail: 'Could not add to cart',
          life: 3000
        });
      }
    });
  }

  removeFromCart(productName: string): void {
    this.cartService.removeItem(productName).subscribe();
  }

  updateCartQuantity(productName: string, quantity: number): void {
    this.cartService.updateQuantity(productName, quantity).subscribe();
  }

  clearCart(): void {
    this.cartService.clearCart().subscribe(() => {
      this.cartService.cartVisible.set(false);
    });
  }

  checkoutCart(): void {
    const cart = this.cartService.cart();
    if (!cart || cart.items.length === 0) return;

    this.isCartCheckout = true;
    this.orderLoading = true;
    const amountInCents = Math.round(
      cart.items.reduce((sum, item) => sum + (item.price * item.quantity), 0) * 100
    );

    this.paymentService.createPaymentIntent(
      amountInCents,
      'eur',
      cart.items.map(i => i.productName).join(', ')
    ).subscribe({
      next: async (intent) => {
        this.currentPaymentIntent = intent;
        this.cartCheckoutIntent = intent;
        this.showPaymentDialog = true;
        this.cartService.cartVisible.set(false);
        this.orderLoading = false;
        setTimeout(() => this.mountStripeCard(intent), 300);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Payment setup failed',
          detail: 'Could not initialize payment',
          life: 4000
        });
        this.orderLoading = false;
      }
    });
  }

  loadOrders(page: number = 0): void {
    this.loading = true;
    const obs: Observable<PagedResponse<OrderDto>> = this.isAdmin()
      ? this.orderService.getAllOrders(page)
      : this.orderService.getMyOrders(page);

    obs.subscribe({
      next: (data) => {
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
        this.products = products.filter(p => p.quantity > 0);
        if (products.length > 0) {
          this.selectedProduct.set(products[0]);
        }
      },
      error: () => {}
    });
  }

  // Step 1: user clicks "Place order" → create PaymentIntent and open dialog
  async openPaymentDialog(): Promise<void> {
    const product = this.selectedProduct();
    if (!product) return;

    this.isCartCheckout = false;
    this.orderLoading = true;
    const amountInCents = Math.round(product.price * this.quantity() * 100);

    this.paymentService.createPaymentIntent(amountInCents, 'eur', product.name).subscribe({
      next: async (intent) => {
        this.currentPaymentIntent = intent;
        this.showPaymentDialog = true;
        this.orderLoading = false;
        setTimeout(() => this.mountStripeCard(intent), 300);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Payment setup failed',
          detail: 'Could not initialize payment',
          life: 4000
        });
        this.orderLoading = false;
      }
    });
  }

  async mountStripeCard(intent: PaymentIntentResponse): Promise<void> {
    const stripe = await this.paymentService.getStripe(intent.publishableKey);
    if (!stripe) return;

    this.stripeElements = stripe.elements({ clientSecret: intent.clientSecret });
    this.cardElement = this.stripeElements.create('payment');
    this.cardElement.mount('#stripe-payment-element');
  }

  // Step 2: user enters card and clicks Pay → confirm with Stripe, then create order
  async confirmStripePayment(): Promise<void> {
    if (!this.stripeElements || !this.currentPaymentIntent) return;
    this.paymentLoading = true;

    const stripe = await this.paymentService.getStripe(this.currentPaymentIntent.publishableKey);
    if (!stripe) return;

    const { error, paymentIntent } = await stripe.confirmPayment({
      elements: this.stripeElements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required'
    });

    if (error) {
      this.messageService.add({
        severity: 'error',
        summary: 'Payment failed',
        detail: error.message || 'Card was declined',
        life: 5000
      });
      this.paymentLoading = false;
      return;
    }

    if (paymentIntent?.status === 'succeeded') {
      if (this.isCartCheckout) {
        this.placeCartOrdersAfterPayment(paymentIntent.id);
      } else {
        this.placeOrderAfterPayment(paymentIntent.id);
      }
    }
  }

  // Step 3: payment confirmed → create the order with paymentIntentId
  placeOrderAfterPayment(paymentIntentId: string): void {
    const product = this.selectedProduct();
    if (!product) return;

    this.orderService.createOrder(
      [{ productName: product.name, quantity: this.quantity(), price: product.price }],
      paymentIntentId
    ).subscribe({
      next: (newOrder: OrderDto) => {
        this.showPaymentDialog = false;
        this.paymentLoading = false;
        this.orders = [newOrder, ...this.orders];
        this.totalElements++;
        this.applyPendingSSEUpdate(newOrder.id);
        this.quantity.set(1);
        this.loadProducts();
        this.cartService.clearCart().subscribe();
        this.cartService.cartVisible.set(false);
        this.messageService.add({
          severity: 'info',
          summary: '⏳ Order submitted',
          detail: 'Payment confirmed — processing your order...',
          life: 3000
        });
        this.cardElement?.destroy();
        this.cardElement = null;
        this.stripeElements = null;
        this.currentPaymentIntent = null;
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Order creation failed',
          detail: err?.error?.message || 'Payment succeeded but order failed',
          life: 5000
        });
        this.paymentLoading = false;
      }
    });
  }

  // Step 3b: cart checkout — create ONE order containing all cart items
  placeCartOrdersAfterPayment(paymentIntentId: string): void {
    const cart = this.cartService.cart();
    if (!cart || cart.items.length === 0) return;

    const items = cart.items.map(i => ({
      productName: i.productName,
      quantity: i.quantity,
      price: i.price
    }));

    this.orderService.createOrder(items, paymentIntentId).subscribe({
      next: (newOrder: OrderDto) => {
        this.showPaymentDialog = false;
        this.paymentLoading = false;
        this.isCartCheckout = false;
        this.orders = [newOrder, ...this.orders];
        this.totalElements++;
        this.applyPendingSSEUpdate(newOrder.id);
        this.cartService.clearCart().subscribe();
        this.cartService.cartVisible.set(false);
        this.quantity.set(1);
        this.loadProducts();
        this.cardElement?.destroy();
        this.cardElement = null;
        this.stripeElements = null;
        this.currentPaymentIntent = null;
        this.cartCheckoutIntent = null;
        this.messageService.add({
          severity: 'info',
          summary: '⏳ Order submitted',
          detail: `${cart.items.length} items — processing...`,
          life: 3000
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Order creation failed',
          detail: err?.error?.message || 'Payment succeeded but order failed',
          life: 5000
        });
        this.paymentLoading = false;
      }
    });
  }

  cancelPayment(): void {
    this.showPaymentDialog = false;
    this.cardElement?.destroy();
    this.cardElement = null;
    this.stripeElements = null;
    this.currentPaymentIntent = null;
    this.cartCheckoutIntent = null;
    this.isCartCheckout = false;
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
                this.zone.run(() => {
                  const order = this.orders.find(o => o.id === data.orderId);
                  if (order) {
                    const previousStatus = order.status;
                    if (previousStatus !== data.status) {
                      this.orders = this.orders.map(o =>
                        o.id === data.orderId ? { ...o, status: data.status } : o
                      );
                      this.showOrderNotification(data.orderId, data.status, order.productName);
                    }
                  } else {
                    this.pendingSSEUpdates.set(data.orderId, data.status);
                    console.log('SSE: order not found yet, storing pending update', data.orderId, data.status);
                  }
                });
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

  applyPendingSSEUpdate(orderId: number): void {
    const pendingStatus = this.pendingSSEUpdates.get(orderId);
    if (pendingStatus) {
      this.pendingSSEUpdates.delete(orderId);
      const order = this.orders.find(o => o.id === orderId);
      if (order && order.status !== pendingStatus) {
        this.orders = this.orders.map(o =>
          o.id === orderId ? { ...o, status: pendingStatus } : o
        );
        this.showOrderNotification(orderId, pendingStatus, order.productName);
        console.log('SSE: applied pending update for order', orderId, pendingStatus);
      }
    }
  }

  ngOnDestroy(): void {
    this.abortController?.abort();
    if (this.sseRetryTimeout) clearTimeout(this.sseRetryTimeout);
    this.pendingSSEUpdates.clear();
  }

  showOrderNotification(orderId: number, status: string, productName: string): void {
    if (status === 'CONFIRMED') {
      this.messageService.add({
        severity: 'success',
        summary: '✅ Order confirmed!',
        detail: `${productName} has been confirmed`,
        life: 5000,
        sticky: false
      });
    } else if (status === 'CANCELLED') {
      this.messageService.add({
        severity: 'error',
        summary: '❌ Order cancelled',
        detail: `${productName} could not be processed — stock restored`,
        life: 6000,
        sticky: false
      });
    }
    this.notificationService.add();
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
