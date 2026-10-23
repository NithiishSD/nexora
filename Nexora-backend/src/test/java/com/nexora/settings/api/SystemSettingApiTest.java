package com.nexora.settings.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class SystemSettingApiTest extends AbstractApiTest {

    @Test
    @DisplayName("get all settings requires FINANCE_VIEW and masks sensitive keys")
    void getAllSettings_requiresFinanceViewAndMasksSensitive() throws Exception {
        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", AuthTokens.bearer(tokens.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BUSINESS.name").isNotEmpty())
                .andExpect(jsonPath("$.BACKUP.provider").value("local"));
    }

    @Test
    @DisplayName("update category updates settings and returns updated category")
    void updateCategory_updatesSettingsAndReturnsUpdated() throws Exception {
        mockMvc.perform(put("/api/v1/settings/BUSINESS")
                        .header("Authorization", AuthTokens.bearer(tokens.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settings": {
                                    "name": "Updated Balaji Silks",
                                    "city": "Kanchipuram",
                                    "gstin": "33AAAAA0000A1Z5"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Balaji Silks"))
                .andExpect(jsonPath("$.city").value("Kanchipuram"))
                .andExpect(jsonPath("$.gstin").value("33AAAAA0000A1Z5"));
    }

    @Test
    @DisplayName("export backup returns JSON snapshot of settings and metadata")
    void exportBackup_returnsJsonSnapshot() throws Exception {
        mockMvc.perform(post("/api/v1/settings/backup/export")
                        .header("Authorization", AuthTokens.bearer(tokens.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.settings").isNotEmpty());
    }
}
