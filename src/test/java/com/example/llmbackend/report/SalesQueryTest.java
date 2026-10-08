package com.example.llmbackend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SalesQueryTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Validator validator;

    @Test
    void validStubPassesValidation() throws Exception {
        String json = readStub("sales-query-valid.json");

        SalesQuery salesQuery = objectMapper.readValue(json, SalesQuery.class);
        Set<ConstraintViolation<SalesQuery>> validate =
                validator.validate(salesQuery);
        if (!validate.isEmpty()) {
            throw new IllegalArgumentException();
        }

        salesQuery.validateDateRange();

        // TODO: Deserialize json into SalesQuery.
        // TODO: Assert that Bean Validation returns no violations.
        // TODO: Assert that validateDateRange() does not throw.
        // TODO: Assert the extracted startDate, endDate and limit.
        Assertions.assertEquals(LocalDate.of(2026, 9, 1), salesQuery.startDate());
        Assertions.assertEquals(LocalDate.of(2026, 9, 30), salesQuery.endDate());
        Assertions.assertEquals(5, salesQuery.limit());
//        fail("Implement the valid Stub scenario before running this test.");
    }

    @Test
    void missingDatesFailRequiredFieldValidation() throws Exception {
        String json = readStub("sales-query-missing-dates.json");

        SalesQuery salesQuery = assertDoesNotThrow(
                () -> objectMapper.readValue(json, SalesQuery.class));

        assertNull(salesQuery.startDate());
        assertNull(salesQuery.endDate());
        assertEquals(5, salesQuery.limit());

        Set<ConstraintViolation<SalesQuery>> violations = validator.validate(salesQuery);
        Set<String> invalidFields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("startDate", "endDate"), invalidFields);
        assertEquals(2, violations.size());
        // Required-field validation failed, so date-range validation is not invoked.
    }

    private String readStub(String fileName) throws IOException {
        String resourcePath = "/stub/" + fileName;
        try (var input = SalesQueryTest.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing Stub resource: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
