import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ProductDto {
  id: number;
  name: string;
  quantity: number;
  price: number;
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  constructor(private http: HttpClient) {}

  getAll(): Observable<ProductDto[]> {
    return this.http.get<ProductDto[]>('/products');
  }

  create(name: string, quantity: number, price: number): Observable<ProductDto> {
    return this.http.post<ProductDto>('/products', {
      name, quantity, price
    });
  }
}
