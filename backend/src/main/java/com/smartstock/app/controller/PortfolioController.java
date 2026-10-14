package com.smartstock.app.controller;

import com.smartstock.app.domain.User;
import com.smartstock.app.dto.PortfolioSummaryResponse;
import com.smartstock.app.dto.TradeRequest;
import com.smartstock.app.dto.UpdatePortfolioNameRequest;
import com.smartstock.app.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/portfolios")
    public ResponseEntity<List<PortfolioSummaryResponse>> getPortfolios(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(portfolioService.getPortfoliosForUser(username));
    }

    @PostMapping("/portfolios")
    public ResponseEntity<PortfolioSummaryResponse> createPortfolio(@RequestParam String name, Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(portfolioService.createPortfolio(username, name));
    }

    @PutMapping("/portfolios/{portfolioId}")
    public ResponseEntity<PortfolioSummaryResponse> renamePortfolio(
            @PathVariable Long portfolioId,
            @Valid @RequestBody UpdatePortfolioNameRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(portfolioService.renamePortfolio(
                authentication.getName(), portfolioId, request));
    }

    @PostMapping("/portfolios/{portfolioId}/buy")
    public ResponseEntity<PortfolioSummaryResponse> buyStock(@PathVariable Long portfolioId,
                                                          @Valid @RequestBody TradeRequest tradeRequest,
                                                          Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(portfolioService.buyStock(username, portfolioId, tradeRequest));
    }

    @PostMapping("/portfolios/{portfolioId}/sell")
    public ResponseEntity<PortfolioSummaryResponse> sellStock(@PathVariable Long portfolioId,
                                                           @Valid @RequestBody TradeRequest tradeRequest,
                                                           Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(portfolioService.sellStock(username, portfolioId, tradeRequest));
    }
}
