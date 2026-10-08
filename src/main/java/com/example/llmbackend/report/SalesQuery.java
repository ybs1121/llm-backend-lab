package com.example.llmbackend.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record SalesQuery(
        // TODO: Add startDate, endDate, limit and field validation annotations.
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @ToolParam(description = "조회할 상위 거래처 수. 생략하면 10, 허용 범위는 1~100.", required = false)
        @Min(1) @Max(100) Integer limit
) {
    public SalesQuery {
        if (limit == null) {
            limit = 10;
        }
    }

    public void validateDateRange() {
        if (this.startDate.isAfter(this.endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없어");
        }
    }
}
