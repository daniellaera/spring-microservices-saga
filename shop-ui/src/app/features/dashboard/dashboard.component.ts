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
import { HttpErrorResponse } from '@angular/common/http';
import { StripeElements, StripePaymentElement } from '@stripe/stripe-js';
import { AuthService } from '../../core/services/auth.service';
import { OrderService, OrderDto, OrderItemInput, PagedResponse } from '../../core/services/order.service';
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
  private pendingSSEUpdates = new Map<number, string>();
  private sseConfirmedOrders = new Set<number>();

  // payment dialog state
  showPaymentDialog = false;
  paymentLoading = false;
  currentPaymentIntent: PaymentIntentResponse | null = null;
  // what the open dialog pays for: the order is created from these, so it matches the intent's amount
  checkoutItems: OrderItemInput[] = [];
  checkoutTotal = 0;
  isCartCheckout = false;
  cardReady = false;
  paymentError: string | null = null;
  checkoutError: string | null = null;
  private stripeElements: StripeElements | null = null;
  private cardElement: StripePaymentElement | null = null;
  // One PaymentIntent per purchase: a retry of the same purchase (after Cancel or an error) reuses its
  // intent; a different purchase gets a new one, and a paid intent is never reused
  private openIntent: { purchaseKey: string; intent: PaymentIntentResponse } | null = null;

  // place order form
  selectedProduct = signal<ProductDto | null>(null);
  quantity = signal(1);
  estimatedTotal = computed(() => {
    const product = this.selectedProduct();
    const qty = this.quantity();
    if (!product) return '0.00';
    return (product.price * qty).toFixed(2);
  });
  // the field doesn't clamp to the stock: an order over it is blocked, so the amount is always what is shown
  exceedsStock = computed(() => {
    const product = this.selectedProduct();
    return !!product && this.quantity() > product.quantity;
  });
  canOrder = computed(() => !!this.selectedProduct() && this.quantity() >= 1 && !this.exceedsStock());

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
    if (!this.canOrder()) return;
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
    this.startCheckout(
      cart.items.map(i => ({ productName: i.productName, quantity: i.quantity, price: i.price })), true);
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
        // keep the selected product, with its fresh stock, while it is in stock; else the first one in stock
        const selected = this.selectedProduct();
        this.selectedProduct.set(this.products.find(p => p.name === selected?.name) ?? this.products[0] ?? null);
      },
      error: (err) => handleHttpError(err, this.messageService, 'Could not load products')
    });
  }

  // Step 1: user clicks "Buy now" → get the purchase's PaymentIntent and open the dialog
  openPaymentDialog(): void {
    const product = this.selectedProduct();
    if (!product || !this.canOrder()) return;
    this.startCheckout([{ productName: product.name, quantity: this.quantity(), price: product.price }], false);
  }

  private startCheckout(items: OrderItemInput[], isCartCheckout: boolean): void {
    if (this.orderLoading) return;
    this.checkoutError = null;
    const purchaseKey = JSON.stringify(items);
    if (this.openIntent?.purchaseKey === purchaseKey) {
      this.openDialog(this.openIntent.intent, items, isCartCheckout);
      return;
    }

    this.orderLoading = true;
    const amountInCents = Math.round(items.reduce((sum, i) => sum + i.price * i.quantity, 0) * 100);
    this.paymentService.createPaymentIntent(amountInCents, 'eur', items.map(i => i.productName).join(', '))
      .pipe(take(1)).subscribe({
        next: intent => {
          this.orderLoading = false;
          this.openIntent = { purchaseKey, intent };
          this.openDialog(intent, items, isCartCheckout);
        },
        error: (err: HttpErrorResponse) => {
          this.orderLoading = false;
          this.checkoutError = DashboardComponent.paymentSetupError(err);
          this.messageService.add({
            severity: 'error',
            summary: 'Payment setup failed',
            detail: this.checkoutError,
            life: 6000
          });
        }
      });
  }

  private static paymentSetupError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'The shop could not be reached. Check your connection and try again.';
    }
    // 404: what the gateway answered on a timeout before /fallback/payments existed
    if (err.status === 404 || err.status >= 500) {
      return 'The payment service did not answer. Please try again.';
    }
    return err.error?.message || err.error?.error || 'Please try again.';
  }

  // The card form mounts once the dialog is shown (onPaymentDialogShown), not after a guessed delay
  private openDialog(intent: PaymentIntentResponse, items: OrderItemInput[], isCartCheckout: boolean): void {
    this.resetPaymentDialog();
    this.currentPaymentIntent = intent;
    this.checkoutItems = items;
    this.checkoutTotal = items.reduce((sum, i) => sum + i.price * i.quantity, 0);
    this.isCartCheckout = isCartCheckout;
    if (isCartCheckout) {
      this.cartService.cartVisible.set(false);
    }
    this.showPaymentDialog = true;
  }

  // p-dialog (onShow): #stripe-payment-element is in the DOM from here on
  onPaymentDialogShown(): void {
    if (this.currentPaymentIntent) {
      this.mountStripeCard(this.currentPaymentIntent);
    }
  }

  async mountStripeCard(intent: PaymentIntentResponse): Promise<void> {
    this.destroyCard();
    this.paymentError = null;
    try {
      const stripe = await this.paymentService.getStripe(intent.publishableKey);
      if (!stripe) {
        throw new Error('Stripe.js is not available');
      }
      // the dialog may have been closed, or opened for another intent, while Stripe.js was loading
      if (this.currentPaymentIntent !== intent || !this.showPaymentDialog) return;

      this.stripeElements = stripe.elements({ clientSecret: intent.clientSecret });
      const card = this.stripeElements.create('payment');
      card.on('ready', () => this.zone.run(() => this.cardReady = true));
      card.on('loaderror', event => this.zone.run(() => this.showCardError(event.error?.message)));
      card.mount('#stripe-payment-element');
      this.cardElement = card;
    } catch (err) {
      this.showCardError((err as Error)?.message);
    }
  }

  retryStripeCard(): void {
    if (this.currentPaymentIntent) {
      this.mountStripeCard(this.currentPaymentIntent);
    }
  }

  private showCardError(detail?: string): void {
    this.destroyCard();
    this.paymentError = `The payment form could not be loaded${detail ? ': ' + detail : '.'}`;
    this.messageService.add({
      severity: 'error',
      summary: 'Payment form unavailable',
      detail: this.paymentError,
      life: 6000
    });
  }

  // Step 2: user enters card and clicks Pay → confirm with Stripe, then create order
  async confirmStripePayment(): Promise<void> {
    const intent = this.currentPaymentIntent;
    if (this.paymentLoading) return;
    if (!intent || !this.stripeElements || !this.cardReady) {
      this.showPaymentError('The payment form is not ready yet. Wait for it to load, or retry.');
      return;
    }
    this.paymentLoading = true;
    this.paymentError = null;

    try {
      const stripe = await this.paymentService.getStripe(intent.publishableKey);
      if (!stripe) {
        throw new Error('Stripe.js is not available');
      }
      const { error, paymentIntent } = await stripe.confirmPayment({
        elements: this.stripeElements,
        confirmParams: { return_url: window.location.href },
        redirect: 'if_required'
      });

      if (error) {
        this.showPaymentError(error.message || 'Card was declined');
        return;
      }
      if (paymentIntent?.status !== 'succeeded') {
        this.showPaymentError(`The payment was not completed (status: ${paymentIntent?.status ?? 'unknown'}).`);
        return;
      }
      // paid: this intent must never pay for another purchase
      this.openIntent = null;
      this.placeOrderAfterPayment(paymentIntent.id);
    } catch (err) {
      this.showPaymentError((err as Error)?.message || 'Something went wrong. Please try again.');
    }
  }

  private showPaymentError(detail: string): void {
    this.paymentLoading = false;
    this.paymentError = detail;
    this.messageService.add({ severity: 'error', summary: 'Payment failed', detail, life: 5000 });
  }

  // Step 3: payment confirmed → create the order of the dialog's items with paymentIntentId
  placeOrderAfterPayment(paymentIntentId: string): void {
    const items = this.checkoutItems;
    const submitted = this.isCartCheckout
      ? `${items.length} items — processing...`
      : 'Payment confirmed — processing your order...';

    this.orderService.createOrder(items, paymentIntentId).pipe(take(1)).subscribe({
      next: (newOrder: OrderDto) => {
        this.resetPaymentDialog();
        this.isCartCheckout = false;
        this.quantity.set(1);
        this.orders = [newOrder, ...this.orders];
        this.totalElements++;
        this.applyPendingSSEUpdate(newOrder.id);
        this.pollUntilConfirmed(newOrder.id, newOrder.productName);
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
          detail: submitted,
          life: 3000
        });
      },
      error: (err) => {
        // the payment went through: close the dialog, so that it can't be paid twice
        this.resetPaymentDialog();
        this.isCartCheckout = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Order creation failed',
          detail: `${err?.error?.message || 'Payment succeeded but order failed'} (payment ${paymentIntentId})`,
          sticky: true
        });
      }
    });
  }

  // Cancel keeps the purchase's intent: "Buy now" for the same purchase reopens it
  cancelPayment(): void {
    this.resetPaymentDialog();
    this.isCartCheckout = false;
  }

  private resetPaymentDialog(): void {
    this.showPaymentDialog = false;
    this.paymentLoading = false;
    this.paymentError = null;
    this.currentPaymentIntent = null;
    this.checkoutItems = [];
    this.checkoutTotal = 0;
    this.destroyCard();
  }

  private destroyCard(): void {
    this.cardElement?.destroy();
    this.cardElement = null;
    this.stripeElements = null;
    this.cardReady = false;
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
    this.destroyCard();
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
