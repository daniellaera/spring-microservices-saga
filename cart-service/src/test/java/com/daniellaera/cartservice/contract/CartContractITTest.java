package com.daniellaera.cartservice.contract;

import com.daniellaera.cartservice.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The contracts of cart-service, recorded from the code before the hexagonal refactor and kept byte-identical
 * (STRICT): the REST bodies of /cart, and the cart as it is stored in Redis (a hash "cart:<email>", one plain JSON
 * value per product, no type information): a cart written by the previous version must still be read.
 * The remaining time of a cart changes by the second: ttlSeconds and expiresIn are checked by shape only.
 * -Dcontracts.record=true rewrites the fixtures; only ever on code whose behaviour is the reference.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CartContractITTest {

    private static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    private static final boolean RECORD = Boolean.getBoolean("contracts.record");
    private static final String USER = "contract@example.com";
    private static final String KEY = "cart:" + USER;
    private static final CustomComparator TIME_LEFT_BY_SHAPE = new CustomComparator(JSONCompareMode.STRICT,
            new Customization("body.ttlSeconds", (expected, actual) -> ((Number) actual).longValue() > 1700
                    || ((Number) expected).longValue() == ((Number) actual).longValue()),
            new Customization("body.expiresIn", (expected, actual) -> expected.equals(actual)
                    || actual.toString().matches("(29|30)m \\d+s")),
            new Customization("timestamp", (expected, actual) -> true));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void emptyCart() {
        redis.delete(KEY);
    }

    @Test
    void emptyCart_body() throws Exception {
        assertResponse("rest-get-empty.json", get("/cart").header("X-User-Email", USER));
    }

    @Test
    void addingTwoProducts_andTheSameOneAgain() throws Exception {
        add("MacBook Pro", 1, "1299.99");
        add("USB-C Hub", 2, "49.90");
        assertResponse("rest-add-again.json", addRequest("MacBook Pro", 1, "1299.99"));
        assertStored("redis-macbook-pro.json", "MacBook Pro");
    }

    @Test
    void updateQuantity_zeroRemoves_andUnknownProductChangesNothing() throws Exception {
        add("Lamp", 1, "49.90");
        assertResponse("rest-update-quantity.json",
                put("/cart/items/Lamp").header("X-User-Email", USER).param("quantity", "3"));
        assertResponse("rest-update-unknown.json",
                put("/cart/items/Nothing").header("X-User-Email", USER).param("quantity", "3"));
        assertResponse("rest-update-to-zero.json",
                put("/cart/items/Lamp").header("X-User-Email", USER).param("quantity", "0"));
    }

    @Test
    void removeItem_andClearCart() throws Exception {
        add("Lamp", 1, "49.90");
        add("Desk", 1, "199.00");
        assertResponse("rest-remove-item.json", delete("/cart/items/Lamp").header("X-User-Email", USER));
        assertResponse("rest-clear.json", delete("/cart").header("X-User-Email", USER));
        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void invalidItem_andMissingUser() throws Exception {
        assertResponse("rest-add-invalid.json", post("/cart/items").header("X-User-Email", USER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productName\":\"\",\"quantity\":0,\"price\":0}"));
        assertResponse("rest-get-without-user.json", get("/cart"));
    }

    @Test
    void aCartStoredByThePreviousVersion_isRead() throws Exception {
        redis.opsForHash().put(KEY, "Lamp", read("redis-stored-before-the-refactor.json").trim());
        redis.expire(KEY, java.time.Duration.ofSeconds(1800));

        assertResponse("rest-get-stored-before.json", get("/cart").header("X-User-Email", USER));
    }

    private void add(String product, int quantity, String price) throws Exception {
        mockMvc.perform(addRequest(product, quantity, price));
    }

    private MockHttpServletRequestBuilder addRequest(String product, int quantity, String price) {
        return post("/cart/items").header("X-User-Email", USER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productName\":\"%s\",\"quantity\":%d,\"price\":%s}".formatted(product, quantity, price));
    }

    // the raw value, as another reader of Redis sees it
    private void assertStored(String fixture, String product) throws Exception {
        Object raw = redis.opsForHash().get(KEY, product);
        check(fixture, raw.toString(), new CustomComparator(JSONCompareMode.STRICT));
    }

    private void assertResponse(String fixture, MockHttpServletRequestBuilder request) throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("status", response.getStatus());
        actual.put("body", body.isEmpty() ? null : JsonMapper.builder().build().readTree(body));
        check(fixture, JsonMapper.builder().build().writeValueAsString(actual), TIME_LEFT_BY_SHAPE);
    }

    private static void check(String fixture, String actual, CustomComparator comparator) throws Exception {
        if (RECORD) {
            Files.writeString(CONTRACTS.resolve(fixture), JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(JsonMapper.builder().build().readTree(actual)) + "\n");
            return;
        }
        JSONAssert.assertEquals(read(fixture), actual, comparator);
    }

    private static String read(String fixture) {
        try {
            return Files.readString(CONTRACTS.resolve(fixture));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
