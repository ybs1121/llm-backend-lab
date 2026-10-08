package com.example.llmbackend.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockSalesServiceTest {

    private final MockSalesService service = new MockSalesService();

    @Test
    void topFiveTotalExcludesSixthCustomer() {
        TopSalesResult result = service.findTopSales("ORG-A", september(5));

        assertEquals("ORG-A", result.organizationId());
        assertEquals(List.of("C001", "C002", "C003", "C004", "C005"),
                result.customerSales().stream().map(CustomerSales::customerId).toList());
        assertEquals(0, new BigDecimal("1500001.00").compareTo(result.totalSaleAmount()));
    }

    @Test
    void fewerCustomersThanLimitReturnsAvailableCustomersAndTheirTotal() {
        TopSalesResult result = service.findTopSales("ORG-A", september(null));

        assertEquals(6, result.customerSales().size());
        assertEquals(0, new BigDecimal("1550001.00").compareTo(result.totalSaleAmount()));
    }

    @Test
    void sameCustomerCodeInAnotherOrganizationDoesNotMixResults() {
        TopSalesResult result = service.findTopSales("ORG-B", september(5));

        assertEquals("ORG-B", result.organizationId());
        assertEquals(1, result.customerSales().size());
        assertEquals("다른기관상사", result.customerSales().getFirst().customerName());
        assertEquals(0, new BigDecimal("9000000.00").compareTo(result.totalSaleAmount()));
    }

    @Test
    void knownEmptyPeriodReturnsEmptyListAndZero() {
        SalesQuery query = new SalesQuery(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 5);

        TopSalesResult result = service.findTopSales("ORG-A", query);

        assertEquals("ORG-A", result.organizationId());
        assertTrue(result.customerSales().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalSaleAmount()));
    }

    @Test
    void unpreparedPeriodIsNotReportedAsNoSales() {
        SalesQuery query = new SalesQuery(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5);

        assertThrows(IllegalArgumentException.class, () -> service.findTopSales("ORG-A", query));
    }

    private SalesQuery september(Integer limit) {
        return new SalesQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), limit);
    }
}
