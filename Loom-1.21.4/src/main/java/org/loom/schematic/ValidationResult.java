package org.loom.schematic;

import java.util.List;

/**
 * Result of validating a schematic for compatibility.
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> errors;

    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = List.copyOf(errors);
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + valid + ", errors=" + errors + "}";
    }

    /**
     * Creates a valid result with no errors.
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Creates an invalid result with the given errors.
     */
    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
