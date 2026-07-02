package com.baseerah.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies {@link GlobalExceptionHandler} maps exceptions to the canonical error envelope.
 *
 * <p>Uses standalone MockMvc wired to a throwaway controller so no application context or DB is
 * required — this isolates the advice under test.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationFailureReturns400ValidationError() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void notFoundReturns404NotFound() throws Exception {
        mockMvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("no such thing"));
    }

    @Test
    void unhandledExceptionReturns500InternalErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."));
    }

    @RestController
    static class TestController {

        record EchoRequest(@NotBlank String name) {
        }

        @PostMapping("/test/echo")
        String echo(@Valid @RequestBody EchoRequest request) {
            return request.name();
        }

        @GetMapping("/test/missing")
        String missing() {
            throw new NotFoundException("no such thing");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
