package edu.college.booking.catalog;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class CatalogException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final Map<String, String> details;
    public CatalogException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }
    public CatalogException(HttpStatus status, String code, String message, Map<String, String> details) {
        super(message); this.status = status; this.code = code; this.details = Map.copyOf(details);
    }
    public static CatalogException invalid(String field, String reason) {
        return new CatalogException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请检查提交的信息", Map.of(field, reason));
    }
    public static CatalogException missing() {
        return new CatalogException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在或不可访问");
    }
}
