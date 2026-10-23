package com.nexora.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.notifications.NotificationTestSupport;
import com.nexora.support.AuthTokens;

class NotificationConfigurationApiTest extends NotificationTestSupport {

    private static final String PATH = "/api/v1/notifications/configurations";

    @Test
    @DisplayName("FR-NTF-01 put updates days before due")
    void put_updatesDaysBeforeDue() throws Exception {
        // the seeded default: SUPPLIER_DUE enabled, 3 days before, repeated daily, HIGH
        asOwner(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].notificationType").value("SUPPLIER_DUE"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].daysBeforeDue").value(3))
                .andExpect(jsonPath("$[0].overdueRepeatDays").value(1))
                .andExpect(jsonPath("$[0].priority").value("HIGH"));

        // AC1: 5 days -> saved, and a purchase due in 5 days gets its DUE_SOON reminder today
        save("SUPPLIER_DUE", 5, 1).andExpect(status().isOk()).andExpect(jsonPath("$.daysBeforeDue").value(5));
        asOwner(get(PATH)).andExpect(jsonPath("$[0].daysBeforeDue").value(5));
        String purchase = duePurchase(data.supplier().id().toString(), today(), 5, "100.00");
        job.run(today());
        assertThat(reminders(purchase)).singleElement().satisfies(r -> assertThat(r.get("reminder_kind"))
                .isEqualTo("DUE_SOON"));

        // AC2: 45 days is 400 VALIDATION_FAILED (repeat 0 too); AC3: an unknown type is 400 MALFORMED_REQUEST;
        // a known type without a configuration row is 404
        save("SUPPLIER_DUE", 45, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("daysBeforeDue"));
        save("SUPPLIER_DUE", 3, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        save("FUEL_DUE", 3, 1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        save("LOAN_DUE", 3, 1).andExpect(status().isNotFound());
        asOwner(get(PATH)).andExpect(jsonPath("$[0].daysBeforeDue").value(5));

        // STAFF can neither read nor change it
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH + "/SUPPLIER_DUE").contentType(MediaType.APPLICATION_JSON).content(body(3, 1))
                .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff()))).andExpect(status().isForbidden());
    }

    private ResultActions save(String type, int daysBeforeDue, int repeat) throws Exception {
        return asOwner(put(PATH + "/" + type).contentType(MediaType.APPLICATION_JSON).content(body(daysBeforeDue, repeat)));
    }

    private static String body(int daysBeforeDue, int repeat) {
        return """
                {"enabled":true,"daysBeforeDue":%d,"overdueRepeatDays":%d,"priority":"HIGH"}"""
                .formatted(daysBeforeDue, repeat);
    }
}
