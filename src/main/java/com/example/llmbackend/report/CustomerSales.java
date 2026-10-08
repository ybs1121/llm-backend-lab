package com.example.llmbackend.report;

import java.math.BigDecimal;

public record CustomerSales(
        String customerId,
        String customerName,
        BigDecimal saleAmount
) {
}
