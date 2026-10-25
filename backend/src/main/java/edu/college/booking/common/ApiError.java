package edu.college.booking.common;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details, String requestId) {
}
