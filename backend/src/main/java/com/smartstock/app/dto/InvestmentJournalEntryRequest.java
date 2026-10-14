package com.smartstock.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InvestmentJournalEntryRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 120, message = "Title must be at most 120 characters")
        String title,

        @NotBlank(message = "Trade thesis or reason is required")
        @Size(max = 4000, message = "Trade thesis must be at most 4000 characters")
        String thesis,

        @NotBlank(message = "Lessons learned is required")
        @Size(max = 4000, message = "Lessons learned must be at most 4000 characters")
        String lessonsLearned,

        @Size(max = 10, message = "At most 10 tags are allowed")
        List<@NotBlank(message = "Tags cannot be blank")
             @Size(max = 40, message = "Tags must be at most 40 characters") String> tags,

        @Positive(message = "Transaction ID must be positive")
        Long transactionId
) {}
