package com.smartstock.app.exception;

public class DuplicateStockException extends RuntimeException {
    public DuplicateStockException(String symbol) {
        super("Stock already exists: " + symbol);
    }
}
