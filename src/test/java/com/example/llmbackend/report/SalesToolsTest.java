package com.example.llmbackend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 검증된 DTO를 받는 Tool 본문의 계약 테스트. 모델 연동 전체를 검증하지는 않아. */
class SalesToolsTest {

    private static ValidatorFactory validatorFactory;
    private MockSalesService salesService;
    private SalesTools tools;
    private SalesQuery validatedQuery;

    @BeforeAll
    static void initializeValidation() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidation() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        salesService = mock(MockSalesService.class);
        tools = new SalesTools(salesService);
        SalesQueryProcessor processor = new SalesQueryProcessor(
                new ObjectMapper().findAndRegisterModules(), validatorFactory.getValidator());
        validatedQuery = processor.parseAndValidate("""
                {"startDate":"2026-09-01","endDate":"2026-09-30","limit":5}
                """);
    }

    @Test
    void passesSameValidatedQueryAndServerOrganizationToService() {
        TopSalesResult expected = new TopSalesResult("ORG-A", List.of(
                new CustomerSales("C001", "가람상사", new BigDecimal("500000.50"))
        ), new BigDecimal("500000.50"));
        when(salesService.findTopSales(eq("ORG-A"), same(validatedQuery))).thenReturn(expected);

        TopSalesResult result = tools.findTopSales(validatedQuery,
                new ToolContext(Map.of("organizationId", "ORG-A")));

        assertSame(expected, result);
        verify(salesService).findTopSales(eq("ORG-A"), same(validatedQuery));
        verifyNoMoreInteractions(salesService);
    }

    @ParameterizedTest
    @MethodSource("invalidOrganizationContexts")
    void missingOrInvalidOrganizationBlocksServiceCall(Map<String, Object> context) {
        assertThrows(IllegalStateException.class,
                () -> tools.findTopSales(validatedQuery, new ToolContext(context)));

        verifyNoInteractions(salesService);
    }

    static Stream<Map<String, Object>> invalidOrganizationContexts() {
        return Stream.of(
                Map.<String, Object>of(),
                Collections.<String, Object>singletonMap("organizationId", null),
                Map.<String, Object>of("organizationId", ""),
                Map.<String, Object>of("organizationId", "   "),
                Map.<String, Object>of("organizationId", 123)
        );
    }

    @Test
    void serviceFailureIsPropagatedInsteadOfReturningEmptySales() {
        IllegalStateException failure = new IllegalStateException("Mock 조회 실패");
        when(salesService.findTopSales(eq("ORG-A"), same(validatedQuery))).thenThrow(failure);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> tools.findTopSales(validatedQuery,
                        new ToolContext(Map.of("organizationId", "ORG-A"))));

        assertSame(failure, actual);
        verify(salesService).findTopSales(eq("ORG-A"), same(validatedQuery));
    }
}
