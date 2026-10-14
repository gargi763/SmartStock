package com.smartstock.app.exception;

public class InvalidTradeException extends RuntimeException {
    public InvalidTradeException(String message) {
        super(message);
    }
}
