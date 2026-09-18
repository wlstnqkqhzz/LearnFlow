package com.be.global.exception;

import com.be.global.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// MVC 입력 오류와 업무 오류를 일관된 JSON 응답으로 변환
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    // 로그인 실패도 필터의 401과 동일한 오류 계약 사용
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(org.springframework.security.core.AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ApiErrorResponse.of("UNAUTHORIZED", "인증이 필요하거나 인증 정보가 유효하지 않습니다."));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException exception) {
        ErrorCode code = exception.getErrorCode();
        HttpStatus status = switch (code) {
            case DEPARTMENT_NOT_FOUND, JOB_POSITION_NOT_FOUND, MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_DEPARTMENT_CODE, DUPLICATE_JOB_POSITION_CODE,
                    DUPLICATE_EMPLOYEE_NUMBER, DUPLICATE_EMAIL,
                    INACTIVE_DEPARTMENT, INACTIVE_JOB_POSITION,
                    INVALID_MEMBER_STATUS_TRANSITION, RESIGNED_MEMBER_UPDATE -> HttpStatus.CONFLICT;
            case SELF_PARENT_DEPARTMENT, DEPARTMENT_CYCLE, REQUIRED_EMPLOYEE_ROLE -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code.name(), code.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<ApiErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ResponseEntity<>(new ApiErrorResponse("VALIDATION_ERROR",
                "입력값을 확인해주세요.", errors), headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
        var errors = exception.getConstraintViolations().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR",
                "입력값을 확인해주세요.", errors));
    }

    // 프레임워크가 처리하는 잘못된 Enum, JSON, 경로, 메서드 등의 오류도 동일 형식 유지
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code;
        String message;
        if (exception instanceof HttpMessageNotReadableException) {
            code = "INVALID_REQUEST";
            message = "JSON 형식과 필드 값, Enum 값을 확인해주세요.";
        } else if (exception instanceof TypeMismatchException) {
            code = "INVALID_ARGUMENT";
            message = "경로 또는 쿼리 파라미터 형식이 올바르지 않습니다.";
        } else {
            code = switch (status.value()) {
                case 400 -> "VALIDATION_ERROR";
                case 404 -> "RESOURCE_NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                case 406 -> "NOT_ACCEPTABLE";
                case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                default -> "REQUEST_ERROR";
            };
            message = status.is5xxServerError() ? "서버 오류가 발생했습니다." : "요청을 처리할 수 없습니다.";
        }
        return new ResponseEntity<>(ApiErrorResponse.of(code, message), headers, status);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException exception) {
        RuntimeException translated = UniqueConstraintErrors.translate(exception);
        if (translated instanceof BusinessException business) {
            return handleBusiness(business);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("DATA_CONFLICT", "기존 데이터와 충돌하는 요청입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        log.error("예상하지 못한 API 오류", exception);
        return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
    }
}
