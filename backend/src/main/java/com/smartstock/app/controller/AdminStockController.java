package com.smartstock.app.controller;

import com.smartstock.app.dto.AdminStockRequest;
import com.smartstock.app.dto.StockResponse;
import com.smartstock.app.dto.UpdateStockMetadataRequest;
import com.smartstock.app.service.AdminStockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/stocks")
public class AdminStockController {

    private final AdminStockService adminStockService;

    public AdminStockController(AdminStockService adminStockService) {
        this.adminStockService = adminStockService;
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> getStocks() {
        return ResponseEntity.ok(adminStockService.getStocks());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<StockResponse> getStock(@PathVariable String symbol) {
        return ResponseEntity.ok(adminStockService.getStock(symbol));
    }

    @PostMapping
    public ResponseEntity<StockResponse> createStock(@Valid @RequestBody AdminStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminStockService.createStock(request));
    }

    @PutMapping("/{symbol}")
    public ResponseEntity<StockResponse> updateStock(@PathVariable String symbol,
                                                     @Valid @RequestBody UpdateStockMetadataRequest request) {
        return ResponseEntity.ok(adminStockService.updateMetadata(symbol, request));
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> deleteStock(@PathVariable String symbol) {
        adminStockService.deleteStock(symbol);
        return ResponseEntity.noContent().build();
    }
}
