package com.example.llmbackend.report;

import java.math.BigDecimal;
import java.util.List;

public record TopSalesResult(
        String organizationId,
        List<CustomerSales> customerSales,
        // 반환한 상위 거래처 목록의 매출액 합계.
        BigDecimal totalSaleAmount
) {
}
