package com.example.llmbackend.trade;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 모델에는 ToolCallbacks.from(이 객체)로 만든 초안 Tool만 제공한다.
 * 승인·저장은 사용자 승인 경로에서 TradeService를 호출하며 여기에는 노출하지 않는다.
 * 이 클래스는 이미 존재하는 서비스의 검증·기관·버전 검사 경로를 그대로 사용한다.
 */
@Component
public class TradeDraftTools {

    private final TradeService service;

    public TradeDraftTools(TradeService service) {
        this.service = service;
    }

    @Tool(description = """
            거래 상대방 ID, 1 이상 정수 금액, 거래일로 거래 내역 초안을 만든다.
            거래일은 YYYY-MM-DD 형식이다. 기관과 초안 ID·버전은 서버가 정한다.
            반환 결과는 사용자 확인용 초안이며, 거래 내역을 최종 저장하지 않는다.
            """)
    public TradeDraft createTradeDraft(
            @ToolParam(description = "거래 상대방 ID") String clientId,
            @ToolParam(description = "1 이상 정수 금액. 상한 제한 없음") BigDecimal saleAmount,
            @ToolParam(description = "거래일, YYYY-MM-DD") LocalDate tradeDate,
            ToolContext serverContext) {
        return service.createDraft(clientId, saleAmount, tradeDate, serverContext);
    }

    @Tool(description = """
            같은 기관의 미저장 거래 내역 초안을 수정한다.
            현재 초안 ID와 사용자가 확인한 버전, 수정할 업무 필드 전체를 전달한다.
            요청 버전이 현재 버전과 같아야 하고, 수정 후 초안 버전이 증가한다.
            변경된 초안을 사용자에게 다시 확인받아야 하며, 거래 내역을 최종 저장하지 않는다.
            """)
    public TradeDraft updateTradeDraft(
            @ToolParam(description = "수정할 초안 ID") String draftId,
            @ToolParam(description = "확인한 초안의 버전") long expectedVersion,
            @ToolParam(description = "수정 후 거래 상대방 ID") String clientId,
            @ToolParam(description = "수정 후 1 이상 정수 금액") BigDecimal saleAmount,
            @ToolParam(description = "수정 후 거래일, YYYY-MM-DD") LocalDate tradeDate,
            ToolContext serverContext) {
        return service.updateDraft(draftId, expectedVersion, clientId, saleAmount, tradeDate, serverContext);
    }
}
