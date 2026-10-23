package com.nexora.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.TestProbeController;

// Five copies of one request sent at the same moment (a double-clicked Save, a retry on a slow line). The first
// claims the key; the others wait on the unique index and then replay its answer. Payments get the same route
// annotation in T3.12; the probe stands in for them here.
@Import(TestProbeController.class)
class IdempotencyConcurrencyTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-07 NFR-USE-05 parallel duplicates create one payment")
    void parallelDuplicates_createOnePayment() throws Exception {
        String key = UUID.randomUUID().toString();
        String token = tokens.owner();
        List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> mockMvc.perform(postJson(TestProbeController.IDEMPOTENT, "{\"name\":\"Ravi\"}")
                    .header("Authorization", "Bearer " + token)
                    .header(IdempotencyFilter.HEADER, key)).andReturn().getResponse());
        }

        List<Outcome<MockHttpServletResponse>> outcomes = ConcurrentRunner.runTogether(tasks);

        // all five answered 201 with the same body; exactly one did the work, four were replays
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        List<MockHttpServletResponse> responses = outcomes.stream().map(Outcome::value).toList();
        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
        assertThat(responses).extracting(r -> r.getContentAsString()).hasSize(5).containsOnly(
                responses.getFirst().getContentAsString());
        assertThat(responses).filteredOn(r -> "true".equals(r.getHeader(IdempotencyFilter.REPLAYED))).hasSize(4);
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys", Integer.class)).isEqualTo(1);
    }
}
