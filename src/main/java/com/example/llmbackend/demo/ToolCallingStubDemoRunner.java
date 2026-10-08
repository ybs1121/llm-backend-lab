package com.example.llmbackend.demo;

import com.example.llmbackend.report.SalesToolCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 고정된 모델 요청으로 Spring AI의 실제 Tool 실행 매니저를 관찰하는 실행용 데모. */
@Component
@Profile("tool-stub-demo")
public class ToolCallingStubDemoRunner implements ApplicationRunner {

    private final SalesToolCallback callback;
    private final ObjectMapper objectMapper;

    public ToolCallingStubDemoRunner(SalesToolCallback callback, ObjectMapper objectMapper) {
        this.callback = callback;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("\n[Tool Stub 데모] 실제 LLM·인증·DB 없이 고정 요청과 Mock 결과로 실행 경로를 확인해.");
        System.out.println("실패는 서버로 예외를 전파하고, 이 데모에서는 후속 모델 호출을 중단해.");
        runScenario("정상 상위 5곳", september("5"), Map.of("organizationId", "ORG-A"), true);
        runScenario("limit 200 차단", september("200"), Map.of("organizationId", "ORG-A"), false);
        runScenario("매출 내역 없음", """
                {"query":{"startDate":"2026-10-01","endDate":"2026-10-31","limit":5}}
                """, Map.of("organizationId", "ORG-A"), true);
        runScenario("기관 정보 누락 차단", september("5"), Map.of(), false);
        runScenario("모델이 다른 기관 ID를 보내도 서버 기관 유지", """
                {"organizationId":"ORG-B",
                 "query":{"startDate":"2026-09-01","endDate":"2026-09-30","limit":5}}
                """, Map.of("organizationId", "ORG-A"), true);
    }

    private void runScenario(String label, String toolArguments, Map<String, Object> serverContext,
                             boolean expectSuccess) {
        System.out.println("\n사례: " + label);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(callback).toolContext(serverContext).build();
        Prompt prompt = new Prompt("매출 조회 실행 경로를 확인해.", options);
        ScriptedSalesChatModel model = new ScriptedSalesChatModel(
                callback.getToolDefinition().name(), toolArguments, objectMapper);
        DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
                .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true)).build();

        System.out.println("서버 Mock 컨텍스트: " + serverContext);
        ChatResponse requested = model.call(prompt);
        AssistantMessage.ToolCall call = requested.getResult().getOutput().getToolCalls().getFirst();
        System.out.println("Stub 모델 요청: " + call.name() + " / " + call.arguments().strip());
        try {
            ToolExecutionResult executed = manager.executeToolCalls(prompt, requested);
            if (!expectSuccess) {
                throw new IllegalStateException("실패해야 하는 사례가 성공했어: " + label);
            }
            ToolResponseMessage resultMessage = (ToolResponseMessage) executed.conversationHistory().getLast();
            System.out.println("Spring AI Tool 결과: " + resultMessage.getResponses().getFirst().responseData());
            ChatResponse finalResponse = model.call(new Prompt(executed.conversationHistory(), options));
            System.out.println("결과를 받은 Stub 문장: " + finalResponse.getResult().getOutput().getText());
            System.out.println("Stub 모델 호출 횟수: " + model.getCallCount());
        } catch (ToolExecutionException exception) {
            if (expectSuccess) {
                throw exception;
            }
            Throwable cause = exception.getCause();
            System.out.println("실행 차단: " + cause.getClass().getSimpleName() + " / " + cause.getMessage());
            System.out.println("후속 Stub 호출 없음. 모델 호출 횟수: " + model.getCallCount());
        }
    }

    private static String september(String limit) {
        return """
                {"query":{"startDate":"2026-09-01","endDate":"2026-09-30","limit":%s}}
                """.formatted(limit);
    }
}
