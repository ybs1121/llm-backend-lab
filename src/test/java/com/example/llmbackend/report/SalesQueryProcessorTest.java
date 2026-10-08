package com.example.llmbackend.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SalesQueryProcessorTest {

    @Autowired
    SalesQueryProcessor processor;

    @Test
    void validQueryIsReturned() throws Exception {
        SalesQuery query = processor.parseAndValidate("""
                {"startDate":"2026-09-01","endDate":"2026-09-30","limit":5}
                """);

        assertEquals(LocalDate.of(2026, 9, 1), query.startDate());
        assertEquals(LocalDate.of(2026, 9, 30), query.endDate());
        assertEquals(5, query.limit());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}",
            "{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\",\"limit\":null}"
    })
    void defaultLimitIsReturned(String json) throws Exception {
        assertEquals(10, processor.parseAndValidate(json).limit());
    }

    @Test
    void missingDatesAreReportedBeforeDateRangeValidation() {
        String json = """
                {"startDate":null,"endDate":null,"limit":5}
                """;

        var exception = assertThrows(ConstraintViolationException.class,
                () -> processor.parseAndValidate(json));

        assertEquals(Set.of("startDate", "endDate"), invalidFields(exception));
    }

    @Test
    void invalidLimitIsReportedBeforeReversedDates() {
        String json = """
                {"startDate":"2026-09-30","endDate":"2026-09-01","limit":200}
                """;

        var exception = assertThrows(ConstraintViolationException.class,
                () -> processor.parseAndValidate(json));

        assertEquals(Set.of("limit"), invalidFields(exception));
        assertEquals(200, exception.getConstraintViolations().iterator().next().getInvalidValue());
    }

    @Test
    void reversedDatesAreRejectedAfterFieldValidation() {
        String json = """
                {"startDate":"2026-09-30","endDate":"2026-09-01","limit":5}
                """;

        assertThrows(IllegalArgumentException.class,
                () -> processor.parseAndValidate(json));
    }

    @Test
    void nonexistentDateFailsDeserialization() {
        String json = """
                {"startDate":"2026-02-30","endDate":"2026-03-01","limit":5}
                """;

        assertThrows(JsonProcessingException.class,
                () -> processor.parseAndValidate(json));
    }

    @Test
    void malformedJsonFailsParsing() {
        assertThrows(JsonProcessingException.class,
                () -> processor.parseAndValidate("{\"startDate\":"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5.9", "\"5\"", "\"\"", "\" \""})
    void nonIntegerJsonLimitIsRejected(String limit) {
        String json = """
                {"startDate":"2026-09-01","endDate":"2026-09-30","limit":%s}
                """.formatted(limit);

        assertThrows(JsonProcessingException.class,
                () -> processor.parseAndValidate(json));
    }

    private Set<String> invalidFields(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
