package com.ex.tradingPlatform.common;

import com.ex.tradingPlatform.auth.SecurityConfig;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class, GlobalExceptionHandlerTest.ThrowingController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProblemDetailSecurityHandler securityHandler;

    @Test
    @WithMockUser
    void invalidBody_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].message").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='quantity')].message").exists());
    }

    @Test
    @WithMockUser
    void constraintViolation_returns400WithFieldErrors() throws Exception {
        mvc.perform(get("/test/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @WithMockUser
    void businessException_returnsItsStatusAndCode() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.detail").value("Not enough funds."))
                .andExpect(jsonPath("$.instance").value("/test/business"));
    }

    @Test
    @WithMockUser
    void dataIntegrityViolation_returns409WithoutDbDetails() throws Exception {
        mvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("uq_users_email"))));
    }

    @Test
    @WithMockUser
    void accessDeniedInController_returns403() throws Exception {
        mvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser
    void unexpectedException_returns500WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("internal secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void unauthenticated_returns401ProblemDetailFromSecurityLayer() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/test/business"));
    }

    @Test
    void securityAccessDeniedHandler_writes403ProblemDetail() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHandler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"instance\":\"/admin\"");
    }

    record Body(@NotBlank String name, @Positive int quantity) {
    }

    @RestController
    static class ThrowingController {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @PostMapping("/test/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new Body("", 1)));
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Not enough funds.");
        }

        @GetMapping("/test/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_users_email\"");
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("internal secret");
        }
    }
}
