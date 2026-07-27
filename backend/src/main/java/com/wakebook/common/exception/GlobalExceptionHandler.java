package com.wakebook.common.exception;

import com.wakebook.common.ApiException;
import com.wakebook.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청값을 확인해 주세요.");

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_001", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest() {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_001", "요청값을 확인해 주세요."));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_001", "요청값 누락 또는 형식 오류입니다."));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("AUTH_003", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("AUTH_001", exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ErrorResponse.of("AUTH_001", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("SERVER_001", "서버 오류가 발생했습니다."));
    }
}
