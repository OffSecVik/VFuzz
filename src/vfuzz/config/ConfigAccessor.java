package vfuzz.config;

import vfuzz.operations.Range;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


/**
 * The ConfigAccessor class provides utility methods for retrieving and converting configuration values
 * from a configuration management system. It retrieves values based on keys and converts them into
 * the desired type, supporting primitive types, sets, enums, and custom types like {@link Range}.
 *
 * <p> It leverages the {@link ConfigManager} for fetching configuration data and provides an option
 * to detect if a configuration value is the default value.
 *
 * @see ConfigManager
 * @see Range
 */
public class ConfigAccessor {

    private static final ConfigManager configManager = ConfigManager.getInstance();

    /**
     * Retrieves a configuration value by its key and converts it to the specified type.
     * Optionally, a flag can be provided to indicate whether the retrieved value is the default value.
     *
     * @param key The configuration key to look up.
     * @param type The expected class type of the configuration value.
     * @param isDefault (Optional) An {@link AtomicBoolean} that will be set to {@code true} if the value is the default one.
     * @param <T> The type of the configuration value.
     * @return The configuration value converted to the specified type, or {@code null} if the key does not exist or conversion fails.
     */
    public static <T> T getConfigValue(String key, Class<T> type, AtomicBoolean... isDefault) {
        String value = configManager.getConfigValue(key);
        T result = convertToType(value, type);
        if (isDefault.length > 0 && isDefault[0] != null) {
            isDefault[0].set(configManager.isDefaultValue(key));
        }
        return result;
    }

    /**
     * Converts a string value to the specified type.
     * Supported types include {@code Integer}, {@code Boolean}, {@code Double}, {@code Float}, {@code Long}, {@code Set}, enums, and {@code String}.
     *
     * @param value The string value to convert.
     * @param type The class type to which the value should be converted.
     * @param <T> The type of the converted value.
     * @return The value converted to the specified type, or {@code null} if conversion fails or the value is null.
     * @throws IllegalArgumentException If the type is unsupported.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertToType(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            if (type == Integer.class) {
                return (T) Integer.valueOf(value);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(value);
            } else if (type == Double.class) {
                return (T) Double.valueOf(value);
            } else if (type == Float.class) {
                return (T) Float.valueOf(value);
            } else if (type == Long.class) {
                return (T) Long.valueOf(value);
            } else if (type == Set.class) {
                return (T) parseRangeSet(value);
            } else if (type.isEnum()) {
                //noinspection rawtypes
                return (T) Enum.valueOf((Class<Enum>) type, value.toUpperCase());
            } else if (type == String.class) {
                return (T) value;
            }
            throw new IllegalArgumentException("Unsupported type conversion requested.");
        } catch (Exception e) {
            System.err.println("Failed to convert '" + value + "' to " + type.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses a comma-separated string into a set of {@link Range} objects.
     *
     * @param csv The comma-separated string representing the ranges.
     * @return A set of {@link Range} objects parsed from the string.
     */
    private static Set<Range> parseRangeSet(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(Range::parseToRange)
                .collect(Collectors.toSet());
    }
}