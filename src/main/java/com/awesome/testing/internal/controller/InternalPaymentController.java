package com.awesome.testing.internal.controller;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.payment.PaymentResultDto;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.internal.security.PaymentSignatureVerifier;
import com.awesome.testing.internal.service.InternalPaymentService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Payment results from payment-service (payment caller only, see
 * {@link com.awesome.testing.internal.InternalApiConfig}). The body is read raw
 * because {@code X-Payment-Signature} signs its exact bytes; it is parsed and
 * validated only after the signature checks out. Undocumented in OpenAPI like
 * the rest of the internal API.
 */
@RestController
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentSignatureVerifier signatureVerifier;
    private final InternalPaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(path = InternalApi.BASE_PATH + "/orders/{id}/payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PaymentResultResponseDto paymentResult(
            @PathVariable Long id,
            @RequestHeader(name = PaymentSignatureVerifier.HEADER, required = false) String signature,
            @RequestBody String rawBody) {
        if (!signatureVerifier.verify(signature, rawBody)) {
            throw new CustomException("Invalid payment signature", HttpStatus.UNAUTHORIZED);
        }
        return paymentService.apply(id, parse(rawBody));
    }

    private PaymentResultDto parse(String rawBody) {
        PaymentResultDto result;
        try {
            result = objectMapper.readValue(rawBody, PaymentResultDto.class);
        } catch (JacksonException e) {
            throw new InvalidPaymentResultException(Map.of("body", "Malformed payment result"), e);
        }
        if (result == null) {
            throw new InvalidPaymentResultException(Map.of("body", "Malformed payment result"), null);
        }
        Set<ConstraintViolation<PaymentResultDto>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new TreeMap<>();
            violations.forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
            throw new InvalidPaymentResultException(errors, null);
        }
        return result;
    }

    /** Same shape as the global bean-validation response: field name to message. */
    @ExceptionHandler(InvalidPaymentResultException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidPaymentResultException e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(e.errors);
    }

    static final class InvalidPaymentResultException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Map<String, String> errors;

        InvalidPaymentResultException(Map<String, String> errors, Throwable cause) {
            super("Invalid payment result", cause, false, false);
            this.errors = errors;
        }
    }
}
