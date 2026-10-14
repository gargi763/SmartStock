package com.smartstock.app.config;

import com.smartstock.app.dto.ApiError;
import com.smartstock.app.exception.InsufficientFundsException;
import com.smartstock.app.exception.InvalidTradeException;
import com.smartstock.app.exception.DuplicateWatchlistItemException;
import com.smartstock.app.exception.DuplicateStockException;
import com.smartstock.app.exception.InvalidStockSymbolException;
import com.smartstock.app.exception.InvalidWatchlistSymbolException;
import com.smartstock.app.exception.MarketDataException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.UserRoleChangeNotAllowedException;
import com.smartstock.app.exception.StockDeletionNotAllowedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DuplicateWatchlistItemException.class)
    public ResponseEntity<ApiError> handleDuplicateWatchlistItem(DuplicateWatchlistItemException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DuplicateStockException.class)
    public ResponseEntity<ApiError> handleDuplicateStock(DuplicateStockException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(StockDeletionNotAllowedException.class)
    public ResponseEntity<ApiError> handleStockDeletionNotAllowed(StockDeletionNotAllowedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidStockSymbolException.class)
    public ResponseEntity<ApiError> handleInvalidStockSymbol(InvalidStockSymbolException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidWatchlistSymbolException.class)
    public ResponseEntity<ApiError> handleInvalidWatchlistSymbol(InvalidWatchlistSymbolException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UserRoleChangeNotAllowedException.class)
    public ResponseEntity<ApiError> handleUserRoleChangeNotAllowed(UserRoleChangeNotAllowedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MarketDataException.class)
    public ResponseEntity<ApiError> handleMarketData(MarketDataException ex, HttpServletRequest request) {
        return buildError(ex.getStatus(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidTradeException.class)
    public ResponseEntity<ApiError> handleInvalidTrade(InvalidTradeException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        String message = String.join("; ", errors);
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status, String message, String path) {
        ApiError error = new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).body(error);
    }
}
