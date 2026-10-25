package com.daniellaera.authservice.contract;

import com.daniellaera.authservice.TestcontainersConfiguration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The HTTP behaviour of auth-service, recorded from the code before the hexagonal refactor and kept identical (STRICT):
 * for each scenario, every request's status and body, every event sent to Kafka (audit-topic, otp-topic), and what is
 * left in PostgreSQL (users, refresh_tokens) and in Redis (the OTP keys and their expiry). Only HTTP, SQL, Redis and
 * the KafkaTemplate are used, nothing of the service's own classes, so the refactor cannot change what is checked.
 * Values that differ on every run are normalized: a JWT becomes its algorithm, subject, role and validity (verified
 * with the signing key), a refresh token "<uuid>", an OTP "<otp>", a time "<timestamp>", a bcrypt hash its shape, an
 * expiry in seconds its bound, a token's SHA-256 prefix "sha256:<prefix>". -Dcontracts.record=true rewrites the fixtures; only ever on code whose behaviour is
 * the reference.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthHttpContractITTest {

    private static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    private static final boolean RECORD = Boolean.getBoolean("contracts.record");
    // src/test/resources/application.yml
    private static final String JWT_SECRET = "testSecretKey1234567890123456789012345678901234";
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private ArrayNode steps;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        Mockito.clearInvocations(kafkaTemplate);
        steps = JSON.createArrayNode();
    }

    // ─── scenarios ───────────────────────────────────────────────────────────

    @Test
    void register_duplicate_invalid_andExtraFields() throws Exception {
        call("register", register("new@example.com", "password123"));
        call("register again", register("new@example.com", "password123"));
        call("register invalid", json(post("/auth/register"), "{\"email\":\"not-an-email\",\"password\":\"123\"}"));
        call("register without email", json(post("/auth/register"), "{\"password\":\"password123\"}"));
        call("register with extra fields", json(post("/auth/register"),
                "{\"email\":\"extra@example.com\",\"password\":\"password123\",\"role\":\"ADMIN\",\"id\":1}"));
        assertTranscript("auth-register.json");
    }

    @Test
    void login_verifyOtp_andTheWrongPasswordOrUser() throws Exception {
        call("register", register("buyer@example.com", "password123"));
        call("login wrong password", login("buyer@example.com", "wrong-password"));
        call("login unknown user", login("nobody@example.com", "password123"));
        call("login", login("buyer@example.com", "password123"));
        call("verify wrong code", verify("buyer@example.com", wrongCodeFor("buyer@example.com")));
        call("verify", verify("buyer@example.com", otpOf("buyer@example.com")));
        call("verify the consumed code again", verify("buyer@example.com", "123456"));
        assertTranscript("auth-login-verify.json");
    }

    @Test
    void fiveWrongCodes_invalidateTheOtp() throws Exception {
        call("register", register("guesser@example.com", "password123"));
        call("login", login("guesser@example.com", "password123"));
        String wrong = wrongCodeFor("guesser@example.com");
        for (int i = 1; i <= 5; i++) {
            call("wrong code " + i, verify("guesser@example.com", wrong));
        }
        call("the right code after", verify("guesser@example.com", "000000"));
        assertTranscript("auth-wrong-codes.json");
    }

    @Test
    void aSixthLoginWithin15Minutes_isRefused() throws Exception {
        call("register", register("eager@example.com", "password123"));
        for (int i = 1; i <= 6; i++) {
            call("login " + i, login("eager@example.com", "password123"));
        }
        assertTranscript("auth-login-limit.json");
    }

    @Test
    void verifyOtp_forAUserThatNoLongerExists() throws Exception {
        call("register", register("gone@example.com", "password123"));
        call("login", login("gone@example.com", "password123"));
        String otp = otpOf("gone@example.com");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users WHERE email = 'gone@example.com'");
        call("verify", verify("gone@example.com", otp));
        assertTranscript("auth-verify-deleted-user.json");
    }

    @Test
    void refresh_rotates_andRefusesRevokedUnknownAndExpiredTokens() throws Exception {
        String first = refreshTokenOf(call("register", register("rotate@example.com", "password123")));
        String second = refreshTokenOf(call("refresh", refresh(first)));
        call("refresh with the rotated token", refresh(first));
        call("refresh unknown", refresh("00000000-0000-0000-0000-000000000000"));
        call("refresh missing", json(post("/auth/refresh"), "{}"));
        // expiry_date holds UTC (the service's Instant), whatever the session's time zone
        jdbc.update("UPDATE refresh_tokens SET expiry_date = (now() AT TIME ZONE 'UTC') - interval '1 minute' WHERE token = ?",
                second);
        call("refresh expired", refresh(second));
        call("refresh expired again", refresh(second));
        assertTranscript("auth-refresh.json");
    }

    @Test
    void logout_revokes_andAnUnknownTokenIsFine() throws Exception {
        String token = refreshTokenOf(call("register", register("leaver@example.com", "password123")));
        call("logout", json(post("/auth/logout"), "{\"refreshToken\":\"" + token + "\"}"));
        call("refresh after logout", refresh(token));
        call("logout unknown", json(post("/auth/logout"), "{\"refreshToken\":\"unknown\"}"));
        call("logout without token", json(post("/auth/logout"), "{\"refreshToken\":\"\"}"));
        assertTranscript("auth-logout.json");
    }

    @Test
    void checkEmail_andProfile() throws Exception {
        call("register", register("profile@example.com", "password123"));
        call("check taken", get("/auth/check-email").param("email", "profile@example.com"));
        call("check free", get("/auth/check-email").param("email", "free@example.com"));
        call("profile without user", get("/auth/profile"));
        call("profile with a blank user", get("/auth/profile").header("X-User-Email", " "));
        call("profile", get("/auth/profile").header("X-User-Email", "profile@example.com"));
        call("profile of an unknown user", get("/auth/profile").header("X-User-Email", "nobody@example.com"));
        call("set first name", json(put("/auth/profile").header("X-User-Email", "profile@example.com"),
                "{\"firstName\":\"Ada\"}"));
        call("set last name", json(put("/auth/profile").header("X-User-Email", "profile@example.com"),
                "{\"lastName\":\"Lovelace\"}"));
        call("name too long", json(put("/auth/profile").header("X-User-Email", "profile@example.com"),
                "{\"firstName\":\"" + "A".repeat(51) + "\"}"));
        call("update without user", json(put("/auth/profile"), "{\"firstName\":\"Ada\"}"));
        assertTranscript("auth-profile.json");
    }

    @Test
    void security_onlyAuthAndActuatorAreOpen() throws Exception {
        call("an unknown path", get("/users"));
        call("an unknown auth path", get("/auth/nothing"));
        call("health", get("/actuator/health"));
        assertTranscript("auth-security.json");
    }

    // ─── requests ────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder register(String email, String password) {
        return json(post("/auth/register"), "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return json(post("/auth/login"), "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private static MockHttpServletRequestBuilder verify(String email, String otp) {
        return json(post("/auth/verify-otp"), "{\"email\":\"%s\",\"otp\":\"%s\"}".formatted(email, otp));
    }

    private static MockHttpServletRequestBuilder refresh(String token) {
        return json(post("/auth/refresh"), "{\"refreshToken\":\"" + token + "\"}");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String otpOf(String email) {
        return redis.opsForValue().get("otp:" + email);
    }

    private String wrongCodeFor(String email) {
        return otpOf(email).equals("999999") ? "999998" : "999999";
    }

    private static String refreshTokenOf(JsonNode response) {
        return response.path("rawRefreshToken").asString();
    }

    // ─── transcript ──────────────────────────────────────────────────────────

    // Performs the request and records it; answers the raw body (with the raw refresh token, for the next calls)
    private JsonNode call(String name, MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        ObjectNode step = JSON.createObjectNode();
        step.put("step", name);
        step.put("status", response.getStatus());
        JsonNode parsed = body.isBlank() ? null : JSON.readTree(body);
        step.set("body", parsed == null ? null : normalize(parsed));
        steps.add(step);
        ObjectNode raw = JSON.createObjectNode();
        if (parsed != null && parsed.has("refreshToken")) {
            raw.put("rawRefreshToken", parsed.get("refreshToken").asString());
        }
        return raw;
    }

    private void assertTranscript(String fixture) throws Exception {
        ObjectNode transcript = JSON.createObjectNode();
        transcript.set("steps", steps);
        transcript.set("events", events());
        transcript.set("users", JSON.valueToTree(jdbc.queryForList(
                "SELECT email, password, role, first_name, last_name, created_at IS NOT NULL AS created FROM users ORDER BY email")
                .stream().map(this::normalizeRow).toList()));
        transcript.set("refreshTokens", JSON.valueToTree(jdbc.queryForList("""
                SELECT u.email, rt.token, rt.revoked,
                       round(extract(epoch FROM rt.expiry_date - (now() AT TIME ZONE 'UTC')) / 86400) AS days_left
                FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id ORDER BY u.email, rt.id""")
                .stream().map(this::normalizeRow).toList()));
        transcript.set("redis", redisState());
        String actual = JSON.writeValueAsString(transcript);
        if (RECORD) {
            Files.writeString(CONTRACTS.resolve(fixture),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(transcript) + "\n");
            return;
        }
        JSONAssert.assertEquals(read(fixture), actual, JSONCompareMode.STRICT);
    }

    // Every send(topic, value) on the KafkaTemplate: the audit and OTP publishers are @Async, so wait until they
    // stop arriving. Sorted, since their order between threads is not part of the contract.
    private ArrayNode events() throws InterruptedException {
        int seen = -1;
        int quietPolls = 0;
        List<String> sent = List.of();
        // done once three polls in a row (1.2 s) saw nothing new
        while (quietPolls < 3) {
            Thread.sleep(400);
            sent = new ArrayList<>();
            for (Invocation invocation : Mockito.mockingDetails(kafkaTemplate).getInvocations()) {
                if (invocation.getMethod().getName().equals("send") && invocation.getArguments().length == 2) {
                    ObjectNode event = JSON.createObjectNode();
                    event.put("topic", (String) invocation.getArguments()[0]);
                    event.set("value", normalize(JSON.readTree((String) invocation.getArguments()[1])));
                    sent.add(JSON.writeValueAsString(event));
                }
            }
            quietPolls = sent.size() == seen ? quietPolls + 1 : 0;
            seen = sent.size();
        }
        ArrayNode events = JSON.createArrayNode();
        sent.stream().sorted().forEach(event -> events.add(JSON.readTree(event)));
        return events;
    }

    // The OTP keys of every email, with their expiry bounded (OTP 5 min, codes issued 15 min)
    private ObjectNode redisState() {
        Map<String, Object> state = new TreeMap<>();
        for (String key : redis.keys("*")) {
            Long ttl = redis.getExpire(key);
            String value = redis.opsForValue().get(key);
            Map<String, Object> entry = new TreeMap<>();
            entry.put("value", key.startsWith("otp:") ? (value != null && value.matches("\\d{6}") ? "<otp>" : value) : value);
            entry.put("ttl", ttl == null ? "none" : ttl > 600 ? "<= 900 s" : ttl > 0 ? "<= 300 s" : String.valueOf(ttl));
            state.put(key, entry);
        }
        return JSON.valueToTree(state);
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new TreeMap<>();
        row.forEach((column, value) -> {
            Object v = value;
            if (column.equals("password") && value instanceof String hash) {
                v = hash.matches("\\$2a\\$10\\$.{53}") ? "<bcrypt $2a$10$>" : hash;
            } else if (column.equals("token") && value instanceof String token) {
                v = token.matches(UUID) ? "<uuid>" : token;
            }
            normalized.put(column, v);
        });
        return normalized;
    }

    private JsonNode normalize(JsonNode node) {
        // keys sorted: the producers build their messages from Map.of, whose order changes from run to run
        if (node.isObject()) {
            ObjectNode copy = JSON.createObjectNode();
            new TreeMap<>(node.properties().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                    .forEach((key, value) -> copy.set(key, normalizeField(key, value)));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JSON.createArrayNode();
            node.forEach(element -> copy.add(normalize(element)));
            return copy;
        }
        return node;
    }

    private JsonNode normalizeField(String name, JsonNode value) {
        if (!value.isString()) {
            return normalize(value);
        }
        String text = value.asString();
        if (name.equals("token")) {
            return jwt(text);
        }
        if (text.matches(UUID)) {
            return JSON.getNodeFactory().stringNode("<uuid>");
        }
        if (name.equals("otp") && text.matches("\\d{6}")) {
            return JSON.getNodeFactory().stringNode("<otp>");
        }
        if (name.equals("timestamp") && isLocalDateTime(text)) {
            return JSON.getNodeFactory().stringNode("<timestamp>");
        }
        if (name.equals("payload")) {
            return JSON.getNodeFactory().stringNode(text.replaceAll(UUID, "<uuid>")
                    .replaceAll("sha256:[0-9a-f]{12}", "sha256:<prefix>"));
        }
        return value;
    }

    // A JWT as what it says, after verifying its signature with the configured secret
    private JsonNode jwt(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes())).build()
                .parseSignedClaims(token);
        Claims claims = jws.getPayload();
        Map<String, Object> described = new TreeMap<>();
        described.put("alg", jws.getHeader().getAlgorithm());
        described.put("sub", claims.getSubject());
        described.put("role", claims.get("role"));
        described.put("validitySeconds", (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000);
        described.put("claims", new TreeMap<>(claims).keySet());
        return JSON.valueToTree(described);
    }

    private static boolean isLocalDateTime(String text) {
        try {
            LocalDateTime.parse(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String read(String fixture) {
        try {
            return Files.readString(CONTRACTS.resolve(fixture));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
