package edu.college.booking.common;

import org.springframework.http.HttpStatus;

public class ApiProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiProblem(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
