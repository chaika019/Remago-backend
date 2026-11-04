package com.adrotation.crm.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> unauthorized(
            UnauthorizedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ApiError> emailVerificationRequired(
            EmailVerificationRequiredException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                "EMAIL_VERIFICATION_REQUIRED",
                ex.getMessage(),
                request,
                Map.of("email", ex.getEmail())
        );
    }


    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> rateLimit(
            RateLimitExceededException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                ex.getMessage(),
                request,
                Map.of("retryAfterSeconds", String.valueOf(ex.getRetryAfterSeconds()))
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(
            NotFoundException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(
            BusinessException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                "Недостаточно прав для выполнения операции",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return build(HttpStatus.BAD_REQUEST, "Ошибка валидации", request, errors);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> badRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        String message = ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "Некорректные параметры запроса";

        return build(
                HttpStatus.BAD_REQUEST,
                message == null || message.isBlank() ? "Некорректный запрос" : message,
                request,
                Map.of()
        );
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Некорректный JSON или недопустимое значение поля",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Файл слишком большой. Максимальный размер одного файла — 20 МБ",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("Database constraint violation at {}", request.getRequestURI(), ex);
        return build(
                HttpStatus.CONFLICT,
                "Не удалось сохранить данные: такая запись уже существует или связана с другими данными",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка сервера. Повторите попытку или обратитесь к администратору.",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        return build(status, null, message, request, validationErrors);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                validationErrors
        ));
    }
}
