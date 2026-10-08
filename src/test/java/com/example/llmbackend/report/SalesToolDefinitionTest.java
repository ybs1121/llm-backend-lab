package com.example.llmbackend.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SalesToolDefinitionTest {

    @Test
    void modelSchemaContainsOnlyQueryAndExcludesServerContext() throws Exception {
        SalesTools tools = new SalesTools(mock(MockSalesService.class));
        ToolCallback[] callbacks = ToolCallbacks.from(tools);

        assertEquals(1, callbacks.length);
        assertEquals("findTopSales", callbacks[0].getToolDefinition().name());
        JsonNode schema = new ObjectMapper().readTree(callbacks[0].getToolDefinition().inputSchema());
        assertEquals(1, schema.path("properties").size());
        assertTrue(schema.path("properties").has("query"));
        assertFalse(schema.toString().contains("organizationId"));
        assertFalse(schema.toString().contains("toolContext"));

        JsonNode querySchema = schema.path("properties").path("query");
        assertTrue(querySchema.path("properties").has("startDate"));
        assertTrue(querySchema.path("properties").has("endDate"));
        assertTrue(querySchema.path("properties").has("limit"));
        assertTrue(querySchema.path("required").toString().contains("startDate"));
        assertTrue(querySchema.path("required").toString().contains("endDate"));
        assertFalse(querySchema.path("required").toString().contains("limit"));
    }
}
