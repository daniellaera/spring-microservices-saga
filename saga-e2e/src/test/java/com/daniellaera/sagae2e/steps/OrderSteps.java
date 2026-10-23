package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.SagaEnvironment;
import com.daniellaera.sagae2e.support.ShopApi;
import com.daniellaera.sagae2e.support.ShopApi.OrderLine;
import com.daniellaera.sagae2e.support.WireMockStubs;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class OrderSteps {

    private static final String SHOP_CURRENCY = "eur";

    private final ScenarioContext context;
    private final ShopApi shop = new ShopApi(SagaEnvironment.get());
    private final WireMockStubs stripe = SagaEnvironment.get().stubs();

    public OrderSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the customer orders {int} {string} paid with an accepted card")
    public void ordersPaidWithAnAcceptedCard(int quantity, String product) {
        payWithCard(List.of(line(product, quantity)), "succeeded");
    }

    @When("the customer orders {int} {string} paid with a declined card")
    public void ordersPaidWithADeclinedCard(int quantity, String product) {
        payWithCard(List.of(line(product, quantity)), "requires_payment_method");
    }

    @When("the customer orders {int} {string} and {int} {string} paid with an accepted card")
    public void ordersTwoProductsPaidWithAnAcceptedCard(int firstQuantity, String firstProduct,
                                                        int secondQuantity, String secondProduct) {
        payWithCard(List.of(line(firstProduct, firstQuantity), line(secondProduct, secondQuantity)), "succeeded");
    }

    @When("the customer orders {int} {string} without a payment intent")
    public void ordersWithoutAPaymentIntent(int quantity, String product) {
        placeOrder(List.of(line(product, quantity)), null);
    }

    @Given("an accepted card payment intent for {bigdecimal}")
    public void anAcceptedCardPaymentIntentFor(BigDecimal amount) {
        String intentId = context.newPaymentIntentId();
        stripe.stubPaymentIntent(intentId, "succeeded", minorUnits(amount), SHOP_CURRENCY);
        context.intentPrepared(intentId);
    }

    @When("the customer orders {int} {string} paid with that intent")
    public void ordersPaidWithThatIntent(int quantity, String product) {
        placeOrder(List.of(line(product, quantity)), context.preparedIntentId());
    }

    @When("the customer orders {int} {string} paid with the same intent")
    public void ordersPaidWithTheSameIntent(int quantity, String product) {
        placeOrder(List.of(line(product, quantity)), context.lastIntentId());
    }

    @Then("the first order is still {word}")
    public void theFirstOrderIsStill(String status) {
        long firstOrderId = context.firstOrderId();
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                shop.order(firstOrderId, context.customerEmail()).path("status").asString()).isEqualTo(status));
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

    // The card's intent is stubbed for exactly the order total in the shop currency, as shop-ui creates it
    private void payWithCard(List<OrderLine> lines, String intentStatus) {
        String intentId = context.newPaymentIntentId();
        stripe.stubPaymentIntent(intentId, intentStatus, minorUnits(total(lines)), SHOP_CURRENCY);
        placeOrder(lines, intentId);
    }

    private static BigDecimal total(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long minorUnits(BigDecimal amount) {
        return amount.setScale(2).movePointRight(2).longValueExact();
    }

    private void placeOrder(List<OrderLine> lines, String paymentIntentId) {
        JsonNode order = shop.placeOrder(context.customerEmail(), lines, paymentIntentId);
        assertThat(order.path("status").asString()).isEqualTo("PENDING");
        context.orderPlaced(order.path("id").asLong());
        if (paymentIntentId != null) {
            context.orderPaidWith(paymentIntentId);
        }
    }
}
