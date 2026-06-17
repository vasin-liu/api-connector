package com.suntek.apiconnector.api;

import com.suntek.apiconnector.mapping.exception.MappingErrorCode;
import com.suntek.apiconnector.mapping.exception.MappingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeApiExceptionHandlerTest {

    private final RuntimeApiExceptionHandler handler = new RuntimeApiExceptionHandler();

    @Test
    void handleMappingSpecInvalidReturnsBadRequest() {
        MappingException ex = new MappingException(
                "Invalid mapping spec",
                MappingErrorCode.MAPPING_SPEC_INVALID,
                Map.of("field", "mapping.request.rules[0].source"));

        ResponseEntity<?> response = handler.handleMapping(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MAPPING_SPEC_INVALID", bodyCode(response));
    }

    @Test
    void handleMappingScriptRuntimeErrorReturnsBadGateway() {
        MappingException ex = new MappingException(
                "Script failed",
                MappingErrorCode.MAPPING_SCRIPT_RUNTIME_ERROR,
                Map.of("code3rd", "DEMO"));

        ResponseEntity<?> response = handler.handleMapping(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("MAPPING_SCRIPT_RUNTIME_ERROR", bodyCode(response));
    }

    private static String bodyCode(ResponseEntity<?> response) {
        Object body = response.getBody();
        if (body instanceof com.suntek.apiconnector.api.dto.ApiErrorResponse apiError) {
            return apiError.getCode();
        }
        throw new AssertionError("Unexpected body type: " + body);
    }
}
