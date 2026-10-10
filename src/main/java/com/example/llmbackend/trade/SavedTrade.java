package com.example.llmbackend.trade;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 저장된 거래 내역. 동일 승인 재요청에는 기존 결과를 반환한다. */
public record SavedTrade(
        String tradeId,
        String organizationId,
        String clientId,
        BigDecimal saleAmount,
        LocalDate tradeDate
) {
}
