package com.modernbank.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.modernbank.TestDb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorProbeController.class)
class ApiFoundationTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        TestDb.useTempFile(registry);
    }

    @Test
    void healthReturnsUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void badRequestUsesErrorContract() throws Exception {
        mvc.perform(get("/api/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Amount must be a valid number."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void notFoundUsesErrorContract() throws Exception {
        mvc.perform(get("/api/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Account not found: 1"));
    }

    @Test
    void unknownRouteIsNotFoundJson() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void businessRuleIs422() throws Exception {
        mvc.perform(get("/api/test/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value("Account is FROZEN."));
    }

    @Test
    void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
        String body = mvc.perform(get("/api/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("secret internal detail"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("Foo.java"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("trace"));
    }

    @Test
    void missingAndInvalidParametersAre400() throws Exception {
        mvc.perform(get("/api/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: id"));
        mvc.perform(get("/api/test/param").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"));
    }

    @Test
    void corsAllowsReactDevServer() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
