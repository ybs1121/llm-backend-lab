package com.example.llmbackend.trade;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 거래 내역의 업무 내용과 승인·저장 관리 정보를 보관하는 초안.
 */
public record TradeDraft(
        @NotBlank String organizationId,
        @NotBlank String clientId,
        @NotNull @Min(1) BigDecimal saleAmount,
        @NotNull LocalDate tradeDate,
        String draftId,
        Long version,
        // null이면 미저장, 값이 있으면 저장된 거래 내역의 ID.
        String savedTradeId
) {
    // 필드 검증 후 호출해. 금액 상한 없이 소수부가 있는 값만 거절해.
    public void validateWholeAmount() {
        if (saleAmount != null && saleAmount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("거래 금액은 정수여야 해");
        }
    }
}
