package com.example.llmbackend.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SalesQueryProcessor {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public SalesQueryProcessor(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.coercionConfigFor(Integer.class)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        this.validator = validator;
    }

    public SalesQuery parseAndValidate(String modelJson) throws JsonProcessingException {
        // TODO: Deserialize the model JSON. SalesQuery applies its default limit.
        // TODO: Run Bean Validation; throw ConstraintViolationException for violations.
        // TODO: Check the date range only after field validation succeeds.
        // TODO: Return the validated query.

        SalesQuery salesQuery = objectMapper.readValue(modelJson, SalesQuery.class);
        var violations = validator.validate(salesQuery);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        salesQuery.validateDateRange();
        

        return salesQuery;


//        throw new UnsupportedOperationException("Implement the model-output processing flow.");
    }
}
