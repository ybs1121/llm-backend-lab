package com.example.llmbackend.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 고정 Tool 요청을 실제 Spring AI 매니저로 실행한다. 자연어 해석과 실제 승인은 미검증이다. */
class TradeDraftToolCallingTest {

    private static ValidatorFactory validatorFactory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private TradeService service;
    private DefaultToolCallingManager manager;
    private ToolCallingChatOptions options;

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
        service = spy(new TradeService(validatorFactory.getValidator()));
        options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(new TradeDraftTools(service)))
                .toolContext(Map.of("organizationId", "ORG-A"))
                .build();
        manager = DefaultToolCallingManager.builder()
                .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true))
                .build();
    }

    @Test
    void offeredToolsContainOnlyDraftCreationAndUpdate() throws Exception {
        var definitions = manager.resolveToolDefinitions(options);
        assertEquals(Set.of("createTradeDraft", "updateTradeDraft"),
                definitions.stream().map(definition -> definition.name()).collect(Collectors.toSet()));
        for (var definition : definitions) {
            JsonNode properties = mapper.readTree(definition.inputSchema()).path("properties");
            assertTrue(properties.isObject());
            assertFalse(properties.has("organizationId"));
            assertFalse(properties.has("serverContext"));
            assertFalse(properties.has("savedTradeId"));
            assertFalse(properties.has("approved"));
        }
    }

    @Test
    void managerCreatesDraftWithoutSavingAndApprovalIsSeparateServiceCall() throws Exception {
        String input = """
                {"clientId":"C001","saleAmount":1000000,"tradeDate":"2026-10-09"}
                """;
        ToolExecutionResult execution = execute("createTradeDraft", input, options);
        ToolResponseMessage message = (ToolResponseMessage) execution.conversationHistory().getLast();
        assertEquals("trade-call-1", message.getResponses().getFirst().id());
        assertEquals("createTradeDraft", message.getResponses().getFirst().name());
        TradeDraft draft = mapper.readValue(message.getResponses().getFirst().responseData(), TradeDraft.class);

        assertEquals("ORG-A", draft.organizationId());
        assertEquals("C001", draft.clientId());
        assertEquals(0, new BigDecimal("1000000").compareTo(draft.saleAmount()));
        assertEquals(LocalDate.of(2026, 10, 9), draft.tradeDate());
        assertNull(draft.savedTradeId());
        assertEquals(0, savedCount());
        verify(service, never()).approveAndSave(anyString(), anyLong(), any());

        // 실제 UI 대신 별도의 사용자 승인 경로에서 호출하는 상황을 흉내 내.
        ToolContext serverContext = new ToolContext(Map.of("organizationId", "ORG-A"));
        SavedTrade saved = service.approveAndSave(draft.draftId(), draft.version(), serverContext);
        assertEquals(saved, service.approveAndSave(draft.draftId(), draft.version(), serverContext));
        assertEquals(1, savedCount());
    }

    @Test
    void modelSuppliedOrganizationAndApprovalFlagCannotSaveOrChangeScope() throws Exception {
        TradeDraft draft = resultOf(execute("createTradeDraft", """
                {"clientId":"C001","saleAmount":1000000,"tradeDate":"2026-10-09",
                 "organizationId":"ORG-B","approved":true,"savedTradeId":"fake-trade"}
                """, options));

        assertEquals("ORG-A", draft.organizationId());
        assertNull(draft.savedTradeId());
        assertEquals(0, savedCount());
        verify(service, never()).approveAndSave(anyString(), anyLong(), any());
    }

    @Test
    void managerUpdatesDraftAndInvalidatesOldApprovalVersion() throws Exception {
        TradeDraft original = resultOf(execute("createTradeDraft", """
                {"clientId":"C001","saleAmount":1000000,"tradeDate":"2026-10-09"}
                """, options));
        String updateJson = mapper.writeValueAsString(Map.of(
                "draftId", original.draftId(), "expectedVersion", original.version(),
                "clientId", "C002", "saleAmount", 1200000, "tradeDate", "2026-10-10"));
        TradeDraft updated = resultOf(execute("updateTradeDraft", updateJson, options));

        assertEquals(original.draftId(), updated.draftId());
        assertEquals(2L, updated.version());
        assertEquals("C002", updated.clientId());
        assertEquals(0, new BigDecimal("1200000").compareTo(updated.saleAmount()));
        assertEquals(0, savedCount());
        assertThrows(IllegalStateException.class,
                () -> service.approveAndSave(original.draftId(), original.version(),
                        new ToolContext(Map.of("organizationId", "ORG-A"))));
        assertEquals(0, savedCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5"})
    void invalidAmountFailsThroughServiceValidation(String amount) {
        String input = """
                {"clientId":"C001","saleAmount":%s,"tradeDate":"2026-10-09"}
                """.formatted(amount);
        assertThrows(ToolExecutionException.class,
                () -> execute("createTradeDraft", input, options));
        assertEquals(0, savedCount());
    }

    @Test
    void emptyToolContextBlocksExecutionBeforeServiceCall() {
        ToolCallingChatOptions noOrganization = ToolCallingChatOptions.builder()
                .toolCallbacks(options.getToolCallbacks()).toolContext(Map.of()).build();
        assertThrows(IllegalArgumentException.class,
                () -> execute("createTradeDraft", """
                        {"clientId":"C001","saleAmount":1000000,"tradeDate":"2026-10-09"}
                        """, noOrganization));
        assertEquals(0, savedCount());
        verify(service, never()).createDraft(anyString(), any(), any(), any());
    }

    @Test
    void contextWithoutOrganizationFailsThroughServiceCheck() {
        ToolCallingChatOptions noOrganization = ToolCallingChatOptions.builder()
                .toolCallbacks(options.getToolCallbacks())
                .toolContext(Map.of("requestId", "mock-request"))
                .build();
        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> execute("createTradeDraft", """
                        {"clientId":"C001","saleAmount":1000000,"tradeDate":"2026-10-09"}
                        """, noOrganization));
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(0, savedCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approveAndSave", "saveTrade"})
    void unregisteredSaveRequestCannotExecuteEvenWithCorrectDraftAndVersion(String toolName) throws Exception {
        TradeDraft draft = resultOf(execute("createTradeDraft", """
                {"clientId":"C001","saleAmount":1000000,"tradeDate":"2026-10-09"}
                """, options));
        String input = mapper.writeValueAsString(Map.of(
                "draftId", draft.draftId(), "expectedVersion", draft.version(), "approved", true));

        assertThrows(IllegalStateException.class, () -> execute(toolName, input, options));
        assertEquals(0, savedCount());
        verify(service, never()).approveAndSave(anyString(), anyLong(), any());
    }

    private ToolExecutionResult execute(String toolName, String arguments, ToolCallingChatOptions toolOptions) {
        Prompt prompt = new Prompt("거래 내역 실행 경로를 확인해.", toolOptions);
        AssistantMessage request = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "trade-call-1", "function", toolName, arguments)))
                .build();
        return manager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(request))));
    }

    private TradeDraft resultOf(ToolExecutionResult execution) throws Exception {
        ToolResponseMessage message = (ToolResponseMessage) execution.conversationHistory().getLast();
        return mapper.readValue(message.getResponses().getFirst().responseData(), TradeDraft.class);
    }

    private int savedCount() {
        Map<?, ?> saved = (Map<?, ?>) ReflectionTestUtils.getField(service, "savedTrades");
        assertNotNull(saved);
        return saved.size();
    }
}
