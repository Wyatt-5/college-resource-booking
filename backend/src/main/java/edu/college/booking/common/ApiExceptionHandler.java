package edu.college.booking.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.TypeMismatchException;
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

    @ExceptionHandler(edu.college.booking.catalog.CatalogException.class)
    public ResponseEntity<ApiError> catalogError(edu.college.booking.catalog.CatalogException exception) {
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(exception.status).header("X-Request-Id", requestId)
                .body(new ApiError(exception.code, exception.getMessage(), new java.util.HashMap<>(exception.details), requestId));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ApiError> invalidBody(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求内容或文件无效", request, exception);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> oversized(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "VALIDATION_ERROR", "上传文件超过大小限制", request, exception);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(
            TypeMismatchException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数格式错误", request, exception);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingPath(
            NoResourceFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在", request, exception);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleFrameworkError(
            ErrorResponseException exception, HttpServletRequest request) {
        int status = exception.getStatusCode().value();
        String code = switch (status) {
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "INVALID_STATE";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "VALIDATION_ERROR";
        };
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
