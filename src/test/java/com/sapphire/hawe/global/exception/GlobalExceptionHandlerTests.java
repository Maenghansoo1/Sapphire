package com.sapphire.hawe.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void mapsFrameworkErrorsToContractCodes() throws Exception {
        mvc.perform(get("/probe/none")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/probe/param")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/probe/int/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/probe/param")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/probe/param?page=abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/probe/upload")).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(get("/probe/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        mvc.perform(get("/probe/custom")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("specific reason"));
        mvc.perform(get("/probe/multipart")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/int/{n}")
        int integer(@PathVariable int n) { return n; }

        @GetMapping("/probe/param")
        int parameter(@RequestParam int page) { return page; }

        @GetMapping("/probe/upload")
        void upload() { throw new MaxUploadSizeExceededException(1); }

        @GetMapping("/probe/unexpected")
        void unexpected() { throw new IllegalStateException("test"); }

        @GetMapping("/probe/custom")
        void custom() { throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "specific reason"); }

        @GetMapping("/probe/multipart")
        void multipart() { throw new MultipartException("invalid multipart"); }
    }
}
