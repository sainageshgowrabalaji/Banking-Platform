package com.sainagesh.bank.web;

import org.springframework.http.HttpStatus;

/**
 * An error a client can act on. It carries the HTTP status and a stable code, such as
 * {@code INSUFFICIENT_FUNDS}. The shared error handler turns it into a problem details body.
 *
 * <p>Throw it from web adapters and use cases. Domain rules throw their own plain exceptions, and the
 * adapter maps those to an {@code ApiException} or handles them directly.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;
    private final String title;

    public ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, "The request is not valid", detail);
    }

    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, "Not found", detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, "The request conflicts with the current state", detail);
    }

    /** The request was understood but a business rule refused it. */
    public static ApiException rejected(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, "A business rule refused the request", detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }
}
