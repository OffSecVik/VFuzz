package vfuzz.operations;

/**
 * The {@code Range} record represents a numeric range with a start and end value.
 * It provides utility methods to check whether a given value is within the range
 * and to parse a string representation of a range.
 *
 * <p>The range is inclusive, meaning that both the start and end values are part of the range.
 */
public record Range(int start, int end) {

    /**
     * Checks if the given value is within the range (inclusive).
     *
     * @param value The value to check.
     * @return {@code true} if the value is within the range, {@code false} otherwise.
     */
    public boolean contains(int value) {
        return value >= start && value <= end;
    }

    @Override
    public String toString() {
        if (start == end) {
            return "" + start;
        }
        return start + "-" + end;
    }

    /**
     * Parses a string representation of a range and creates a {@code Range} object.
     *
     * <p>The string can represent a single number (e.g., "5"), which results in a range
     * where the start and end are the same. It can also represent a range in the form
     * "start-end" (e.g., "5-10").
     *
     * @param s The string to parse.
     * @return A {@code Range} object representing the parsed range.
     * @throws NumberFormatException if the string cannot be parsed into integers.
     */
    public static Range parseToRange(String s) {
        if (s.contains("-")) {
            String[] parts = s.split("-");
            return new Range(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } else {
            int singleValue = Integer.parseInt(s.trim());
            return new Range(singleValue, singleValue);
        }
    }
}
