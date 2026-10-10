package com.example.llmbackend.trade;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 서버의 초안·승인 계약을 검증한다. 실제 인증·모델·DB 검증은 아니다. */
class TradeServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 9);
    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final ToolContext ORG_A = new ToolContext(Map.of("organizationId", "ORG-A"));
    private static final ToolContext ORG_B = new ToolContext(Map.of("organizationId", "ORG-B"));
    private static ValidatorFactory validatorFactory;
    private TradeService service;

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
        service = new TradeService(validatorFactory.getValidator());
    }

    @Test
    void creatingDraftDoesNotSaveTrade() {
        TradeDraft draft = createDraft();

        assertEquals("ORG-A", draft.organizationId());
        assertEquals("C001", draft.clientId());
        assertEquals(AMOUNT, draft.saleAmount());
        assertEquals(DATE, draft.tradeDate());
        assertNotNull(draft.draftId());
        assertEquals(1L, draft.version());
        assertNull(draft.savedTradeId());
        assertEquals(0, storedCount("savedTrades"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "0.5"})
    void amountBelowOneIsRejected(String amount) {
        assertThrows(ConstraintViolationException.class,
                () -> service.createDraft("C001", new BigDecimal(amount), DATE, ORG_A));
        assertEquals(0, storedCount("drafts"));
        assertEquals(0, storedCount("savedTrades"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1000000.01"})
    void fractionalAmountIsRejected(String amount) {
        assertThrows(IllegalArgumentException.class,
                () -> service.createDraft("C001", new BigDecimal(amount), DATE, ORG_A));
        assertEquals(0, storedCount("drafts"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1000000.00", "99999999999999999999999999999999999999"})
    void wholeAmountHasNoBusinessUpperLimit(String amount) {
        BigDecimal expected = new BigDecimal(amount);
        TradeDraft draft = service.createDraft("C001", expected, DATE, ORG_A);
        SavedTrade saved = service.approveAndSave(draft.draftId(), draft.version(), ORG_A);

        assertEquals(expected, saved.saleAmount());
    }

    @ParameterizedTest
    @MethodSource("missingBusinessFields")
    void missingBusinessFieldsAreRejected(String clientId, BigDecimal amount, LocalDate date) {
        assertThrows(ConstraintViolationException.class,
                () -> service.createDraft(clientId, amount, date, ORG_A));
        assertEquals(0, storedCount("drafts"));
    }

    static Stream<Arguments> missingBusinessFields() {
        return Stream.of(
                Arguments.of(null, AMOUNT, DATE),
                Arguments.of("C001", null, DATE),
                Arguments.of("C001", AMOUNT, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void blankClientIdIsRejected(String clientId) {
        assertThrows(ConstraintViolationException.class,
                () -> service.createDraft(clientId, AMOUNT, DATE, ORG_A));
    }

    @ParameterizedTest
    @MethodSource("invalidContexts")
    void invalidServerOrganizationBlocksCreationAndApproval(ToolContext context) {
        assertThrows(IllegalStateException.class,
                () -> service.createDraft("C001", AMOUNT, DATE, context));
        TradeDraft draft = createDraft();
        assertThrows(IllegalStateException.class,
                () -> service.approveAndSave(draft.draftId(), draft.version(), context));
        assertEquals(0, storedCount("savedTrades"));
    }

    static Stream<ToolContext> invalidContexts() {
        return Stream.of(
                null,
                new ToolContext(Map.of()),
                new ToolContext(Collections.singletonMap("organizationId", null)),
                new ToolContext(Map.of("organizationId", "")),
                new ToolContext(Map.of("organizationId", "   ")),
                new ToolContext(Map.of("organizationId", 123)));
    }

    @Test
    void approvalSavesServerDraftContentAndReplaysSameResult() {
        TradeDraft draft = createDraft();
        SavedTrade first = service.approveAndSave(draft.draftId(), draft.version(), ORG_A);
        SavedTrade repeated = service.approveAndSave(draft.draftId(), draft.version(), ORG_A);

        assertEquals("ORG-A", first.organizationId());
        assertEquals(draft.clientId(), first.clientId());
        assertEquals(draft.saleAmount(), first.saleAmount());
        assertEquals(draft.tradeDate(), first.tradeDate());
        assertEquals(first, repeated);
        assertEquals(1, storedCount("savedTrades"));
    }

    @Test
    void updateIncrementsVersionAndOnlyNewVersionCanBeApproved() {
        TradeDraft original = createDraft();
        TradeDraft updated = service.updateDraft(original.draftId(), original.version(),
                "C002", new BigDecimal("1200000"), DATE.plusDays(1), ORG_A);

        assertEquals(original.draftId(), updated.draftId());
        assertEquals(2L, updated.version());
        assertEquals(1L, original.version());
        assertThrows(IllegalStateException.class,
                () -> service.approveAndSave(original.draftId(), original.version(), ORG_A));
        assertEquals(0, storedCount("savedTrades"));

        SavedTrade saved = service.approveAndSave(updated.draftId(), updated.version(), ORG_A);
        assertEquals(updated.clientId(), saved.clientId());
        assertEquals(updated.saleAmount(), saved.saleAmount());
        assertEquals(updated.tradeDate(), saved.tradeDate());
    }

    @Test
    void outdatedUpdateDoesNotOverwriteLatestDraft() {
        TradeDraft original = createDraft();
        TradeDraft updated = service.updateDraft(original.draftId(), original.version(),
                "C002", new BigDecimal("1200000"), DATE, ORG_A);

        assertThrows(IllegalStateException.class,
                () -> service.updateDraft(original.draftId(), original.version(),
                        "C003", new BigDecimal("1500000"), DATE, ORG_A));

        SavedTrade saved = service.approveAndSave(updated.draftId(), updated.version(), ORG_A);
        assertEquals("C002", saved.clientId());
        assertEquals(new BigDecimal("1200000"), saved.saleAmount());
    }

    @Test
    void invalidUpdateLeavesOriginalDraftApprovable() {
        TradeDraft original = createDraft();
        assertThrows(ConstraintViolationException.class,
                () -> service.updateDraft(original.draftId(), original.version(),
                        "C002", BigDecimal.ZERO, DATE, ORG_A));

        SavedTrade saved = service.approveAndSave(original.draftId(), original.version(), ORG_A);
        assertEquals(original.clientId(), saved.clientId());
        assertEquals(original.saleAmount(), saved.saleAmount());
    }

    @Test
    void differentOrganizationCannotUpdateOrApproveDraft() {
        TradeDraft draft = createDraft();
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDraft(draft.draftId(), draft.version(),
                        "C002", AMOUNT, DATE, ORG_B));
        assertThrows(IllegalArgumentException.class,
                () -> service.approveAndSave(draft.draftId(), draft.version(), ORG_B));
        assertEquals(0, storedCount("savedTrades"));

        assertEquals("C001",
                service.approveAndSave(draft.draftId(), draft.version(), ORG_A).clientId());
    }

    @Test
    void differentOrganizationCannotReadSavedResultThroughReplay() {
        TradeDraft draft = createDraft();
        service.approveAndSave(draft.draftId(), draft.version(), ORG_A);

        assertThrows(IllegalArgumentException.class,
                () -> service.approveAndSave(draft.draftId(), draft.version(), ORG_B));
        assertEquals(1, storedCount("savedTrades"));
    }

    @Test
    void wrongVersionCannotReadSavedResultThroughReplay() {
        TradeDraft draft = createDraft();
        SavedTrade saved = service.approveAndSave(draft.draftId(), draft.version(), ORG_A);

        assertThrows(IllegalStateException.class,
                () -> service.approveAndSave(draft.draftId(), draft.version() + 1, ORG_A));
        assertEquals(saved,
                service.approveAndSave(draft.draftId(), draft.version(), ORG_A));
    }

    @Test
    void savedDraftCannotBeUpdated() {
        TradeDraft draft = createDraft();
        SavedTrade saved = service.approveAndSave(draft.draftId(), draft.version(), ORG_A);

        assertThrows(IllegalStateException.class,
                () -> service.updateDraft(draft.draftId(), draft.version(),
                        "C002", new BigDecimal("1200000"), DATE, ORG_A));
        assertEquals(saved,
                service.approveAndSave(draft.draftId(), draft.version(), ORG_A));
    }

    @Test
    void unknownDraftCannotBeApprovedOrUpdated() {
        assertThrows(IllegalArgumentException.class,
                () -> service.approveAndSave("unknown", 1, ORG_A));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDraft("unknown", 1, "C001", AMOUNT, DATE, ORG_A));
        assertEquals(0, storedCount("savedTrades"));
    }

    @Test
    void concurrentApprovalsCreateOnlyOneTrade() throws Exception {
        TradeDraft draft = createDraft();
        int workers = 12;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        var results = new ArrayList<Future<SavedTrade>>();

        try (var executor = Executors.newFixedThreadPool(workers)) {
            try {
                for (int i = 0; i < workers; i++) {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("동시 요청 시작 신호 시간 초과");
                        }
                        return service.approveAndSave(draft.draftId(), draft.version(), ORG_A);
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            var tradeIds = new HashSet<String>();
            for (Future<SavedTrade> result : results) {
                tradeIds.add(result.get(10, TimeUnit.SECONDS).tradeId());
            }
            assertEquals(1, tradeIds.size());
            assertEquals(1, storedCount("savedTrades"));
        }
    }

    private TradeDraft createDraft() {
        return service.createDraft("C001", AMOUNT, DATE, ORG_A);
    }

    // 저장소 조회 API를 추가하지 않고, 실제 생성 건수와 승인 전 미저장을 확인한다.
    private int storedCount(String fieldName) {
        Map<?, ?> stored = (Map<?, ?>) ReflectionTestUtils.getField(service, fieldName);
        assertNotNull(stored);
        return stored.size();
    }
}
