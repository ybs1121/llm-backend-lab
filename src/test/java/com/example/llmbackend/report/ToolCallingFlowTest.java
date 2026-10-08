package com.example.llmbackend.report;

import com.example.llmbackend.demo.ScriptedSalesChatModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallingFlowTest {

    private static ValidatorFactory validatorFactory;
    private ObjectMapper objectMapper;
    private MockSalesService service;
    private SalesToolCallback callback;
    private ToolCallingChatOptions options;
    private DefaultToolCallingManager manager;

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
        service = spy(new MockSalesService());
        callback = spy(new SalesToolCallback(
                new SalesQueryProcessor(objectMapper, validatorFactory.getValidator()),
                new SalesTools(service), objectMapper));
        options = ToolCallingChatOptions.builder()
                .toolCallbacks(callback).toolContext(Map.of("organizationId", "ORG-A")).build();
        manager = DefaultToolCallingManager.builder()
                .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true)).build();
    }

    @Test
    void managerDispatchesStubRequestAndReturnsCorrelatedResultForSecondModelCall() throws Exception {
        String input = september("5");
        ScriptedSalesChatModel model = new ScriptedSalesChatModel("findTopSales", input, objectMapper);
        Prompt initial = new Prompt("지난달 매출액 상위 5곳을 조회해.", options);
        assertEquals("findTopSales", manager.resolveToolDefinitions(options).getFirst().name());

        ChatResponse request = model.call(initial);
        ToolExecutionResult execution = manager.executeToolCalls(initial, request);

        assertFalse(execution.returnDirect());
        ToolResponseMessage message = (ToolResponseMessage) execution.conversationHistory().getLast();
        assertEquals("sales-call-1", message.getResponses().getFirst().id());
        assertEquals("findTopSales", message.getResponses().getFirst().name());
        JsonNode result = objectMapper.readTree(message.getResponses().getFirst().responseData());
        assertEquals("ORG-A", result.path("organizationId").asText());
        assertEquals(5, result.path("customerSales").size());
        assertEquals(0, new BigDecimal("1500001.00").compareTo(result.path("totalSaleAmount").decimalValue()));
        verify(callback).call(eq(input), argThat((ToolContext context) ->
                "ORG-A".equals(context.getContext().get("organizationId"))));
        verify(service).findTopSales(eq("ORG-A"), any(SalesQuery.class));

        ChatResponse answer = model.call(new Prompt(execution.conversationHistory(), options));
        assertFalse(answer.hasToolCalls());
        assertTrue(answer.getResult().getOutput().getText().contains("1500001"));
        assertEquals(2, model.getCallCount());
    }

    @Test
    void managerPropagatesInvalidArgumentsWithoutQueryOrSecondModelCall() {
        ScriptedSalesChatModel model = new ScriptedSalesChatModel("findTopSales", september("200"), objectMapper);
        Prompt initial = new Prompt("매출액 상위 200곳을 조회해.", options);
        ChatResponse request = model.call(initial);

        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> manager.executeToolCalls(initial, request));

        assertInstanceOf(ConstraintViolationException.class, exception.getCause());
        verifyNoInteractions(service);
        assertEquals(1, model.getCallCount());
    }

    private static String september(String limit) {
        return """
                {"query":{"startDate":"2026-09-01","endDate":"2026-09-30","limit":%s}}
                """.formatted(limit);
    }
}
