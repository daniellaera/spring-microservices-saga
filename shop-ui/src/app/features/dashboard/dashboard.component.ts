import { Component, NgZone, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Observable, Subject, interval, timer } from 'rxjs';
import { filter, switchMap, take, takeUntil } from 'rxjs/operators';
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
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';
import { OrderService, OrderDto, PagedResponse } from '../../core/services/order.service';
import { ProductService, ProductDto } from '../../core/services/product.service';
import { NotificationService } from '../../core/services/notification.service';
import { PaymentService, PaymentIntentResponse } from '../../core/services/payment.service';
import { CartService } from '../../core/services/cart.service';
import { environment } from '../../../environments/environment';
import { handleHttpError } from '../../core/utils/error-handler.util';

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
  private static readonly SSE_RETRY_DELAY_MS = 3000;
  private static readonly STRIPE_MOUNT_DELAY_MS = 300;
  private static readonly POLL_INTERVAL_MS = 2000;
  private static readonly POLL_TIMEOUT_MS = 30000;
  private static readonly ORDER_STATUS_CONFIRMED = 'CONFIRMED';
  private static readonly ORDER_STATUS_CANCELLED = 'CANCELLED';
  private static readonly ORDER_STATUS_PENDING = 'PENDING';

  private destroy$ = new Subject<void>();

  private authService = inject(AuthService);
  private orderService = inject(OrderService);
  private productService = inject(ProductService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private notificationService = inject(NotificationService);
  private paymentService = inject(PaymentService);
  private zone = inject(NgZone);
  private router = inject(Router);
  cartService = inject(CartService);

  environment = environment;

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
  private stripeMountTimeout: ReturnType<typeof setTimeout> | null = null;
  private pendingSSEUpdates = new Map<number, string>();
  private sseConfirmedOrders = new Set<number>();

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
    this.cartService.getCart().pipe(takeUntil(this.destroy$)).subscribe({
      error: (err) => handleHttpError(err, this.messageService, 'Could not load cart')
    });
  }

  addToCart(product: ProductDto): void {
    this.cartService.addItem(product.name, this.quantity(), product.price).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: '🛒 Added to cart',
          detail: `${product.name} added`,
          life: 2000
        });
        this.cartService.cartVisible.set(true);
      },
      error: (err) => handleHttpError(err, this.messageService, 'Could not add to cart')
    });
  }

  removeFromCart(productName: string): void {
    this.cartService.removeItem(productName).pipe(takeUntil(this.destroy$)).subscribe({
      error: (err) => handleHttpError(err, this.messageService, 'Could not remove item from cart')
    });
  }

  updateCartQuantity(productName: string, quantity: number): void {
    this.cartService.updateQuantity(productName, quantity).pipe(takeUntil(this.destroy$)).subscribe({
      error: (err) => handleHttpError(err, this.messageService, 'Could not update cart quantity')
    });
  }

  clearCart(): void {
    this.confirmationService.confirm({
      message: 'Remove all items from your cart?',
      header: 'Clear Cart',
      icon: 'pi pi-trash',
      acceptLabel: 'Clear',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.cartService.clearCart().pipe(takeUntil(this.destroy$)).subscribe({
          next: () => this.cartService.cartVisible.set(false),
          error: (err) => handleHttpError(err, this.messageService, 'Could not clear cart')
        });
      }
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
    ).pipe(take(1)).subscribe({
      next: async (intent) => {
        this.currentPaymentIntent = intent;
        this.cartCheckoutIntent = intent;
        this.showPaymentDialog = true;
        this.cartService.cartVisible.set(false);
        this.orderLoading = false;
        // STRIPE_MOUNT_DELAY_MS: p-dialog needs one animation
        // frame to render #stripe-payment-element in DOM
        // before Stripe Elements can mount to it
        if (this.stripeMountTimeout) clearTimeout(this.stripeMountTimeout);
        this.stripeMountTimeout = setTimeout(() => this.mountStripeCard(intent), DashboardComponent.STRIPE_MOUNT_DELAY_MS);
      },
      error: (err) => {
        handleHttpError(err, this.messageService, 'Payment setup failed');
        this.orderLoading = false;
      }
    });
  }

  loadOrders(page: number = 0): void {
    this.loading = true;
    const obs: Observable<PagedResponse<OrderDto>> = this.isAdmin()
      ? this.orderService.getAllOrders(page)
      : this.orderService.getMyOrders(page);

    obs.pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.orders = data.content;
        this.currentPage = data.currentPage;
        this.totalPages = data.totalPages;
        this.totalElements = data.totalElements;
        this.hasNext = data.hasNext;
        this.hasPrevious = data.hasPrevious;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        handleHttpError(err, this.messageService, 'Could not load orders');
      }
    });
  }

  loadProducts(): void {
    this.productService.getAll().pipe(takeUntil(this.destroy$)).subscribe({
      next: products => {
        this.products = products.filter(p => p.quantity > 0);
        if (products.length > 0) {
          this.selectedProduct.set(products[0]);
        }
      },
      error: (err) => handleHttpError(err, this.messageService, 'Could not load products')
    });
  }

  // Step 1: user clicks "Place order" → create PaymentIntent and open dialog
  async openPaymentDialog(): Promise<void> {
    const product = this.selectedProduct();
    if (!product) return;

    this.isCartCheckout = false;
    this.orderLoading = true;
    const amountInCents = Math.round(product.price * this.quantity() * 100);

    this.paymentService.createPaymentIntent(amountInCents, 'eur', product.name).pipe(take(1)).subscribe({
      next: async (intent) => {
        this.currentPaymentIntent = intent;
        this.showPaymentDialog = true;
        this.orderLoading = false;
        // STRIPE_MOUNT_DELAY_MS: p-dialog needs one animation
        // frame to render #stripe-payment-element in DOM
        // before Stripe Elements can mount to it
        if (this.stripeMountTimeout) clearTimeout(this.stripeMountTimeout);
        this.stripeMountTimeout = setTimeout(() => this.mountStripeCard(intent), DashboardComponent.STRIPE_MOUNT_DELAY_MS);
      },
      error: (err) => {
        handleHttpError(err, this.messageService, 'Payment setup failed');
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
    ).pipe(take(1)).subscribe({
      next: (newOrder: OrderDto) => {
        this.showPaymentDialog = false;
        this.paymentLoading = false;
        this.orders = [newOrder, ...this.orders];
        this.totalElements++;
        this.applyPendingSSEUpdate(newOrder.id);
        this.pollUntilConfirmed(newOrder.id, newOrder.productName);
        this.quantity.set(1);
        this.loadProducts();
        this.cartService.clearCart().pipe(take(1)).subscribe({
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Failed',
              detail: 'Order placed, but cart could not be cleared',
              life: 3000
            });
          }
        });
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

    this.orderService.createOrder(items, paymentIntentId).pipe(take(1)).subscribe({
      next: (newOrder: OrderDto) => {
        this.showPaymentDialog = false;
        this.paymentLoading = false;
        this.isCartCheckout = false;
        this.orders = [newOrder, ...this.orders];
        this.totalElements++;
        this.applyPendingSSEUpdate(newOrder.id);
        this.pollUntilConfirmed(newOrder.id, newOrder.productName);
        this.cartService.clearCart().pipe(take(1)).subscribe({
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Failed',
              detail: 'Order placed, but cart could not be cleared',
              life: 3000
            });
          }
        });
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
        this.scheduleSSEReconnect();
        return;
      }
      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      const read = (): void => {
        reader.read().then(({ done, value }) => {
          if (done) {
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
                      this.sseConfirmedOrders.add(data.orderId);
                      this.showOrderNotification(data.orderId, data.status, order.productName);
                    }
                  } else {
                    this.pendingSSEUpdates.set(data.orderId, data.status);
                    this.pollUntilConfirmed(data.orderId);
                  }
                });
              } catch {}
            }
          }
          read();
        }).catch(err => {
          if (err.name !== 'AbortError') {
            this.scheduleSSEReconnect();
          }
        });
      };
      read();
    })
    .catch(err => {
      if (err.name !== 'AbortError') {
        this.scheduleSSEReconnect();
      }
    });
  }

  scheduleSSEReconnect(): void {
    if (this.sseRetryTimeout) clearTimeout(this.sseRetryTimeout);
    this.sseRetryTimeout = setTimeout(() => this.connectSSE(), DashboardComponent.SSE_RETRY_DELAY_MS);
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
      } else if (!order) {
        this.pollUntilConfirmed(orderId);
      }
    }
  }

  private pollUntilConfirmed(orderId: number, productName?: string): void {
    const stop$ = new Subject<void>();

    interval(DashboardComponent.POLL_INTERVAL_MS).pipe(
      switchMap(() => this.orderService.getOrderById(orderId)),
      filter(order => order.status !== DashboardComponent.ORDER_STATUS_PENDING),
      take(1),
      takeUntil(stop$),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (order) => {
        this.zone.run(() => {
          const exists = this.orders.some(o => o.id === orderId);
          this.orders = exists
            ? this.orders.map(o => (o.id === orderId ? { ...order } : o))
            : [order, ...this.orders];
          // only notify if SSE didn't already handle it
          if (!this.sseConfirmedOrders.has(orderId)) {
            this.showOrderNotification(orderId, order.status, productName ?? order.productName);
          }
          this.sseConfirmedOrders.delete(orderId);
          stop$.next();
          stop$.complete();
        });
      },
      error: () => stop$.complete()
    });

    // POLL_TIMEOUT_MS: safety net so an unresolved order doesn't poll forever
    timer(DashboardComponent.POLL_TIMEOUT_MS).pipe(take(1), takeUntil(this.destroy$)).subscribe(() => {
      stop$.next();
      stop$.complete();
    });
  }

  ngOnDestroy(): void {
    this.abortController?.abort();
    if (this.sseRetryTimeout) clearTimeout(this.sseRetryTimeout);
    if (this.stripeMountTimeout) clearTimeout(this.stripeMountTimeout);
    this.pendingSSEUpdates.clear();
    this.sseConfirmedOrders.clear();
    this.destroy$.next();
    this.destroy$.complete();
  }

  showOrderNotification(orderId: number, status: string, productName: string): void {
    if (status === DashboardComponent.ORDER_STATUS_CONFIRMED) {
      this.messageService.add({
        severity: 'success',
        summary: '✅ Order confirmed!',
        detail: `${productName} has been confirmed`,
        life: 5000,
        sticky: false
      });
      this.notificationService.add({
        type: 'success',
        title: '✅ Order confirmed',
        message: `${productName} has been confirmed`,
        orderId
      });
    } else if (status === DashboardComponent.ORDER_STATUS_CANCELLED) {
      this.messageService.add({
        severity: 'error',
        summary: '❌ Order cancelled',
        detail: `${productName} could not be processed — stock restored`,
        life: 6000,
        sticky: false
      });
      this.notificationService.add({
        type: 'error',
        title: '❌ Order cancelled',
        message: `${productName} could not be processed`,
        orderId
      });
    }
  }

  getStatusSeverity(status: string): 'success' | 'danger' | 'warn' | 'info' {
    switch (status) {
      case DashboardComponent.ORDER_STATUS_CONFIRMED: return 'success';
      case DashboardComponent.ORDER_STATUS_CANCELLED: return 'danger';
      case DashboardComponent.ORDER_STATUS_PENDING: return 'warn';
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
