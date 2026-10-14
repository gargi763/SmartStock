package com.smartstock.app.exception;

public class InvalidStockSymbolException extends RuntimeException {
    public InvalidStockSymbolException() {
        super("Stock symbol must contain 1 to 10 letters, digits, periods, or hyphens");
    }
}
