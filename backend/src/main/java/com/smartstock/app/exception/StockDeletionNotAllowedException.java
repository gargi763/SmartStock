package com.smartstock.app.exception;

public class StockDeletionNotAllowedException extends RuntimeException {
    public StockDeletionNotAllowedException(String symbol) {
        super("Stock cannot be deleted because it is referenced by investor data: " + symbol);
    }
}
