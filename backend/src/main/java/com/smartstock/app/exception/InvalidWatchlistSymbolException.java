package com.smartstock.app.exception;

public class InvalidWatchlistSymbolException extends RuntimeException {
    public InvalidWatchlistSymbolException() {
        super("Stock symbol must contain 1 to 10 valid characters");
    }
}
