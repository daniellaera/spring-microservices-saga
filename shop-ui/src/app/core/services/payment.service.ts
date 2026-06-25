import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { loadStripe, Stripe } from '@stripe/stripe-js';

export interface PaymentIntentResponse {
  clientSecret: string;
  paymentIntentId: string;
  status: string;
  publishableKey: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private stripe: Stripe | null = null;

  constructor(private http: HttpClient) {}

  createPaymentIntent(amount: number, currency: string = 'eur', productName: string = ''): Observable<PaymentIntentResponse> {
    return this.http.post<PaymentIntentResponse>('/payments/create-intent', { amount, currency, productName });
  }

  async getStripe(publishableKey: string): Promise<Stripe | null> {
    if (!this.stripe) {
      this.stripe = await loadStripe(publishableKey);
    }
    return this.stripe;
  }

  confirmPayment(paymentIntentId: string): Observable<{ success: boolean }> {
    return this.http.get<{ success: boolean }>(`/payments/confirm/${paymentIntentId}`);
  }
}
