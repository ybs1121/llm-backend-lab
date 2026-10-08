package com.example.llmbackend.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.llmbackend.report.TopSalesResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/** 고정 Tool 요청과 결과 기반의 고정 형식 문장을 만드는 Stub. 자연어를 해석하지 않아. */
public class ScriptedSalesChatModel implements ChatModel {

    private final String toolName;
    private final String arguments;
    private final ObjectMapper objectMapper;
    private int callCount;

    public ScriptedSalesChatModel(String toolName, String arguments, ObjectMapper objectMapper) {
        this.toolName = toolName;
        this.arguments = arguments;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        callCount++;
        if (!(prompt.getInstructions().getLast() instanceof ToolResponseMessage resultMessage)) {
            AssistantMessage message = AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "sales-call-1", "function", toolName, arguments)))
                    .build();
            return new ChatResponse(List.of(new Generation(message)));
        }

        ToolResponseMessage.ToolResponse response = resultMessage.getResponses().getFirst();
        if (!response.id().equals("sales-call-1") || !response.name().equals(toolName)) {
            throw new IllegalStateException("Stub이 요청한 Tool 호출과 결과가 일치하지 않아");
        }
        try {
            TopSalesResult result = objectMapper.readValue(response.responseData(), TopSalesResult.class);
            String organizationId = result.organizationId();
            int count = result.customerSales().size();
            String text = count == 0
                    ? "%s 기관의 해당 기간 매출 내역이 없어. 반환한 매출 합계는 0이야.".formatted(organizationId)
                    : "%s 기관의 매출액 상위 %d곳을 조회했어. 반환한 거래처들의 매출 합계는 %s이야."
                    .formatted(organizationId, count, result.totalSaleAmount().toPlainString());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tool 결과 JSON을 읽을 수 없어", exception);
        }
    }

    public int getCallCount() {
        return callCount;
    }
}
