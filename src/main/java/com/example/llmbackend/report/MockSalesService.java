package com.example.llmbackend.report;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DB에서 거래처별 집계와 정렬을 끝낸 조회 결과를 흉내 내는 학습용 서비스. */
@Service
public class MockSalesService {

    private static final LocalDate SEPTEMBER_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEPTEMBER_END = LocalDate.of(2026, 9, 30);
    private static final LocalDate OCTOBER_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCTOBER_END = LocalDate.of(2026, 10, 31);

    // 원천 매출 행이 아니라, 기관·기간별로 집계·정렬이 완료된 고정 결과야.
    private static final Map<Scope, Fixture> FIXTURES = Map.of(
            new Scope("ORG-A", SEPTEMBER_START, SEPTEMBER_END),
            new Fixture(List.of(
                    new CustomerSales("C001", "가람상사", new BigDecimal("500000.50")),
                    new CustomerSales("C002", "나래유통", new BigDecimal("400000.25")),
                    new CustomerSales("C003", "다온상사", new BigDecimal("300000.00")),
                    new CustomerSales("C004", "라온유통", new BigDecimal("200000.00")),
                    new CustomerSales("C005", "마루상사", new BigDecimal("100000.25")),
                    new CustomerSales("C006", "바른유통", new BigDecimal("50000.00"))
            ), List.of(
                    new BigDecimal("500000.50"), new BigDecimal("900000.75"),
                    new BigDecimal("1200000.75"), new BigDecimal("1400000.75"),
                    new BigDecimal("1500001.00"), new BigDecimal("1550001.00")
            )),
            new Scope("ORG-B", SEPTEMBER_START, SEPTEMBER_END),
            new Fixture(List.of(
                    new CustomerSales("C001", "다른기관상사", new BigDecimal("9000000.00"))
            ), List.of(new BigDecimal("9000000.00"))),
            new Scope("ORG-A", OCTOBER_START, OCTOBER_END),
            new Fixture(List.of(), List.of())
    );

    /** 검증된 조건을 받아 준비된 조회 결과를 반환해. 실제 DB·인증 연동은 없어. */
    public TopSalesResult findTopSales(String organizationId, SalesQuery query) {
        Fixture fixture = FIXTURES.get(new Scope(organizationId, query.startDate(), query.endDate()));
        if (fixture == null) {
            throw new IllegalArgumentException("이 기관·기간의 Mock 조회 결과는 준비하지 않았어.");
        }

        // DB의 LIMIT과 상위 N개 합계 결과를 고정 fixture에서 선택해.
        int count = Math.min(query.limit(), fixture.customers().size());
        List<CustomerSales> customers = List.copyOf(fixture.customers().subList(0, count));
        BigDecimal total = count == 0 ? BigDecimal.ZERO : fixture.cumulativeTotals().get(count - 1);
        return new TopSalesResult(organizationId, customers, total);
    }

    private record Scope(String organizationId, LocalDate startDate, LocalDate endDate) {
    }

    private record Fixture(List<CustomerSales> customers, List<BigDecimal> cumulativeTotals) {
    }
}
