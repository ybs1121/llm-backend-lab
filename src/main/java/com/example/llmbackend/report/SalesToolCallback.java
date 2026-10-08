package com.example.llmbackend.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.stereotype.Component;

/**
 * Spring AI가 전달하는 Tool 인자 JSON과 서버의 검증·조회 흐름을 연결해.
 * 모델 연동 시에는 이 Callback을 등록해야 Processor를 거치는 경로가 유지돼.
 */
@Component
public class SalesToolCallback implements ToolCallback {

    private final SalesQueryProcessor processor;
    private final SalesTools tools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public SalesToolCallback(SalesQueryProcessor processor, SalesTools tools, ObjectMapper objectMapper) {
        this.processor = processor;
        this.tools = tools;
        this.objectMapper = objectMapper;
        // @Tool에서 이름·설명·입력 Schema만 가져와. 실행은 아래 경로에서 직접 제어해.
        this.toolDefinition = ToolCallbacks.from(tools)[0].getToolDefinition();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            if (toolContext == null) {
                throw new IllegalStateException("서버 ToolContext가 없어 조회할 수 없어");
            }
            JsonNode arguments = objectMapper.readTree(toolInput);
            if (arguments == null || !arguments.isObject() || !arguments.path("query").isObject()) {
                throw new IllegalArgumentException("Tool 인자에는 query 객체가 필요해");
            }

            TopSalesResult result = executeQuery(arguments.get("query").toString(), toolContext);
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException | RuntimeException exception) {
            // Spring AI의 Tool 실패 처리에 원인을 보존해서 전달해.
            throw new ToolExecutionException(toolDefinition, exception);
        }
    }

    private TopSalesResult executeQuery(String queryJson, ToolContext toolContext)
            throws JsonProcessingException {
        SalesQuery salesQuery = processor.parseAndValidate(queryJson);
        return tools.findTopSales(salesQuery, toolContext);
    }
}
