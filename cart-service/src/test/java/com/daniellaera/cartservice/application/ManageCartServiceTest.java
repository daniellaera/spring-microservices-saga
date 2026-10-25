package com.daniellaera.cartservice.application;

import com.daniellaera.cartservice.application.fakes.InMemoryCartRepository;
import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ManageCartServiceTest {

    private static final String USER = "test@example.com";

    private final InMemoryCartRepository repository = new InMemoryCartRepository();
    private final ManageCartService service = new ManageCartService(repository);

    private static CartItem item(String product, int quantity, String price) {
        return new CartItem(product, quantity, new BigDecimal(price));
    }

    @Test
    void aCustomerWithNothingStored_hasTheEmptyCart() {
        Cart cart = service.getCart(USER);

        assertThat(cart).isEqualTo(Cart.empty(USER));
        assertThat(cart.expiresIn()).isEqualTo("Cart is empty");
    }

    @Test
    void addingAnItem_storesIt_andAnswersTheCartWithIt() {
        Cart cart = service.addItem(USER, item("MacBook Pro", 1, "1299.99"));

        assertThat(cart.items()).containsExactly(item("MacBook Pro", 1, "1299.99"));
        assertThat(cart.total()).isEqualByComparingTo("1299.99");
        assertThat(cart.ttlSeconds()).isEqualTo(1800);
        assertThat(repository.writes()).containsExactly("save MacBook Pro 1800");
    }

    @Test
    void addingAProductAlreadyInTheCart_sumsTheQuantities_atTheNewPrice() {
        service.addItem(USER, item("MacBook Pro", 1, "1299.99"));

        Cart cart = service.addItem(USER, item("MacBook Pro", 2, "1199.99"));

        assertThat(cart.items()).containsExactly(item("MacBook Pro", 3, "1199.99"));
    }

    @Test
    void removingAnItem_deletesIt_andRestartsTheLifetime() {
        service.addItem(USER, item("MacBook Pro", 1, "1299.99"));
        service.addItem(USER, item("Lamp", 1, "49.90"));

        Cart cart = service.removeItem(USER, "MacBook Pro");

        assertThat(cart.items()).containsExactly(item("Lamp", 1, "49.90"));
        assertThat(repository.writes()).last().isEqualTo("remove MacBook Pro 1800");
    }

    @Test
    void clearingTheCart_deletesIt() {
        service.addItem(USER, item("MacBook Pro", 1, "1299.99"));

        service.clearCart(USER);

        assertThat(service.getCart(USER)).isEqualTo(Cart.empty(USER));
        assertThat(repository.writes()).last().isEqualTo("delete");
    }

    @Test
    void updatingToZeroOrLess_removesTheItem_withoutStoringIt() {
        service.addItem(USER, item("MacBook Pro", 1, "1299.99"));

        Cart cart = service.updateQuantity(USER, "MacBook Pro", 0);
        service.addItem(USER, item("Lamp", 1, "49.90"));
        service.updateQuantity(USER, "Lamp", -1);

        assertThat(cart).isEqualTo(Cart.empty(USER));
        assertThat(repository.writes()).containsExactly("save MacBook Pro 1800", "remove MacBook Pro 1800",
                "save Lamp 1800", "remove Lamp 1800");
    }

    @Test
    void updatingTheQuantity_keepsThePrice() {
        service.addItem(USER, item("MacBook Pro", 1, "1299.99"));

        Cart cart = service.updateQuantity(USER, "MacBook Pro", 4);

        assertThat(cart.items()).containsExactly(item("MacBook Pro", 4, "1299.99"));
    }

    @Test
    void updatingAProductNotInTheCart_changesNothing() {
        Cart cart = service.updateQuantity(USER, "Nothing", 3);

        assertThat(cart).isEqualTo(Cart.empty(USER));
        assertThat(repository.writes()).isEmpty();
    }

    @Test
    void everyWrite_restartsTheLifetime() {
        service.addItem(USER, item("MacBook Pro", 1, "1299.99"));
        service.updateQuantity(USER, "MacBook Pro", 2);
        service.removeItem(USER, "MacBook Pro");

        assertThat(repository.writes()).containsExactly("save MacBook Pro 1800", "save MacBook Pro 1800",
                "remove MacBook Pro 1800");
    }
}
