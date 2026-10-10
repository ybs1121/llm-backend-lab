package com.example.llmbackend.trade;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 단일 프로세스의 공유 서비스 인스턴스에서만 저장과 중복 요청을 통제하는 실습.
 * 프로세스 재시작이나 여러 서버 사이의 멱등성은 보장하지 않는다.
 * ToolContext에는 현재 실제 인증 대신 서버가 지정한 Mock 기관을 전달한다.
 */
@Service
public class TradeService {

    private final Validator validator;
    private final Map<String, TradeDraft> drafts = new HashMap<>();
    private final Map<String, SavedTrade> savedTrades = new HashMap<>();

    public TradeService(Validator validator) {
        this.validator = validator;
    }

    /** 검증한 초안만 보관한다. 거래 내역은 아직 생성하지 않는다. */
    public synchronized TradeDraft createDraft(
            String clientId, BigDecimal saleAmount, LocalDate tradeDate,
            ToolContext serverContext) {
        String organizationId = organizationIdFrom(serverContext);
        TradeDraft draft = new TradeDraft(organizationId, clientId, saleAmount, tradeDate,
                UUID.randomUUID().toString(), 1L, null);
        validateDraft(draft);
        drafts.put(draft.draftId(), draft);
        return draft;
    }

    /**
     * 같은 기관의 미저장 초안만 수정한다. 내용 수정 시 버전을 올린다.
     */
    public synchronized TradeDraft updateDraft(
            String draftId, long expectedVersion,
            String clientId, BigDecimal saleAmount, LocalDate tradeDate,
            ToolContext serverContext) {
        TradeDraft current = findAuthorizedDraft(draftId, expectedVersion, serverContext);
        if (current.savedTradeId() != null) {
            throw new IllegalStateException("이미 저장한 초안은 수정할 수 없어");
        }
        TradeDraft updated = new TradeDraft(current.organizationId(), clientId, saleAmount,
                tradeDate, current.draftId(), Math.addExact(current.version(), 1L), null);
        validateDraft(updated);
        drafts.put(draftId, updated);
        return updated;
    }

    /**
     * 승인 대상 확인 후 서버 초안의 내용으로 저장한다.
     * 같은 승인 재요청은 기존 결과를 반환한다. 승인된 내용의 버전은 유지한다.
     */
    public synchronized SavedTrade approveAndSave(
            String draftId, long expectedVersion, ToolContext serverContext) {
        // 저장 완료 여부보다 기관·버전을 먼저 검사해서 결과 유출과 오래된 승인을 막아.
        TradeDraft draft = findAuthorizedDraft(draftId, expectedVersion, serverContext);
        if (draft.savedTradeId() != null) {
            SavedTrade existing = savedTrades.get(draft.savedTradeId());
            if (existing == null) {
                throw new IllegalStateException("초안에 연결된 저장 결과를 찾을 수 없어");
            }
            return existing;
        }

        String tradeId = UUID.randomUUID().toString();
        SavedTrade saved = new SavedTrade(tradeId, draft.organizationId(), draft.clientId(),
                draft.saleAmount(), draft.tradeDate());
        TradeDraft completed = new TradeDraft(draft.organizationId(), draft.clientId(),
                draft.saleAmount(), draft.tradeDate(), draft.draftId(), draft.version(), tradeId);

        savedTrades.put(tradeId, saved);
        drafts.put(draftId, completed);
        return saved;
    }

    private TradeDraft findAuthorizedDraft(
            String draftId, long expectedVersion, ToolContext serverContext) {
        String organizationId = organizationIdFrom(serverContext);
        TradeDraft draft = drafts.get(draftId);
        // 존재하지 않는 초안과 다른 기관의 초안은 같은 오류로 처리해.
        if (draft == null || !organizationId.equals(draft.organizationId())) {
            throw new IllegalArgumentException("접근 가능한 초안을 찾을 수 없어");
        }
        if (draft.version() != expectedVersion) {
            throw new IllegalStateException("초안 버전이 달라. 최신 내용을 확인하고 다시 요청해야 해");
        }
        return draft;
    }

    private String organizationIdFrom(ToolContext serverContext) {
        if (serverContext == null) {
            throw new IllegalStateException("서버의 기관 정보가 없어 처리할 수 없어");
        }
        Object value = serverContext.getContext().get("organizationId");
        if (!(value instanceof String organizationId) || organizationId.isBlank()) {
            throw new IllegalStateException("서버의 기관 정보가 없어 처리할 수 없어");
        }
        return organizationId;
    }

    private void validateDraft(TradeDraft draft) {
        var violations = validator.validate(draft);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        draft.validateWholeAmount();
    }
}
