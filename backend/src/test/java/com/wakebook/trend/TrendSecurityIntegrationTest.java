package com.wakebook.trend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrendSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void publicDailyDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/trends/daily").contextPath("/api")
                .param("libraryCode", "121018").param("date", "2099-01-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_001"));
    }

    @Test
    void librarianDailyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/librarian/trends/daily").contextPath("/api"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_001"));
    }
}
