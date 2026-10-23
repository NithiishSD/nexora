package com.nexora.support;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.identity.infrastructure.RoleRepository;
import com.nexora.identity.infrastructure.UserRepository;
import com.nexora.inventory.application.InventoryService;
import com.nexora.parties.application.PartyService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Base class for HTTP tests: the full application, a real database, MockMvc (requests go through the
// real security filter chain and controllers, without opening a network port) and real tokens.
@AutoConfigureMockMvc
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    // The application's own JSON mapper (Jackson 3), to write request bodies and read responses.
    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // The owner created at startup by OwnerBootstrap from the dev defaults (identity data is never cleaned).
    @Value("${nexora.bootstrap.owner-username}")
    private String ownerUsername;

    @Value("${nexora.bootstrap.owner-password}")
    private String ownerPassword;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private PartyService partyService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    // The application clock (Asia/Kolkata): tests derive "today" from it exactly as the business code does.
    @Autowired
    protected Clock clock;

    protected AuthTokens tokens;

    protected TestDataFactory data;

    // Every API test starts from empty business tables and gets fresh helpers.
    @BeforeEach
    void setUpApiTest() {
        DatabaseCleaner.clean(jdbc);
        data = new TestDataFactory(partyService, inventoryService, new TransactionTemplate(transactionManager));
        tokens = new AuthTokens(mockMvc, jsonMapper, jwtEncoder, userRepository, roleRepository, passwordEncoder,
                ownerUsername, ownerPassword);
    }

    // Serializes a request body to a JSON string.
    protected String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }

    // Sends the request as the owner (all permissions).
    protected ResultActions asOwner(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())));
    }

    // Sends the request as staff (read-only permissions).
    protected ResultActions asStaff(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())));
    }

    // A POST with a JSON body.
    protected static MockHttpServletRequestBuilder postJson(String path, String body) {
        return MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // The response body as a JSON tree.
    protected JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // Creates a purchase through the API and returns its id.
    protected String createPurchase(UUID supplierId, String productType, String orderedKg, String ratePerKg)
            throws Exception {
        return body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","productType":"%s","purchaseDate":"2026-10-01","orderedWeightKg":%s,
                 "ratePerKg":%s,"paymentType":"CREDIT"}""".formatted(supplierId, productType, orderedKg, ratePerKg)))
                .andExpect(MockMvcResultMatchers.status().isCreated()))
                .get("id").asString();
    }

    // Records a receipt through the API (the response is returned unchecked, so tests can expect errors).
    protected ResultActions receive(String purchaseId, String received, String accepted, String rejected)
            throws Exception {
        return asOwner(postWithKey("/api/v1/purchases/" + purchaseId + "/receipts", """
                {"receiptDate":"2026-10-03","receivedWeightKg":%s,"acceptedWeightKg":%s,"rejectedWeightKg":%s}"""
                .formatted(received, accepted, rejected)));
    }

    // Today in the business time zone.
    protected LocalDate today() {
        return LocalDate.now(clock);
    }

    // Creates a one-line order dated today through the API and returns its id.
    protected String createOrder(UUID customerId, String productType, String kg, String ratePerKg) throws Exception {
        return body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s",
                 "items":[{"productType":"%s","requiredWeightKg":%s,"ratePerKg":%s}]}"""
                .formatted(customerId, today(), productType, kg, ratePerKg)))
                .andExpect(MockMvcResultMatchers.status().isCreated()))
                .get("id").asString();
    }

    // Creates a cash or bank account through the API with an opening balance dated today; returns its id.
    protected String createAccount(String name, String type, String opening) throws Exception {
        String bank = type.equals("BANK") ? ",\"bankName\":\"" + name + " bank\"" : "";
        return body(asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"%s","accountType":"%s"%s,"openingBalance":%s,"openingDate":"%s"}"""
                .formatted(name, type, bank, opening, today()))).andExpect(MockMvcResultMatchers.status().isCreated()))
                .get("id").asString();
    }

    // Creates an order through the API and confirms it (from then on it is owed, assumption Q2); returns its id.
    protected String confirmedOrder(UUID customerId, String productType, String kg, String ratePerKg) throws Exception {
        String id = createOrder(customerId, productType, kg, ratePerKg);
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(MockMvcResultMatchers.status().isOk());
        return id;
    }

    // POST with a fresh Idempotency-Key, as the client sends every retry-sensitive write.
    protected static MockHttpServletRequestBuilder postWithKey(String path, String body) {
        return postJson(path, body).header("Idempotency-Key", UUID.randomUUID().toString());
    }

    // POST with a chosen Idempotency-Key (replay tests send the same key twice).
    protected static MockHttpServletRequestBuilder postWithKey(String path, String body, String key) {
        return postJson(path, body).header("Idempotency-Key", key);
    }

    // Sends the same request twice with one key: the second answer must be the stored first one, marked replayed.
    protected String replayTwice(String path, String body, int expectedStatus) throws Exception {
        String key = UUID.randomUUID().toString();
        String first = asOwner(postWithKey(path, body, key))
                .andExpect(MockMvcResultMatchers.status().is(expectedStatus))
                .andExpect(MockMvcResultMatchers.header().doesNotExist("Idempotency-Replayed"))
                .andReturn().getResponse().getContentAsString();
        asOwner(postWithKey(path, body, key))
                .andExpect(MockMvcResultMatchers.status().is(expectedStatus))
                .andExpect(MockMvcResultMatchers.header().string("Idempotency-Replayed", "true"))
                .andExpect(MockMvcResultMatchers.content().json(first));
        return first;
    }
}
