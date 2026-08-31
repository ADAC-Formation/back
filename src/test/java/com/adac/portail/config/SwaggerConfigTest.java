package com.adac.portail.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TICKET-007 — verifies the OpenAPI doc is branded, not springdoc's generic default title.
 * (Swagger UI itself being reachable is already covered by {@code SwaggerUiTest}, and the
 * context starting is covered by {@code PortailAdacApplicationTests} — see that ticket's note
 * not to test simple config beans beyond "does the app start".)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SwaggerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsExposeTheAdacTitle() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Portail de Formation ADAC"));
    }
}
