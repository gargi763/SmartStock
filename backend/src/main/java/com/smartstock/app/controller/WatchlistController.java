package com.smartstock.app.controller;

import com.smartstock.app.dto.WatchlistItemResponse;
import com.smartstock.app.dto.WatchlistRequest;
import com.smartstock.app.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public ResponseEntity<List<WatchlistItemResponse>> getWatchlist(Authentication authentication) {
        return ResponseEntity.ok(watchlistService.getWatchlist(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<WatchlistItemResponse> addStock(@Valid @RequestBody WatchlistRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(watchlistService.addStock(authentication.getName(), request.symbol()));
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeStock(@PathVariable String symbol, Authentication authentication) {
        watchlistService.removeStock(authentication.getName(), symbol);
        return ResponseEntity.noContent().build();
    }
}
