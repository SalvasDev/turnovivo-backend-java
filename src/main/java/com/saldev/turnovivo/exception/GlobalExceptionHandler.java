package com.saldev.turnovivo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handles all resource-not-found errors (404).
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // Handles business-logic errors and conflicts (dynamic status: 400, 409, or 403).
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex) {
        return buildResponse(ex.getMessage(), ex.getStatus());
    }

    /*
    * Handles missing required query-string parameters (for example, ?customerId=...).
    * Without this handler, Spring would normally return 400, but the generic
    * catch-all below would incorrectly convert it to 500.
    */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException ex) {
        return buildResponse(
                "The mandatory parameter is missing. '" + ex.getParameterName() + "' from the request.",
                HttpStatus.BAD_REQUEST
        );
    }

    // Handles malformed path or query parameter values (for example, an invalid UUID).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(
                "The value '" + ex.getValue() + "' \n" + "is not valid for the parameter '" + ex.getName() + "'.",
                HttpStatus.BAD_REQUEST
        );
    }

    // Handles validation errors for DTOs annotated with @Valid (for example, @NotBlank or @Email).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" "));

        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.warn("Database constraint rejected a request", ex);
        return buildResponse(
                "The operation conflicts with the current state of the data.",
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return buildResponse(
                "You do not have authorization to perform this action.",
                HttpStatus.FORBIDDEN
        );
    }

    // Internal safety boundary for unexpected Java errors (500).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex) {
         logger.error("Unhandled server error", ex);

        return buildResponse(
                "Ocurrió un error interno en el servidor. Por favor intente más tarde.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<?> buildResponse(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return new ResponseEntity<>(body, status);
    }
}
