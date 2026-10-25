package edu.college.booking.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingPath(
            NoResourceFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在", request, exception);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleFrameworkError(
            ErrorResponseException exception, HttpServletRequest request) {
        int status = exception.getStatusCode().value();
        String code = status == 404 ? "NOT_FOUND" : "VALIDATION_ERROR";
        return error(exception.getStatusCode(), code, "请求无效或资源不存在", request, exception);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseError(
            DataAccessException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据库暂时不可用", request, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(
            Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器处理失败", request, exception);
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String code, String message,
            HttpServletRequest request, Exception cause) {
        String requestId = UUID.randomUUID().toString();
        if (status.is5xxServerError()) {
            log.error("requestId={} path={} failed", requestId, request.getRequestURI(), cause);
        }
        return ResponseEntity.status(status)
                .header("X-Request-Id", requestId)
                .body(new ApiError(code, message, Map.of(), requestId));
    }
}
