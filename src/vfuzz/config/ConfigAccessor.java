package vfuzz.config;

import vfuzz.operations.Range;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


/**
 * The {@code ConfigAccessor} class provides utility methods for retrieving and converting configuration values
 * from the configuration management system. It allows for type-safe access to configuration settings and supports
 * various data types, including primitive types, collections, enums, and custom types like {@link Range}.
 *
 * <p>The class leverages {@link ConfigManager} to fetch configuration data and provides an optional mechanism
 * to determine whether a retrieved value is the default value.
 *
 * <h2>Features:</h2>
 * <ul>
 *     <li>Retrieve configuration values by their keys.</li>
 *     <li>Convert configuration values to the desired type.</li>
 *     <li>Support for various data types including {@code Integer}, {@code Boolean}, {@code Double}, {@code Float}, {@code Long}, {@code Set}, enums, and {@code String}.</li>
 *     <li>Support for custom parsing of range sets.</li>
 *     <li>Optional detection of default configuration values.</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * AtomicBoolean isDefault = new AtomicBoolean();
 * Integer threadCount = ConfigAccessor.getConfigValue("threadCount", Integer.class, isDefault);
 * if (isDefault.get()) {
 *     System.out.println("Using default value for threadCount");
 * }
 * }</pre>
 *
 * @see ConfigManager
 * @see Range
 */
public class ConfigAccessor {

    private static final ConfigManager configManager = ConfigManager.getInstance();

    /**
     * Retrieves a configuration value by its key and converts it to the specified type.
     *
     * <p>The method supports an optional {@link AtomicBoolean} parameter to indicate whether the value is
     * the default one. If provided, the boolean will be set to {@code true} if the retrieved value matches
     * the default configuration value.
     *
     * @param key The configuration key to look up.
     * @param type The expected class type of the configuration value.
     * @param isDefault (Optional) An {@link AtomicBoolean} that will be updated to indicate if the value is default.
     * @param <T> The type of the configuration value.
     * @return The configuration value converted to the specified type, or {@code null} if the key does not exist
     *         or the conversion fails.
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
     *
     * <p>Supported types include:
     * <ul>
     *     <li>{@code Integer}</li>
     *     <li>{@code Boolean}</li>
     *     <li>{@code Double}</li>
     *     <li>{@code Float}</li>
     *     <li>{@code Long}</li>
     *     <li>{@code Set<Range>}</li>
     *     <li>Enums</li>
     *     <li>{@code String}</li>
     * </ul>
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
     * <p>The input string should be formatted as comma-separated values, each representing a range.
     * Example: {@code "1-10, 15-20, 30"} will parse into a set containing ranges [1-10], [15-20], and [30].
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