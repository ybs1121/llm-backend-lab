package com.example.llmbackend.report;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SalesTools {

    private final MockSalesService salesService;

    public SalesTools(MockSalesService salesService) {
        this.salesService = salesService;
    }

    @Tool(description = """
            지정 기간의 거래처별 매출액을 기준으로 내림차순 상위 N곳을 조회한다.
            시작일과 종료일을 포함하며, N을 생략하면 10곳을 조회한다. N은 1~100이다.
            거래처 코드·이름·매출액, 조회 기관 ID, 반환한 거래처들의 매출 합계를 반환한다.
            거래 횟수 기준 순위 조회는 지원하지 않는다.
            """)
    // SalesToolCallback이 Processor로 검증한 DTO를 그대로 전달해.
    // 모델 연동 시에도 SalesToolCallback을 등록해서 이 경로를 유지해야 해.
    public TopSalesResult findTopSales(SalesQuery query, ToolContext toolContext) {
        Object value = toolContext.getContext().get("organizationId");

        if (!(value instanceof String organizationId) || organizationId.isBlank()) {
            throw new IllegalStateException("서버의 기관 정보가 없어 조회할 수 없어");
        }
        return salesService.findTopSales(organizationId, query);
    }
}
