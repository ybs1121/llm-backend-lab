package com.example.llmbackend.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesToolCallbackTest {

    private static ValidatorFactory validatorFactory;
    private ObjectMapper objectMapper;
    private SalesQueryProcessor processor;
    private SalesTools tools;
    private MockSalesService salesService;
    private SalesToolCallback callback;
    private final ToolContext context = new ToolContext(Map.of("organizationId", "ORG-A"));

    @BeforeAll
    static void initializeValidation() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidation() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        processor = spy(new SalesQueryProcessor(objectMapper, validatorFactory.getValidator()));
        salesService = spy(new MockSalesService());
        tools = spy(new SalesTools(salesService));
        callback = new SalesToolCallback(processor, tools, objectMapper);
    }

    @Test
    void validatesOnceThenPassesSameQueryToToolAndServiceAndReturnsJson() throws Exception {
        JsonNode result = objectMapper.readTree(callback.call(september("5"), context));

        assertEquals("ORG-A", result.path("organizationId").asText());
        assertEquals(5, result.path("customerSales").size());
        assertEquals("C001", result.path("customerSales").get(0).path("customerId").asText());
        assertEquals(0, new BigDecimal("1500001.00").compareTo(result.path("totalSaleAmount").decimalValue()));

        ArgumentCaptor<SalesQuery> queryCaptor = ArgumentCaptor.forClass(SalesQuery.class);
        verify(salesService).findTopSales(eq("ORG-A"), queryCaptor.capture());
        SalesQuery sameQuery = queryCaptor.getValue();
        InOrder order = inOrder(processor, tools, salesService);
        order.verify(processor).parseAndValidate(anyString());
        order.verify(tools).findTopSales(same(sameQuery), same(context));
        order.verify(salesService).findTopSales(eq("ORG-A"), same(sameQuery));
        verifyNoMoreInteractions(processor, tools, salesService);
    }

    @ParameterizedTest
    @MethodSource("rejectedInputs")
    void invalidArgumentsNeverReachToolOrService(String input, Class<? extends Throwable> causeType) {
        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> callback.call(input, context));

        assertInstanceOf(causeType, exception.getCause());
        verifyNoInteractions(tools, salesService);
    }

    static Stream<Arguments> rejectedInputs() {
        return Stream.of(
                Arguments.of(september("200"), ConstraintViolationException.class),
                Arguments.of(september("0"), ConstraintViolationException.class),
                Arguments.of(september("-1"), ConstraintViolationException.class),
                Arguments.of(september("5.9"), JsonProcessingException.class),
                Arguments.of(september("\"5\""), JsonProcessingException.class),
                Arguments.of(september("\"\""), JsonProcessingException.class),
                Arguments.of(september("true"), JsonProcessingException.class),
                Arguments.of("""
                        {"query":{"startDate":null,"endDate":"2026-09-30","limit":5}}
                        """, ConstraintViolationException.class),
                Arguments.of("""
                        {"query":{"startDate":"2026-02-30","endDate":"2026-09-30","limit":5}}
                        """, JsonProcessingException.class),
                Arguments.of("""
                        {"query":{"startDate":"2026-09-30","endDate":"2026-09-01","limit":5}}
                        """, IllegalArgumentException.class),
                Arguments.of("{", JsonProcessingException.class),
                Arguments.of("{}", IllegalArgumentException.class),
                Arguments.of("{\"query\":null}", IllegalArgumentException.class),
                Arguments.of("{\"query\":[]}", IllegalArgumentException.class),
                Arguments.of("[]", IllegalArgumentException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("defaultLimitInputs")
    void omittedOrNullLimitUsesProcessorDefault(String input) throws Exception {
        JsonNode result = objectMapper.readTree(callback.call(input, context));

        ArgumentCaptor<SalesQuery> queryCaptor = ArgumentCaptor.forClass(SalesQuery.class);
        verify(salesService).findTopSales(eq("ORG-A"), queryCaptor.capture());
        assertEquals(10, queryCaptor.getValue().limit());
        assertEquals(6, result.path("customerSales").size());
        assertEquals(0, new BigDecimal("1550001.00").compareTo(result.path("totalSaleAmount").decimalValue()));
    }

    static Stream<String> defaultLimitInputs() {
        return Stream.of(september("null"), """
                {"query":{"startDate":"2026-09-01","endDate":"2026-09-30"}}
                """);
    }

    @Test
    void knownEmptySalesReturnsValidJsonWithEmptyListAndZero() throws Exception {
        JsonNode result = objectMapper.readTree(callback.call("""
                {"query":{"startDate":"2026-10-01","endDate":"2026-10-31","limit":5}}
                """, context));

        assertEquals("ORG-A", result.path("organizationId").asText());
        assertTrue(result.path("customerSales").isArray());
        assertTrue(result.path("customerSales").isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.path("totalSaleAmount").decimalValue()));
    }

    @Test
    void callWithoutServerContextIsRejectedBeforeValidationOrLookup() {
        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> callback.call(september("5")));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        verifyNoInteractions(processor, tools, salesService);
    }

    @Test
    void contextWithoutOrganizationCannotCallService() {
        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> callback.call(september("5"), new ToolContext(Map.of())));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        verifyNoInteractions(salesService);
    }

    @Test
    void modelSuppliedOrganizationCannotOverrideServerContext() throws Exception {
        String input = """
                {"organizationId":"ORG-B","toolContext":{"organizationId":"ORG-B"},
                 "query":{"startDate":"2026-09-01","endDate":"2026-09-30","limit":5}}
                """;

        JsonNode result = objectMapper.readTree(callback.call(input, context));

        assertEquals("ORG-A", result.path("organizationId").asText());
        assertEquals("가람상사", result.path("customerSales").get(0).path("customerName").asText());
        verify(salesService).findTopSales(eq("ORG-A"), any(SalesQuery.class));
        verify(salesService, never()).findTopSales(eq("ORG-B"), any(SalesQuery.class));
    }

    @Test
    void serviceErrorKeepsItsCauseInsteadOfReturningZeroSales() {
        IllegalStateException failure = new IllegalStateException("Mock 조회 실패");
        doThrow(failure).when(salesService).findTopSales(eq("ORG-A"), any(SalesQuery.class));

        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> callback.call(september("5"), context));

        assertSame(failure, exception.getCause());
        verify(salesService).findTopSales(eq("ORG-A"), any(SalesQuery.class));
    }

    private static String september(String rawLimit) {
        return """
                {"query":{"startDate":"2026-09-01","endDate":"2026-09-30","limit":%s}}
                """.formatted(rawLimit);
    }
}
