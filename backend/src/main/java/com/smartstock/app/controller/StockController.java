package com.smartstock.app.controller;

import com.smartstock.app.dto.StockResponse;
import com.smartstock.app.dto.CompanyFundamentalsResponse;
import com.smartstock.app.dto.HistoricalPriceHistoryResponse;
import com.smartstock.app.service.CompanyFundamentalsService;
import com.smartstock.app.service.HistoricalStockPriceService;
import com.smartstock.app.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StockController {

    private final StockService stockService;
    private final HistoricalStockPriceService historicalStockPriceService;
    private final CompanyFundamentalsService companyFundamentalsService;

    public StockController(
            StockService stockService,
            HistoricalStockPriceService historicalStockPriceService,
            CompanyFundamentalsService companyFundamentalsService) {
        this.stockService = stockService;
        this.historicalStockPriceService = historicalStockPriceService;
        this.companyFundamentalsService = companyFundamentalsService;
    }

    @GetMapping("/stocks")
    public ResponseEntity<List<StockResponse>> getStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }

    @GetMapping("/stocks/{symbol}")
    public ResponseEntity<StockResponse> getStock(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getStock(symbol));
    }

    @GetMapping("/stocks/{symbol}/history")
    public ResponseEntity<HistoricalPriceHistoryResponse> getHistoricalPrices(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1M") String range) {
        return ResponseEntity.ok(historicalStockPriceService.getHistory(symbol, range));
    }

    @GetMapping("/stocks/{symbol}/fundamentals")
    public ResponseEntity<CompanyFundamentalsResponse> getCompanyFundamentals(@PathVariable String symbol) {
        return ResponseEntity.ok(companyFundamentalsService.getFundamentals(symbol));
    }
}
