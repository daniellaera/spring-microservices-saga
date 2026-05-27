import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
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
export class ProductsComponent implements OnInit {
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

  ngOnInit(): void { this.loadProducts(); }

  loadProducts(): void {
    this.loading = true;
    this.productService.getAll().subscribe({
      next: p => { this.products = p; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  openAddDialog(): void {
    this.newName = '';
    this.newQuantity = 10;
    this.newPrice = 0;
    this.nameError = '';
    this.showAddDialog = true;
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
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Done',
          detail: this.newName + ' added successfully',
          life: 3000
        });
        this.showAddDialog = false;
        this.saving = false;
        this.loadProducts();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err?.error?.message || 'Could not add product',
          life: 4000
        });
        this.saving = false;
      }
    });
  }
}
