package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.SagaEnvironment;
import com.daniellaera.sagae2e.support.ShopApi;
import com.daniellaera.sagae2e.support.ShopApi.OrderLine;
import com.daniellaera.sagae2e.support.WireMockStubs;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class OrderSteps {

    private final ScenarioContext context;
    private final ShopApi shop = new ShopApi(SagaEnvironment.get());

    public OrderSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the customer orders {int} {string} paid with an accepted card")
    public void ordersPaidWithAnAcceptedCard(int quantity, String product) {
        placeOrder(List.of(line(product, quantity)), WireMockStubs.ACCEPTED_INTENT);
    }

    @When("the customer orders {int} {string} paid with a declined card")
    public void ordersPaidWithADeclinedCard(int quantity, String product) {
        placeOrder(List.of(line(product, quantity)), WireMockStubs.DECLINED_INTENT);
    }

    @When("the customer orders {int} {string} and {int} {string} paid with an accepted card")
    public void ordersTwoProductsPaidWithAnAcceptedCard(int firstQuantity, String firstProduct,
                                                        int secondQuantity, String secondProduct) {
        placeOrder(List.of(line(firstProduct, firstQuantity), line(secondProduct, secondQuantity)),
                WireMockStubs.ACCEPTED_INTENT);
    }

    @Then("the order is {word}")
    public void theOrderIs(String status) {
        long orderId = context.orderId();
        await().untilAsserted(() -> assertThat(shop.order(orderId, context.customerEmail()).path("status").asString())
                .isEqualTo(status));
    }

    @Then("one {word} payment transaction exists for the order")
    public void onePaymentTransactionExists(String status) {
        long orderId = context.orderId();
        await().untilAsserted(() -> assertThat(shop.transactionsOf(orderId))
                .extracting(transaction -> transaction.path("status").asString())
                .containsExactly(status));
    }

    private OrderLine line(String product, int quantity) {
        ScenarioContext.Product known = context.product(product);
        return new OrderLine(known.name(), quantity, known.price());
    }

    private void placeOrder(List<OrderLine> lines, String paymentIntentId) {
        JsonNode order = shop.placeOrder(context.customerEmail(), lines, paymentIntentId);
        assertThat(order.path("status").asString()).isEqualTo("PENDING");
        context.orderPlaced(order.path("id").asLong());
    }
}
