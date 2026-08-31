package com.adac.portail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Swagger UI is exposed and reachable (following the redirect
 * from /swagger-ui.html to /swagger-ui/index.html, as a browser would).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SwaggerUiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiRespondsOk() throws Exception {
        String redirectedUrl = mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        mockMvc.perform(get(redirectedUrl))
                .andExpect(status().isOk());
    }
}
