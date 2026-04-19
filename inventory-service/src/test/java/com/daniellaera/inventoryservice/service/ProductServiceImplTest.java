package com.daniellaera.inventoryservice.service;

import com.daniellaera.inventoryservice.dto.ProductDTO;
import com.daniellaera.inventoryservice.dto.ProductRequest;
import com.daniellaera.inventoryservice.exception.ResourceNotFoundException;
import com.daniellaera.inventoryservice.model.Product;
import com.daniellaera.inventoryservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setName("MacBook Pro");
        product.setQuantity(10);
    }

    @Test
    void getAllProducts_shouldReturnListOfDTOs() {
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<ProductDTO> result = productService.getAllProducts();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("MacBook Pro");
        assertThat(result.getFirst().quantity()).isEqualTo(10);
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void getProductById_shouldReturnDTO_whenExists() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductDTO result = productService.getProductById(1L);

        assertThat(result.name()).isEqualTo("MacBook Pro");
        assertThat(result.quantity()).isEqualTo(10);
    }

    @Test
    void getProductById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void createProduct_shouldSaveAndReturnDTO() {
        ProductRequest request = new ProductRequest("iPhone 16", 5);

        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductDTO result = productService.createProduct(request);

        assertThat(result.name()).isEqualTo("MacBook Pro");
        assertThat(result.quantity()).isEqualTo(10);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void createProduct_shouldSetNameAndQuantity() {
        ProductRequest request = new ProductRequest("iPhone 16", 5);
        Product saved = new Product();
        saved.setName("iPhone 16");
        saved.setQuantity(5);

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDTO result = productService.createProduct(request);

        assertThat(result.name()).isEqualTo("iPhone 16");
        assertThat(result.quantity()).isEqualTo(5);
    }
}