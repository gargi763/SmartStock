package com.smartstock.app.controller;

import com.smartstock.app.dto.InvestmentSimulationRequest;
import com.smartstock.app.dto.InvestmentSimulationResponse;
import com.smartstock.app.service.InvestmentSimulationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class InvestmentSimulationController {

    private final InvestmentSimulationService simulationService;

    public InvestmentSimulationController(InvestmentSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<InvestmentSimulationResponse> calculate(
            @Valid @RequestBody InvestmentSimulationRequest request) {
        return ResponseEntity.ok(simulationService.calculate(request));
    }
}
