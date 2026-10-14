package com.smartstock.app.exception;

public class DuplicateWatchlistItemException extends RuntimeException {
    public DuplicateWatchlistItemException(String symbol) {
        super("Stock is already in the watchlist: " + symbol);
    }
}
