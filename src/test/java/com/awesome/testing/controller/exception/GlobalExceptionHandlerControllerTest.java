package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
class GlobalExceptionHandlerControllerTest {

    private final GlobalExceptionHandlerController controller = new GlobalExceptionHandlerController();

    @Test
    void shouldHandleCustomException() {
        CustomException exception = new CustomException("Boom", HttpStatus.BAD_REQUEST);

        ResponseEntity<ErrorDto> response = controller.handleCustomException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Boom");
    }

    @Test
    void shouldHandleValidationExceptions() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "field", "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                Objects.requireNonNull(ReflectionUtils.findMethod(Dummy.class, "dummy", String.class)), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = controller.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("field", "must not be blank");
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        ResponseEntity<Map<String, String>> response =
                controller.handleIllegalArgumentException(new IllegalArgumentException("Invalid state"));

        assertThat(response.getBody()).containsEntry("error", "Invalid state");
    }

    @Test
    void shouldHandleAccessDeniedException() {
        ResponseEntity<ErrorDto> response =
                controller.handleAccessDeniedException(new AccessDeniedException("forbidden"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void shouldHandleBadCredentialsException() {
        ResponseEntity<ErrorDto> response =
                controller.handleBadCredentialsException(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username/password supplied");
    }

    @Test
    void shouldHandleAuthenticationException() {
        ResponseEntity<ErrorDto> response =
                controller.handleAuthenticationException(new AuthenticationException("unauthorized") {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    @Test
    void shouldHandleProductNotFound() {
        ResponseEntity<ErrorDto> response =
                controller.handleProductNotFoundException(new ProductNotFoundException("missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("missing");
    }

    private static final class Dummy {
        @SuppressWarnings("unused")
        void dummy(String arg) {}
    }
}
