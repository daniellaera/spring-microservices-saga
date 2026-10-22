package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.SagaEnvironment;
import com.daniellaera.sagae2e.support.ShopApi;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ProductSteps {

    private static final BigDecimal PRICE = new BigDecimal("25.00");
    // How long a stock level must hold to count as "unchanged"
    private static final Duration STEADY = Duration.ofSeconds(3);

    private final ScenarioContext context;
    private final ShopApi shop = new ShopApi(SagaEnvironment.get());

    public ProductSteps(ScenarioContext context) {
        this.context = context;
    }

    @Given("a product {string} with {int} in stock")
    public void aProductWithStock(String product, int quantity) {
        aPricedProductWithStock(product, PRICE, quantity);
    }

    @Given("a product {string} priced {bigdecimal} with {int} in stock")
    public void aPricedProductWithStock(String product, BigDecimal price, int quantity) {
        JsonNode created = shop.createProduct(context.uniqueName(product), quantity, price);
        assertThat(created.path("quantity").asInt()).isEqualTo(quantity);
        context.addProduct(product, new ScenarioContext.Product(created.path("id").asLong(),
                created.path("name").asString(), price));
    }

    @Then("{string} has {int} in stock")
    public void hasInStock(String product, int quantity) {
        long id = context.product(product).id();
        await().untilAsserted(() -> assertThat(stockOf(id)).isEqualTo(quantity));
    }

    @Then("{string} still has {int} in stock")
    public void stillHasInStock(String product, int quantity) {
        long id = context.product(product).id();
        await().during(STEADY).atMost(STEADY.plusSeconds(30))
                .untilAsserted(() -> assertThat(stockOf(id)).isEqualTo(quantity));
    }

    private int stockOf(long productId) {
        return shop.product(productId).path("quantity").asInt();
    }
}
