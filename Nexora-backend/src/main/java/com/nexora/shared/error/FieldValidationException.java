package com.nexora.shared.error;

// A cross-field rule failed (e.g. "defaultCreditDays is only allowed for suppliers"). It is a 400
// VALIDATION_FAILED like a Bean Validation error, and the handler reports the field in errors[].
public class FieldValidationException extends BusinessException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
