package com.smartstock.app.exception;

import org.springframework.http.HttpStatus;

public class MarketDataException extends RuntimeException {

    private final HttpStatus status;

    public MarketDataException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
