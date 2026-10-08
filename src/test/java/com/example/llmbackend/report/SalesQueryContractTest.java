package com.example.llmbackend.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SalesQueryContractTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Validator validator;

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void limitBoundariesAreAccepted(int limit) throws Exception {
        SalesQuery query = objectMapper.readValue(jsonWithLimit(Integer.toString(limit)), SalesQuery.class);

        assertEquals(limit, query.limit());
        assertTrue(validator.validate(query).isEmpty());
        assertDoesNotThrow(query::validateDateRange);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, 200})
    void explicitInvalidLimitIsPreservedAndRejected(int limit) throws Exception {
        SalesQuery query = objectMapper.readValue(jsonWithLimit(Integer.toString(limit)), SalesQuery.class);

        assertEquals(limit, query.limit());
        var violations = validator.validate(query);
        Set<String> invalidFields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("limit"), invalidFields);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}",
            "{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\",\"limit\":null}"
    })
    void omittedOrNullLimitDefaultsToTen(String json) throws Exception {
        SalesQuery query = objectMapper.readValue(json, SalesQuery.class);

        assertEquals(10, query.limit());
        assertTrue(validator.validate(query).isEmpty());
        assertDoesNotThrow(query::validateDateRange);
    }

    @Test
    void sameDateIsAccepted() throws Exception {
        String json = """
                {"startDate":"2026-09-01","endDate":"2026-09-01","limit":5}
                """;
        SalesQuery query = objectMapper.readValue(json, SalesQuery.class);

        assertTrue(validator.validate(query).isEmpty());
        assertEquals(query.startDate(), query.endDate());
        assertDoesNotThrow(query::validateDateRange);
    }

    @Test
    void reversedDatesPassFieldValidationButFailDateRangeValidation() throws Exception {
        String json = """
                {"startDate":"2026-09-30","endDate":"2026-09-01","limit":5}
                """;
        SalesQuery query = objectMapper.readValue(json, SalesQuery.class);

        assertTrue(validator.validate(query).isEmpty());
        assertThrows(IllegalArgumentException.class, query::validateDateRange);
    }

    @Test
    void nonexistentDateFailsDeserialization() {
        String json = """
                {"startDate":"2026-02-30","endDate":"2026-03-01","limit":5}
                """;

        assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue(json, SalesQuery.class));
    }

    @Test
    void malformedJsonFailsParsing() {
        String json = """
                {"startDate":"2026-09-01",
                """;

        assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue(json, SalesQuery.class));
    }

    private String jsonWithLimit(String limit) {
        return """
                {"startDate":"2026-09-01","endDate":"2026-09-30","limit":%s}
                """.formatted(limit);
    }
}
