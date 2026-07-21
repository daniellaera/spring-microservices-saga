import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subject } from 'rxjs';
import { take } from 'rxjs/operators';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CardModule } from 'primeng/card';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageService } from 'primeng/api';
import { ProductService, ProductDto } from '../../core/services/product.service';
import { handleHttpError } from '../../core/utils/error-handler.util';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule,
    InputNumberModule, CardModule, ToastModule,
    DialogModule, TagModule, SkeletonModule,
    RouterModule
  ],
  providers: [MessageService],
  templateUrl: './products.component.html'
})
export class ProductsComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  private productService = inject(ProductService);
  private messageService = inject(MessageService);

  products: ProductDto[] = [];
  loading = true;
  saving = false;
  showAddDialog = false;

  newName = '';
  newQuantity = 10;
  newPrice = 0;
  nameError = '';

  showRestockDialog = false;
  selectedProductForRestock: ProductDto | null = null;
  restockQuantity = 10;
  restocking = false;

  ngOnInit(): void { this.loadProducts(); }

  loadProducts(): void {
    this.loading = true;
    this.productService.getAll().pipe(take(1)).subscribe({
      next: p => { this.products = p; this.loading = false; },
      error: (err) => {
        this.loading = false;
        handleHttpError(err, this.messageService, 'Could not load products');
      }
    });
  }

  openAddDialog(): void {
    this.newName = '';
    this.newQuantity = 10;
    this.newPrice = 0;
    this.nameError = '';
    this.showAddDialog = true;
  }

  openRestockDialog(product: ProductDto): void {
    this.selectedProductForRestock = product;
    this.restockQuantity = 10;
    this.showRestockDialog = true;
  }

  restock(): void {
    if (!this.selectedProductForRestock) return;
    this.restocking = true;
    this.productService.restock(
      this.selectedProductForRestock.id,
      this.restockQuantity
    ).pipe(take(1)).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Stock refilled',
          detail: `${this.selectedProductForRestock!.name} restocked +${this.restockQuantity}`,
          life: 3000
        });
        this.showRestockDialog = false;
        this.restocking = false;
        this.loadProducts();
      },
      error: (err) => {
        handleHttpError(err, this.messageService, 'Could not restock');
        this.restocking = false;
      }
    });
  }

  addProduct(): void {
    if (!this.newName.trim()) {
      this.nameError = 'Product name is required';
      return;
    }
    this.saving = true;
    this.productService.create(
      this.newName.trim(),
      this.newQuantity,
      this.newPrice
    ).pipe(take(1)).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Done',
          detail: this.newName + ' added successfully',
          life: 3000
        });
        this.showAddDialog = false;
        this.saving = false;
        this.newName = '';
        this.newQuantity = 10;
        this.newPrice = 0;
        this.nameError = '';
        this.loadProducts();
      },
      error: (err) => {
        handleHttpError(err, this.messageService, 'Could not add product');
        this.saving = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
